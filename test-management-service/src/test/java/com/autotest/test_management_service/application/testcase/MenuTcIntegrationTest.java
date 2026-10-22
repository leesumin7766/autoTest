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

@SpringBootTest(properties={"execution.worker.enabled=false","s3.enabled=false","s3.access-key=test","s3.secret-key=test","playwright.credentials-key-file=./build/test-playwright/credentials.key"})
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
    @Autowired TestExecutionService executions;
    @Autowired com.autotest.test_management_service.application.crawler.PlaywrightSetupService setups;
    @Autowired com.autotest.test_management_service.application.crawler.PlaywrightCredentialCipher cipher;
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
        var upload = new org.springframework.mock.web.MockMultipartFile("file", "menu.json", "application/json", rows.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String uploadUrl="/api/submissions/"+submission+"/functional-menu/upload";
        mvc.perform(multipart(uploadUrl).file(upload).header("X-Member-Id",member+1)).andExpect(status().isForbidden());
        var uploaded=mapper.readTree(mvc.perform(multipart(uploadUrl).file(upload).header("X-Member-Id",member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.job.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsByteArray());
        String uploadedJob=uploaded.path("job").path("jobId").asText();
        mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("menuJobId",uploadedJob))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.set.output.caseCount").value(77));
        jdbc.update("DELETE FROM menu_tc_sets WHERE submission_id=?",submission);
        String payload=mapper.writeValueAsString(Map.of("menuJobId",menu,"featureTypes",Map.of("feature","부가기능")));
        var first=mapper.readTree(mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String set=first.path("set").path("setId").asText();assertFalse(set.isBlank());
        assertEquals(77,first.path("set").path("output").path("testCases").size());
        var second=mapper.readTree(mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(set,second.path("set").path("setId").asText());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM menu_tc_sets WHERE submission_id=?",Integer.class,submission));
        mvc.perform(get(base).header("X-Member-Id",member+1)).andExpect(status().isForbidden());
        mvc.perform(get(base+"/"+set+"/download").header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        String executionBase="/api/submissions/"+submission+"/executions";
        String prepare=mapper.writeValueAsString(Map.of("tcSetId",set,"targetUrl","https://example.com","notes","실행 골격 검증"));
        mvc.perform(post(executionBase).header("X-Member-Id",member+1).contentType(MediaType.APPLICATION_JSON).content(prepare)).andExpect(status().isForbidden());
        mvc.perform(post(executionBase).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("tcSetId",set,"targetUrl","javascript:alert(1)")))).andExpect(status().isBadRequest());
        mvc.perform(post(executionBase).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("tcSetId",set,"targetUrl","https://example.com","caseIds",List.of("unknown"))))).andExpect(status().isBadRequest());
        var execution=mapper.readTree(mvc.perform(post(executionBase).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(prepare))
                .andExpect(status().isOk()).andExpect(jsonPath("$.run.status").value("PREPARED")).andReturn().getResponse().getContentAsByteArray());
        String run=execution.path("run").path("runId").asText();
        var snapshot=execution.path("run").path("snapshot");assertEquals(77,snapshot.path("items").size());
        assertEquals(4,snapshot.path("groups").size());
        for(var item:snapshot.path("items")){assertTrue(item.path("result").isNull());assertEquals("PENDING",item.path("executionStatus").asText());assertTrue(item.path("evidence").isEmpty());}
        mvc.perform(get(executionBase).param("run",run).header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.run.snapshot.caseCount").value(77));
        mvc.perform(get(executionBase).param("run",run).header("X-Member-Id",member+1)).andExpect(status().isForbidden());
        mvc.perform(post(executionBase+"/"+run+"/start").header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.run.status").value("WAITING_EXECUTOR"));
        mvc.perform(post(executionBase+"/"+run+"/start").header("X-Member-Id",member)).andExpect(status().isOk());
        var other=mapper.readTree(mvc.perform(post(executionBase).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(prepare)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String otherRun=other.path("run").path("runId").asText();
        mvc.perform(post(executionBase+"/"+otherRun+"/start").header("X-Member-Id",member)).andExpect(status().isConflict());
        mvc.perform(post(executionBase+"/"+run+"/cancel").header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.run.status").value("CANCELED"));
        mvc.perform(post(executionBase+"/"+run+"/start").header("X-Member-Id",member)).andExpect(status().isConflict());
        // Exercise the actual persistence worker with a deterministic AI transport, separately from live model QA.
        var fakeAi=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        fakeAi.createContext("/api/v1/test-executions/feature",exchange->{
            var body=mapper.readTree(exchange.getRequestBody());var answer=mapper.createObjectNode();var values=answer.putArray("items");
            body.path("cases").forEach(tc->{var item=values.addObject().put("caseId",tc.path("id").asText()).put("executionStatus","COMPLETED").put("judgmentStatus","COMPLETED").put("result","P").put("reason","격리된 워커 저장 검증").put("requirementCoverageComplete",true);item.putArray("conditionAssessments").addObject().put("conditionId","C1").put("outcome","PASS").put("grounded",true);});
            answer.putArray("evidence").addObject().put("id","fixture-observation").put("text","테스트 증거");answer.putArray("plans");
            byte[] bytes=mapper.writeValueAsBytes(answer);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });fakeAi.start();
        var worker=new TestExecutionWorker(jdbc,mapper,executions,setups,cipher,"http://127.0.0.1:"+fakeAi.getAddress().getPort(),"test-token");
        try {
            // Only one function TC: common/manual groups stay outside this worker transport test.
            String caseId=snapshot.path("items").get(0).path("caseId").asText();
            var workerRun=executions.prepare(submission,member,UUID.fromString(set),"https://example.com","",List.of(caseId),false).path("run");
            UUID workerRunId=UUID.fromString(workerRun.path("runId").asText());executions.transition(submission,member,workerRunId,false);
            // Keep the competing prepared history, but no other queued work.
            worker.processNext();
            var finished=executions.status(submission,member,workerRunId).path("run");
            assertEquals("COMPLETED",finished.path("status").asText());assertEquals(1,finished.path("passCount").asInt());
            assertEquals("P",finished.path("snapshot").path("items").get(0).path("result").asText());
            assertTrue(finished.path("snapshot").path("groupResults").size()>0);
            // A stale worker attempt cannot be considered active after completion.
            UUID attempt=jdbc.queryForObject("SELECT attempt FROM test_execution_runs WHERE run_id=?",UUID.class,workerRunId);
            assertFalse(worker.active(workerRunId,attempt));
            assertTrue(first.path("set").path("output").path("testCases").get(0).path("result").isNull());
            String review=mapper.writeValueAsString(Map.of("caseId",caseId,"revision",0,"result","F","reason","담당자가 실제 결과 불일치를 확인함","disposition","CONFIRMED"));
            mvc.perform(post(executionBase+"/"+workerRunId+"/review").header("X-Member-Id",member+1).contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isForbidden());
            mvc.perform(post(executionBase+"/"+workerRunId+"/review").header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isOk()).andExpect(jsonPath("$.run.snapshot.reviewRevision").value(1));
            mvc.perform(post(executionBase+"/"+workerRunId+"/review").header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isConflict());
            mvc.perform(get(executionBase+"/"+workerRunId+"/report").header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
            String retryPayload=mapper.writeValueAsString(Map.of("caseIds",List.of(caseId),"productVersion","patch-2"));
            var retried=mapper.readTree(mvc.perform(post(executionBase+"/"+workerRunId+"/retry").header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(retryPayload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            UUID retryId=UUID.fromString(retried.path("run").path("runId").asText());
            assertEquals(workerRunId.toString(),retried.path("run").path("snapshot").path("retryOf").asText());
            assertEquals(1,retried.path("run").path("snapshot").path("previousAttempts").size());
            executions.transition(submission,member,retryId,false);worker.processNext();
            var guarded=executions.status(submission,member,retryId).path("run");
            assertEquals("PARTIAL",guarded.path("status").asText());assertTrue(guarded.path("snapshot").path("items").get(0).path("result").isNull());
            assertTrue(guarded.path("snapshot").path("items").get(0).path("reason").asText().contains("이전 실패"));
            assertEquals("F",executions.status(submission,member,workerRunId).path("run").path("snapshot").path("items").get(0).path("result").asText());
            var audits=executions.status(submission,member,workerRunId).path("run").path("snapshot").path("reviews");assertEquals(1,audits.size());assertEquals("P",audits.get(0).path("before").asText());
        } finally {worker.close();fakeAi.stop(0);}
        jdbc.update("UPDATE functional_menu_jobs SET revision=2 WHERE job_id=?",menu);
        mvc.perform(post(executionBase+"/"+otherRun+"/start").header("X-Member-Id",member)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXECUTION_SOURCE_CHANGED"));
        mvc.perform(get(executionBase).param("run",run).header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.run.stale").value(true)).andExpect(jsonPath("$.run.snapshot.caseCount").value(77));
        mvc.perform(get(base).header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.set.stale").value(true));
        mvc.perform(get(base+"/"+set+"/download").header("X-Member-Id",member)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TC_SOURCE_CHANGED"));
        jdbc.update("UPDATE functional_menu_jobs SET status='DRAFT' WHERE job_id=?",menu);
        mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TC_MENU_APPROVAL_REQUIRED"));
    }
}
