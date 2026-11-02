package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"execution.worker.enabled=false","product-description.internal-token=test-token","s3.enabled=false","s3.access-key=test","s3.secret-key=test","playwright.credentials-key-file=./build/test-playwright/credentials.key"})
@AutoConfigureMockMvc
class ManualReviewExecutionIntegrationTest {
    static final HttpServer processor;
    static final ExecutorService threads=Executors.newCachedThreadPool();
    static final CountDownLatch secondStarted=new CountDownLatch(1), releaseSecond=new CountDownLatch(1);
    static final List<Integer> pages=new CopyOnWriteArrayList<>();
    static {
        try {
            processor=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            processor.setExecutor(threads);
            processor.createContext("/",exchange->{
                String path=exchange.getRequestURI().getPath(),body="{}";
                assertEquals("test-token",exchange.getRequestHeaders().getFirst("X-Internal-Token"));
                if(path.endsWith("/prepare"))body="{\"totalPages\":3,\"processorVersion\":\"fixture-v2\"}";
                else if(path.endsWith("/progress"))body="{\"currentPage\":2,\"stage\":\"REVIEWING\"}";
                else if(path.endsWith("/cancel")||path.endsWith("/close")){releaseSecond.countDown();body="{\"stopped\":true}";}
                else if(path.contains("/pages/")){
                    String[] parts=path.split("/");int attempt=Integer.parseInt(parts[3]),page=Integer.parseInt(parts[5]);pages.add(page);
                    if(page==2&&attempt==1){secondStarted.countDown();try{releaseSecond.await(15,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
                    String candidate=page==2&&attempt==1?"late-attempt-1":"page-"+page;
                    body="{\"status\":\"COMPLETED\",\"page\":"+page+",\"pageCount\":3,\"failedChunks\":0,\"reviewedChunks\":1,\"retriedChunks\":0,\"blockCount\":1,\"nativePageCount\":1,\"ocrPageCount\":0,\"emptyPages\":[],\"extractedCharacterCount\":30,\"catalogVersion\":\"fixture-v1\",\"catalogProducts\":[\"Windows 10\"],\"warnings\":[],\"candidates\":[{\"id\":\""+candidate+"\",\"page\":"+page+",\"status\":\"NEEDS_REVIEW\"}]}";
                }
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                try{exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}catch(java.io.IOException ignored){}finally{exchange.close();}
            });
            processor.start();
        } catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
        registry.add("MANUAL_REVIEW_SERVICE_URL",()->"http://localhost:"+processor.getAddress().getPort());
        registry.add("spring.datasource.url",()->{
            String url=System.getenv().getOrDefault("AUTOTEST_TEST_DATABASE_URL","jdbc:postgresql://localhost:5432/autotest_test");
            if(!URI.create(url.substring(5)).getPath().endsWith("_test"))throw new IllegalStateException("An isolated *_test database is required");return url;
        });
    }
    @AfterAll static void close(){releaseSecond.countDown();processor.stop(0);threads.shutdownNow();}
    @Autowired JdbcTemplate jdbc;
    @Autowired ManualReviewService service;
    @Autowired MockMvc mvc;
    @MockBean FileStoragePort storage;

    @Test void pauseResumeKeepsCompletedPagesFencesLateResultsAndFreezesElapsed()throws Exception{
        var submission=UUID.randomUUID();var file=UUID.randomUUID();
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES(?,98101,1,'PARSED',now())",submission);
        jdbc.update("INSERT INTO submission_files(file_id,submission_id,role,file_type,file_format,original_filename,mime_type,stored_path,extracted_text,status,uploaded_at) VALUES(?,?,'MANUAL','PDF','PDF','fixture.pdf','application/pdf','local:fixture','fixture source','PARSED',now())",file,submission);
        when(storage.load(any())).thenAnswer(call->new ByteArrayInputStream("%PDF-fixture".getBytes(StandardCharsets.US_ASCII)));
        UUID job=null;
        try{
            var started=service.start(submission,98101);job=UUID.fromString(started.path("job").path("jobId").asText());
            assertTrue(secondStarted.await(10,TimeUnit.SECONDS));
            var running=service.get(submission,98101,job).path("job");
            assertEquals(3,running.path("progress").path("totalPages").asInt());
            assertEquals(2,running.path("progress").path("currentPage").asInt());
            assertEquals(1,running.path("progress").path("completedPages").asInt());
            mvc.perform(post("/api/submissions/{id}/manual-reviews/{job}/pause",submission,job).header("X-Member-Id",98102)).andExpect(status().isForbidden());
            mvc.perform(post("/api/submissions/{id}/manual-reviews/{job}/pause",submission,job).header("X-Member-Id",98101)).andExpect(status().isAccepted()).andExpect(jsonPath("$.job.status").value("PAUSED"));
            var paused=service.get(submission,98101,job).path("job");long elapsed=paused.path("progress").path("elapsedMs").asLong();
            Thread.sleep(150);
            assertEquals(elapsed,service.get(submission,98101,job).path("job").path("progress").path("elapsedMs").asLong());
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM manual_review_pages WHERE job_id=? AND complete",Integer.class,job));
            mvc.perform(post("/api/submissions/{id}/manual-reviews/{job}/resume",submission,job).header("X-Member-Id",98101)).andExpect(status().isAccepted());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);JsonNode done;
            do{done=service.get(submission,98101,job).path("job");if(!done.path("status").asText().equals("RUNNING"))break;Thread.sleep(30);}while(System.nanoTime()<deadline);
            assertEquals("COMPLETED",done.path("status").asText());
            assertEquals(3,done.path("progress").path("completedPages").asInt());
            assertEquals(2,done.path("attempt").asInt());
            assertTrue(done.path("progress").path("elapsedMs").asLong()>=elapsed);
            assertEquals(List.of(1,2,2,3),pages);
            assertEquals(3,done.path("output").path("candidates").size());
            assertFalse(done.path("output").toString().contains("late-attempt-1"));
        }finally{
            releaseSecond.countDown();
            if(job!=null){jdbc.update("DELETE FROM manual_review_pages WHERE job_id=?",job);jdbc.update("DELETE FROM manual_review_jobs WHERE job_id=?",job);}
            jdbc.update("DELETE FROM submission_files WHERE submission_id=?",submission);jdbc.update("DELETE FROM submissions WHERE submission_id=?",submission);
        }
    }
}
