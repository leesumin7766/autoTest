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
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"execution.worker.enabled=false","product-description.internal-token=test-token","s3.enabled=false","s3.access-key=test","s3.secret-key=test","playwright.credentials-key-file=./build/test-playwright/credentials.key"})
@AutoConfigureMockMvc
@Transactional
class SubmissionWorkspaceIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",()->{
            String url=System.getenv().getOrDefault("AUTOTEST_TEST_DATABASE_URL","jdbc:postgresql://localhost:5432/autotest_test");
            if(!URI.create(url.substring(5)).getPath().endsWith("_test"))throw new IllegalStateException("An isolated *_test database is required");
            return url;
        });
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired SubmissionWorkspaceService service;
    private UUID submission(long member){var id=UUID.randomUUID();jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES(?,?,7,'UPLOADED',now()+interval '1 day')",id,member);return id;}
    @Test void existingPartialSubmissionCanBeNamedAndRestoredWithoutChangingDocuments()throws Exception{
        var id=submission(900001);
        mvc.perform(get("/api/submissions/{id}/workspace",id).header("X-Member-Id",900001)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.documentCount").value(0));
        mvc.perform(patch("/api/submissions/{id}/workspace",id).header("X-Member-Id",900001).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"서버 회귀 시험\",\"lastPage\":\"execution\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("서버 회귀 시험")).andExpect(jsonPath("$.lastPage").value("execution"));
        mvc.perform(get("/api/submissions/{id}/workspace",id).header("X-Member-Id",900001)).andExpect(jsonPath("$.name").value("서버 회귀 시험")).andExpect(jsonPath("$.productId").value(7));
        assertEquals("UPLOADED",jdbc.queryForObject("SELECT status FROM submissions WHERE submission_id=?",String.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM submission_files WHERE submission_id=?",Integer.class,id));
    }
    @Test void navigationAndRenameUpdatesPreserveEachOther(){
        var id=submission(900002);service.update(id,900002,"제품 A",null);service.update(id,900002,null,"menu");
        var renamed=service.update(id,900002,"제품 A 회귀",null);
        assertEquals("menu",renamed.lastPage());assertEquals("제품 A 회귀",renamed.name());
    }
    @Test void anotherMemberCannotListReadOrRenameWorkspace()throws Exception{
        var id=submission(900003);service.update(id,900003,"다른 회원 작업",null);
        assertTrue(service.list(900004,0,20).stream().noneMatch(w->w.id().equals(id)));
        mvc.perform(get("/api/submissions/{id}/workspace",id).header("X-Member-Id",900004)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/submissions/{id}/workspace",id).header("X-Member-Id",900004).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"변경 시도\"}")).andExpect(status().isNotFound());
        assertEquals("다른 회원 작업",service.get(id,900003).name());
    }
    @Test void invalidPageNameAndPaginationDoNotModifyWorkspace()throws Exception{
        var id=submission(900005);service.update(id,900005,"원본 이름","documents");
        for(String body:new String[]{"{\"name\":\" \"}","{\"lastPage\":\"unknown\"}","{}"})
            mvc.perform(patch("/api/submissions/{id}/workspace",id).header("X-Member-Id",900005).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/submissions/workspaces?limit=0").header("X-Member-Id",900005)).andExpect(status().isBadRequest());
        assertEquals("원본 이름",service.get(id,900005).name());
    }
    @Test void listingPaginatesAndIncludesExistingSubmissionsWithoutMetadata()throws Exception{
        var a=submission(900006);var b=submission(900006);
        mvc.perform(get("/api/submissions/workspaces?limit=1").header("X-Member-Id",900006)).andExpect(status().isOk()).andExpect(jsonPath("$.workspaces.length()").value(1)).andExpect(jsonPath("$.hasMore").value(true));
        var first=service.list(900006,0,1).getFirst().id();var second=service.list(900006,1,1).getFirst().id();
        assertNotEquals(first,second);assertTrue(java.util.Set.of(a,b).containsAll(java.util.Set.of(first,second)));
    }
    @Test void newestCreationComesFirstEvenWhenAnOlderWorkspaceIsUpdated(){
        var older=submission(900007);var newer=submission(900007);
        jdbc.update("UPDATE submissions SET uploaded_at='2026-01-01T00:00:00Z' WHERE submission_id=?",older);
        jdbc.update("UPDATE submissions SET uploaded_at='2026-02-01T00:00:00Z' WHERE submission_id=?",newer);
        service.update(older,900007,"이름 변경","execution");
        assertEquals(newer,service.list(900007,0,1).getFirst().id());
        assertEquals(older,service.list(900007,1,1).getFirst().id());
    }
}
