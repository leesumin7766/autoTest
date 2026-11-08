package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;

/** 검토 서비스가 응답은 하지만 중단을 확인해 주지 않을 때: 바로 포기하지 않고, 15분이 지나면 정리한다 */
@SpringBootTest(properties={"execution.worker.enabled=false","product-description.internal-token=test-token","s3.enabled=false","s3.access-key=test","s3.secret-key=test",
    "playwright.credentials-key-file=./build/test-playwright/credentials.key","manual-review.reclaim-delay-ms=3600000"})
class ManualReviewStopTimeoutIntegrationTest {
    static final HttpServer processor;
    static {
        try {
            processor=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            processor.createContext("/",exchange->{
                byte[] body=(exchange.getRequestURI().getPath().endsWith("/close")?"{\"stopped\":false}":"{}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
                exchange.getResponseBody().write(body);exchange.close();
            });
            processor.start();
        } catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
        registry.add("MANUAL_REVIEW_SERVICE_URL",()->"http://127.0.0.1:"+processor.getAddress().getPort());
        registry.add("spring.datasource.url",()->{
            String url=System.getenv().getOrDefault("AUTOTEST_TEST_DATABASE_URL","jdbc:postgresql://localhost:5432/autotest_test");
            if(!URI.create(url.substring(5)).getPath().endsWith("_test"))throw new IllegalStateException("An isolated *_test database is required");return url;
        });
    }
    @AfterAll static void close(){processor.stop(0);}
    @Autowired JdbcTemplate jdbc;
    @Autowired ManualReviewService service;
    @MockBean FileStoragePort storage;

    @Test void unconfirmedStopWaitsThenGivesUpAfterTheLimit(){
        var submission=UUID.randomUUID();var file=UUID.randomUUID();var job=UUID.randomUUID();
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES(?,98301,1,'PARSED',now())",submission);
        jdbc.update("INSERT INTO submission_files(file_id,submission_id,role,file_type,file_format,original_filename,mime_type,stored_path,extracted_text,status,uploaded_at) VALUES(?,?,'MANUAL','PDF','PDF','fixture.pdf','application/pdf','local:fixture','fixture source','PARSED',now())",file,submission);
        try{
            jdbc.update("INSERT INTO manual_review_jobs(job_id,submission_id,member_id,manual_file_id,filename,document_hash,status,as_of,stage,error_code,attempt_started_at,updated_at) "
                +"VALUES(?,?,98301,?,'fixture.pdf',?,'PAUSING',current_date,'PAUSING','STOP_NOT_CONFIRMED',now()-interval '2 minutes',now()-interval '2 minutes')",job,submission,file,"0".repeat(64));
            service.reclaimStalePausing();
            assertEquals("PAUSING",jdbc.queryForObject("SELECT status FROM manual_review_jobs WHERE job_id=?",String.class,job),"한도 전에는 기다린다");

            jdbc.update("UPDATE manual_review_jobs SET updated_at=now()-?::interval WHERE job_id=?",(ManualReviewService.STOP_GIVE_UP.toMinutes()+5)+" minutes",job);
            service.reclaimStalePausing();
            assertEquals("INTERRUPTED",jdbc.queryForObject("SELECT status FROM manual_review_jobs WHERE job_id=?",String.class,job));
            assertEquals("STOP_TIMEOUT",jdbc.queryForObject("SELECT error_code FROM manual_review_jobs WHERE job_id=?",String.class,job));
            assertTrue(service.get(submission,98301,job).path("job").path("resumable").asBoolean());
        }finally{
            jdbc.update("DELETE FROM manual_review_jobs WHERE submission_id=?",submission);
            jdbc.update("DELETE FROM submission_files WHERE submission_id=?",submission);
            jdbc.update("DELETE FROM submissions WHERE submission_id=?",submission);
        }
    }
}
