package com.autotest.test_management_service.application.testcase;

import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"s3.enabled=false","s3.access-key=test","s3.secret-key=test","playwright.credentials-key-file=./build/test-playwright/credentials.key"})
@AutoConfigureMockMvc
@Transactional
class MenuTcIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->{
            String url=System.getenv().getOrDefault("AUTOTEST_TEST_DATABASE_URL","jdbc:postgresql://localhost:5432/autotest_test");
            if(!URI.create(url.substring(5)).getPath().endsWith("_test"))throw new IllegalStateException("An isolated *_test database is required");
            return url;
        });
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired SubmissionPersistenceService persistence;
    @Test void sourceApprovalToDurableIdempotentSetToExcelAndStaleFencing() throws Exception {
        UUID submission=UUID.randomUUID(),crawl=UUID.randomUUID(),menu=UUID.randomUUID();long member=92351;
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES(?,?,1,'PARSED',now())",submission,member);
        for(String role:List.of("FUNCTION_LIST","MANUAL"))jdbc.update("""
            INSERT INTO submission_files(file_id,submission_id,role,file_type,file_format,original_filename,mime_type,stored_path,extracted_text,status,uploaded_at)
            VALUES(?,?,?,'PDF','PDF',?,'application/pdf','s3://autotest-docs/menu-tc-test',?,'PARSED',now())
            """,UUID.randomUUID(),submission,role,role+".pdf","검증용 제품 사용자 조회 기능 "+role);
        String digest=DocumentDigest.of(persistence.deliveryDocuments(persistence.findDocumentsBySubmissionId(SubmissionId.of(submission))));
        jdbc.update("INSERT INTO submission_ai_deliveries(submission_id,status,attempt_count,preflight_result,verified_document_digest,updated_at) VALUES(?,'DELIVERED',1,'{\"decision\":\"READY\",\"warnings\":[]}'::jsonb,?,now())",submission,digest);
        jdbc.update("INSERT INTO playwright_crawl_jobs(job_id,submission_id,member_id,status,setup_snapshot,document_digest,execution_mode,purpose) VALUES(?,?,?,'COMPLETED','{\"targetUrl\":\"https://example.com\"}'::jsonb,?,'LOCAL','CRAWL')",crawl,submission,member,digest);
        String rows="""
            {"rows":[{"rowId":"feature","included":true,"functionName":"사용자 조회","menuPath":["관리"],"menuDescription":"사용자를 조회하는 기능","evidence":[{"sourceId":"doc-1","quote":"사용자 조회"}],"matchedMenuKeys":[]}]}
            """;
        jdbc.update("INSERT INTO functional_menu_jobs(job_id,submission_id,member_id,crawl_job_id,document_digest,product_name,rules_version,status,generation_mode,revision,snapshot,output) VALUES(?,?,?,?,?,'검증용 제품','functional-v1','APPROVED','REAL',1,'{\"warnings\":[],\"evidence\":[]}'::jsonb,?::jsonb)",menu,submission,member,crawl,digest,rows);
        String base="/api/submissions/"+submission+"/menu-test-cases";
        String payload=mapper.writeValueAsString(Map.of("menuJobId",menu,"featureTypes",Map.of("feature","부가기능")));
        var first=mapper.readTree(mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String set=first.path("set").path("setId").asText();assertFalse(set.isBlank());
        assertEquals(77,first.path("set").path("output").path("testCases").size());
        var second=mapper.readTree(mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(set,second.path("set").path("setId").asText());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM menu_tc_sets WHERE submission_id=?",Integer.class,submission));
        mvc.perform(get(base).header("X-Member-Id",member+1)).andExpect(status().isForbidden());
        mvc.perform(get(base+"/"+set+"/download").header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        jdbc.update("UPDATE functional_menu_jobs SET revision=2 WHERE job_id=?",menu);
        mvc.perform(get(base).header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.set.stale").value(true));
        mvc.perform(get(base+"/"+set+"/download").header("X-Member-Id",member)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TC_SOURCE_CHANGED"));
        jdbc.update("UPDATE functional_menu_jobs SET status='DRAFT' WHERE job_id=?",menu);
        mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TC_MENU_APPROVAL_REQUIRED"));
    }
}
