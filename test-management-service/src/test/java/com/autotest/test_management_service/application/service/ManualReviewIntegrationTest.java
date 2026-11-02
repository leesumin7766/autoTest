package com.autotest.test_management_service.application.service;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import java.io.ByteArrayInputStream;

@SpringBootTest(properties={"execution.worker.enabled=false","product-description.internal-token=test-token","s3.enabled=false","s3.access-key=test","s3.secret-key=test","playwright.credentials-key-file=./build/test-playwright/credentials.key"})
@AutoConfigureMockMvc
@Transactional
class ManualReviewIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->{
            String url=System.getenv().getOrDefault("AUTOTEST_TEST_DATABASE_URL","jdbc:postgresql://localhost:5432/autotest_test");
            if(!URI.create(url.substring(5)).getPath().endsWith("_test"))throw new IllegalStateException("An isolated *_test database is required");
            return url;
        });
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockBean FileStoragePort storage;

    private UUID submission() {
        var id=UUID.randomUUID();
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES(?,98001,1,'UPLOADED',now())",id);
        return id;
    }
    @Test void historyIsOwnedAndNoManualCannotStart() throws Exception {
        var id=submission();
        mvc.perform(get("/api/submissions/{id}/manual-reviews",id).header("X-Member-Id",98002)).andExpect(status().isForbidden());
        mvc.perform(get("/api/submissions/{id}/manual-reviews",id).header("X-Member-Id",98001)).andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(0));
        mvc.perform(post("/api/submissions/{id}/manual-reviews",id).header("X-Member-Id",98001)).andExpect(status().isConflict());
    }
    @Test void oldResultsRemainReadableButAreMarkedStale() throws Exception {
        var id=submission();var job=UUID.randomUUID();
        jdbc.update("INSERT INTO manual_review_jobs(job_id,submission_id,member_id,manual_file_id,filename,document_hash,status,as_of,output) VALUES(?,?,98001,?,'old.pdf',?,'PARTIAL',current_date,'{\"candidates\":[]}'::jsonb)",job,id,UUID.randomUUID(),"0".repeat(64));
        mvc.perform(get("/api/submissions/{id}/manual-reviews",id).header("X-Member-Id",98001)).andExpect(status().isOk())
            .andExpect(jsonPath("$.job.status").value("PARTIAL")).andExpect(jsonPath("$.job.stale").value(true));
        mvc.perform(get("/api/submissions/{id}/manual-reviews?job={job}",id,UUID.randomUUID()).header("X-Member-Id",98001)).andExpect(status().isNotFound());
    }
    @Test void changedSourceCannotResumeAndCheckpointIsPreserved() throws Exception {
        var id=submission();var job=UUID.randomUUID();var file=UUID.randomUUID();
        jdbc.update("INSERT INTO submission_files(file_id,submission_id,role,file_type,file_format,original_filename,mime_type,stored_path,extracted_text,status,uploaded_at) VALUES(?,?,'MANUAL','PDF','PDF','source.pdf','application/pdf','local:fixture','source text','PARSED',now())",file,id);
        jdbc.update("INSERT INTO manual_review_jobs(job_id,submission_id,member_id,manual_file_id,filename,document_hash,status,as_of,total_pages,completed_pages,stage,elapsed_ms) VALUES(?,?,98001,?,'source.pdf',?,'PAUSED',current_date,3,1,'PAUSED',12000)",job,id,file,"0".repeat(64));
        jdbc.update("INSERT INTO manual_review_pages(job_id,page_no,complete,output) VALUES(?,1,true,'{\"candidates\":[]}'::jsonb)",job);
        when(storage.load(any())).thenReturn(new ByteArrayInputStream("%PDF-changed".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        mvc.perform(post("/api/submissions/{id}/manual-reviews/{job}/resume",id,job).header("X-Member-Id",98001)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MANUAL_REVIEW_INPUT_CHANGED"));
        assertEquals("PAUSED",jdbc.queryForObject("SELECT status FROM manual_review_jobs WHERE job_id=?",String.class,job));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM manual_review_pages WHERE job_id=? AND complete",Integer.class,job));
        mvc.perform(post("/api/submissions/{id}/manual-reviews/{job}/resume",id,job).header("X-Member-Id",98002)).andExpect(status().isForbidden());
    }
}
