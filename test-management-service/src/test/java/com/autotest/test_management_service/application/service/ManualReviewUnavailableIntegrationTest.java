package com.autotest.test_management_service.application.service;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import java.io.ByteArrayInputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
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

/** 2026-11-08 운영 결함: 검토 서비스가 꺼져 있을 때 작업이 PAUSING 에 멈춰 모든 매뉴얼 검토를 막던 문제 */
@SpringBootTest(properties={"execution.worker.enabled=false","product-description.internal-token=test-token","s3.enabled=false","s3.access-key=test","s3.secret-key=test",
    "playwright.credentials-key-file=./build/test-playwright/credentials.key","manual-review.reclaim-delay-ms=3600000"})
@AutoConfigureMockMvc
class ManualReviewUnavailableIntegrationTest {
    /** 아무도 듣지 않는 포트 = 검토 서비스 없음 */
    static final int CLOSED_PORT;
    static {
        try(var socket=new ServerSocket(0,0,InetAddress.getByName("127.0.0.1"))){CLOSED_PORT=socket.getLocalPort();}
        catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
        registry.add("MANUAL_REVIEW_SERVICE_URL",()->"http://127.0.0.1:"+CLOSED_PORT);
        registry.add("spring.datasource.url",()->{
            String url=System.getenv().getOrDefault("AUTOTEST_TEST_DATABASE_URL","jdbc:postgresql://localhost:5432/autotest_test");
            if(!URI.create(url.substring(5)).getPath().endsWith("_test"))throw new IllegalStateException("An isolated *_test database is required");return url;
        });
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ManualReviewService service;
    @Autowired MockMvc mvc;
    @MockBean FileStoragePort storage;
    UUID submission, file;

    @BeforeEach void fixture(){
        submission=UUID.randomUUID();file=UUID.randomUUID();
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES(?,98201,1,'PARSED',now())",submission);
        jdbc.update("INSERT INTO submission_files(file_id,submission_id,role,file_type,file_format,original_filename,mime_type,stored_path,extracted_text,status,uploaded_at) VALUES(?,?,'MANUAL','PDF','PDF','fixture.pdf','application/pdf','local:fixture','fixture source','PARSED',now())",file,submission);
        when(storage.load(any())).thenAnswer(call->new ByteArrayInputStream("%PDF-fixture".getBytes(StandardCharsets.US_ASCII)));
    }
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM manual_review_pages WHERE job_id IN (SELECT job_id FROM manual_review_jobs WHERE submission_id=?)",submission);
        jdbc.update("DELETE FROM manual_review_jobs WHERE submission_id=?",submission);
        jdbc.update("DELETE FROM submission_files WHERE submission_id=?",submission);
        jdbc.update("DELETE FROM submissions WHERE submission_id=?",submission);
    }
    UUID pausing(String age){
        var job=UUID.randomUUID();
        jdbc.update("INSERT INTO manual_review_jobs(job_id,submission_id,member_id,manual_file_id,filename,document_hash,status,as_of,stage,error_code,attempt_started_at,updated_at) "
            +"VALUES(?,?,98201,?,'fixture.pdf',?,'PAUSING',current_date,'PAUSING','STOP_NOT_CONFIRMED',now()-?::interval,now()-?::interval)",job,submission,file,"0".repeat(64),age,age);
        return job;
    }

    @Test void startIsRefusedWithoutCreatingAJobWhenTheServiceIsDown()throws Exception{
        mvc.perform(post("/api/submissions/{id}/manual-reviews",submission).header("X-Member-Id",98201))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MANUAL_REVIEW_UNAVAILABLE"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM manual_review_jobs WHERE submission_id=?",Integer.class,submission));
    }

    @Test void stuckPausingJobIsReleasedWhenTheServiceIsUnreachable(){
        var job=pausing("1 minute");
        service.reclaimStalePausing();
        assertEquals("INTERRUPTED",jdbc.queryForObject("SELECT status FROM manual_review_jobs WHERE job_id=?",String.class,job));
        assertEquals("SERVICE_UNAVAILABLE",jdbc.queryForObject("SELECT error_code FROM manual_review_jobs WHERE job_id=?",String.class,job));
        var view=service.get(submission,98201,job).path("job");
        assertTrue(view.path("resumable").asBoolean(),"저장된 페이지부터 이어서 검토할 수 있어야 한다");
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM manual_review_jobs WHERE status IN ('RUNNING','PAUSING')",Integer.class));
    }

    @Test void startReleasesAStuckJobInsteadOfReportingBusy()throws Exception{
        var job=pausing("1 minute");
        // 예전에는 MANUAL_REVIEW_BUSY(409) 로 영원히 막혔다. 이제는 멈춘 작업을 정리하고, 서비스가 없다는 진짜 이유를 돌려준다
        mvc.perform(post("/api/submissions/{id}/manual-reviews",submission).header("X-Member-Id",98201))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MANUAL_REVIEW_UNAVAILABLE"));
        assertEquals("INTERRUPTED",jdbc.queryForObject("SELECT status FROM manual_review_jobs WHERE job_id=?",String.class,job));
    }

    @Test void onlyConnectionFailuresCountAsUnreachable(){
        assertTrue(ManualReviewService.unreachable(new ConnectException("refused")));
        assertTrue(ManualReviewService.unreachable(new java.io.IOException(new java.nio.channels.UnresolvedAddressException())));
        assertFalse(ManualReviewService.unreachable(new java.net.http.HttpTimeoutException("slow")));
        assertFalse(ManualReviewService.unreachable(new java.net.http.HttpConnectTimeoutException("slow connect")));
        assertFalse(ManualReviewService.unreachable(new IllegalStateException("other")));
    }
}
