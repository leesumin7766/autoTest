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

@SpringBootTest(properties={"execution.worker.enabled=false","product-description.internal-token=test-token","s3.enabled=false","s3.access-key=test","s3.secret-key=test","playwright.credentials-key-file=./build/test-playwright/credentials.key"})
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
    @org.springframework.test.context.bean.override.mockito.MockitoBean ExecutionReadinessService readiness;
    @org.junit.jupiter.api.BeforeEach void readyRuntime(){
        org.mockito.Mockito.when(readiness.status()).thenReturn(new ObjectMapper().createObjectNode().put("executorConnected",true).put("judgeConnected",true));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired SubmissionPersistenceService persistence;
    @Autowired TestExecutionService executions;
    @Autowired com.autotest.test_management_service.application.crawler.PlaywrightSetupService setups;
    @Autowired com.autotest.test_management_service.application.crawler.PlaywrightCredentialCipher cipher;
    @Test void executorStatusListsOwnActiveLocalJobsOnly() throws Exception {
        UUID submission=UUID.randomUUID(),own=UUID.randomUUID();long member=92352;
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES(?,?,1,'PARSED',now())",submission,member);
        for(int i=0;i<2;i++)jdbc.update("INSERT INTO playwright_crawl_jobs(job_id,submission_id,member_id,status,setup_snapshot,document_digest,execution_mode,purpose) VALUES(?,?,?,?,'{\"password\":\"never-expose\"}'::jsonb,?,'LOCAL','CONNECTION')",
            i==0?own:UUID.randomUUID(),submission,member,i==1?"COMPLETED":"RUNNING","a".repeat(64));
        org.mockito.Mockito.when(readiness.status()).thenReturn(mapper.createObjectNode().put("browserConnected",true).put("executorBusy",true));
        String body=mvc.perform(get("/api/crawl-agents/local/status").header("X-Member-Id",member))
            .andExpect(status().isOk()).andExpect(jsonPath("$.busy").value(true)).andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.jobs.length()").value(1)).andExpect(jsonPath("$.jobs[0].id").value(own.toString())).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("never-expose"));
        mvc.perform(get("/api/crawl-agents/local/status").header("X-Member-Id",member+1)).andExpect(status().isOk()).andExpect(jsonPath("$.jobs.length()").value(0));
    }
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.set.output.caseCount").value(84));
        // Real XLSX upload -> persisted selectable menu -> generated TC -> downloadable workbook.
        byte[] menuExcel;
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var bytes=new java.io.ByteArrayOutputStream()){
            var sheet=book.createSheet("메뉴트리");var header=sheet.createRow(0);String[] labels={"1depth","2depth","3depth","4depth","5depth","메뉴 설명"};
            for(int i=0;i<labels.length;i++)header.createCell(i).setCellValue(labels[i]);
            var row=sheet.createRow(1);String[] values={"업로드 검증 제품","관리","사용자 관리","","사용자 조회","사용자를 조회하는 기능"};for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);book.write(bytes);menuExcel=bytes.toByteArray();
        }
        var xlsx=new org.springframework.mock.web.MockMultipartFile("file","메뉴트리.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",menuExcel);
        var excelUpload=mapper.readTree(mvc.perform(multipart(uploadUrl).file(xlsx).header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.job.status").value("APPROVED")).andReturn().getResponse().getContentAsByteArray());
        String excelMenu=excelUpload.path("job").path("jobId").asText();
        mvc.perform(get("/api/submissions/"+submission+"/functional-menu").param("job",excelMenu).header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.job.output.rows[0].functionName").value("사용자 조회"));
        var excelSet=mapper.readTree(mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("menuJobId",excelMenu)))).andExpect(status().isOk()).andExpect(jsonPath("$.set.output.caseCount").value(84)).andReturn().getResponse().getContentAsByteArray());
        mvc.perform(get(base+"/"+excelSet.path("set").path("setId").asText()+"/download").header("X-Member-Id",member)).andExpect(status().isOk());
        // An uploaded menu has no crawl link; saved login settings must still add login TCs.
        var loginSetup=mapper.createObjectNode().put("targetUrl","https://example.com").put("loginMethod","FORM_AUTO").put("testEnvironmentConfirmed",true).put("password","test-secret");
        com.fasterxml.jackson.databind.node.ObjectNode config=mapper.valueToTree(com.autotest.test_management_service.application.crawler.CrawlAutomation.defaults());
        config.put("username","fixture-user").put("loginUrl","https://example.com/sign-in");loginSetup.set("automation",config);
        setups.save(submission,member,mapper.treeToValue(loginSetup,com.autotest.test_management_service.application.crawler.PlaywrightSetupService.SetupRequest.class));
        var loginPayload=Map.of("menuJobId",excelMenu,"loginUrl","https://example.com");
        var withLogin=mapper.readTree(mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.set.output.summary.로그인").value(1))
                .andExpect(jsonPath("$.set.output.caseCount").value(111)).andReturn().getResponse().getContentAsByteArray());
        assertEquals("https://example.com",withLogin.path("set").path("output").path("testCases").get(0).path("executionPlan").path("targetUrl").asText());
        assertFalse(withLogin.toString().contains("test-secret"));assertFalse(withLogin.toString().contains("fixture-user"));
        mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("menuJobId",excelMenu,"loginUrl","https://unrelated.example"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TC_LOGIN_URL_INVALID"));
        jdbc.update("DELETE FROM playwright_crawl_setups WHERE submission_id=?",submission);
        // Authentication is generated even when absent from the approved sidebar features.
        jdbc.update("UPDATE functional_menu_jobs SET snapshot=?::jsonb WHERE job_id=?", "{\"warnings\":[],\"evidence\":[{\"id\":\"doc:auth\",\"kind\":\"DOCUMENT\",\"text\":\"아이디와 비밀번호로 로그인합니다. 비밀번호 5회 실패 시 계정 잠금\"}]}",menu);
        var authenticationSet=mapper.readTree(mvc.perform(post(base).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("menuJobId",menu))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.set.output.caseCount").value(111))
                .andExpect(jsonPath("$.set.output.testCases[0].ruleId").value("LOGIN-04"))
                .andExpect(jsonPath("$.set.output.source.authentication.failedAttemptLimit").value(5)).andReturn().getResponse().getContentAsByteArray());
        mvc.perform(get(base+"/"+authenticationSet.path("set").path("setId").asText()+"/download").header("X-Member-Id",member)).andExpect(status().isOk());
        jdbc.update("UPDATE functional_menu_jobs SET snapshot='{\"warnings\":[],\"evidence\":[]}'::jsonb WHERE job_id=?",menu);
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
        for(var item:snapshot.path("items")){assertTrue(item.path("result").isNull());assertEquals(item.path("tc").path("featureId").asText().startsWith("scope:")?"NEEDS_CONTEXT":"PENDING",item.path("executionStatus").asText());assertTrue(item.path("evidence").isEmpty());}
        mvc.perform(get(executionBase).param("run",run).header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.run.snapshot.caseCount").value(77));
        mvc.perform(get(executionBase).param("run",run).header("X-Member-Id",member+1)).andExpect(status().isForbidden());
        org.mockito.Mockito.doThrow(new com.autotest.test_management_service.application.productdescription.ProductDescriptionException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"EXECUTION_NOT_READY","실행기 준비 필요")).when(readiness).requireReady();
        mvc.perform(post(executionBase+"/"+run+"/start").header("X-Member-Id",member)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EXECUTION_NOT_READY"));
        assertEquals("PREPARED",executions.status(submission,member,UUID.fromString(run)).path("run").path("status").asText());
        org.mockito.Mockito.doNothing().when(readiness).requireReady();
        mvc.perform(put("/api/submissions/"+submission+"/playwright-setup").header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUrl\":\"https://other.example.com\",\"loginMethod\":\"NONE\",\"testEnvironmentConfirmed\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post(executionBase+"/"+run+"/start").header("X-Member-Id",member)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXECUTION_LOGIN_SETUP_MISMATCH"));
        mvc.perform(put("/api/submissions/"+submission+"/playwright-setup").header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content("{\"targetUrl\":\"https://example.com\",\"loginMethod\":\"NONE\",\"testEnvironmentConfirmed\":true}")).andExpect(status().isOk());
        mvc.perform(post(executionBase+"/"+run+"/start").header("X-Member-Id",member)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXECUTION_LOGIN_SETUP_CHANGED"));
        jdbc.update("DELETE FROM playwright_crawl_setups WHERE submission_id=?",submission);
        mvc.perform(post(executionBase+"/"+run+"/start").header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.run.status").value("WAITING_EXECUTOR"));
        mvc.perform(post(executionBase+"/"+run+"/start").header("X-Member-Id",member)).andExpect(status().isOk());
        var other=mapper.readTree(mvc.perform(post(executionBase).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(prepare)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String otherRun=other.path("run").path("runId").asText();
        mvc.perform(post(executionBase+"/"+otherRun+"/start").header("X-Member-Id",member)).andExpect(status().isConflict());
        mvc.perform(post(executionBase+"/"+run+"/cancel").header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.run.status").value("CANCELED"));
        mvc.perform(post(executionBase+"/"+run+"/start").header("X-Member-Id",member)).andExpect(status().isConflict());
        String commonId="";for(var item:snapshot.path("items"))if(item.path("tc").path("featureId").asText().startsWith("scope:")){commonId=item.path("caseId").asText();break;}
        var common=executions.prepare(submission,member,UUID.fromString(set),"https://example.com","",List.of(commonId),false).path("run");
        assertEquals(1,common.path("notExecutedCount").asInt());
        mvc.perform(post(executionBase+"/"+common.path("runId").asText()+"/start").header("X-Member-Id",member)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXECUTION_PREREQUISITES_REQUIRED"));
        // Continue only unfinished function TCs; common/manual cases and completed verdicts stay preserved.
        var oldSnapshot=(com.fasterxml.jackson.databind.node.ObjectNode)snapshot.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)oldSnapshot.path("items").get(0)).put("executionStatus","COMPLETED").put("result","F");
        jdbc.update("UPDATE test_execution_runs SET snapshot=?::jsonb WHERE run_id=?",oldSnapshot.toString(),UUID.fromString(run));
        mvc.perform(post(executionBase+"/"+run+"/resume").header("X-Member-Id",member+1)).andExpect(status().isForbidden());
        var continued=mapper.readTree(mvc.perform(post(executionBase+"/"+run+"/resume").header("X-Member-Id",member)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("run");
        assertEquals(run,continued.path("snapshot").path("resumedFrom").asText());assertTrue(continued.path("snapshot").path("caseCount").asInt()<77);
        for(var item:continued.path("snapshot").path("items")){assertNotEquals(oldSnapshot.path("items").get(0).path("caseId"),item.path("caseId"));assertFalse(item.path("tc").path("featureId").asText().startsWith("scope:"));}
        assertEquals("F",executions.status(submission,member,UUID.fromString(run)).path("run").path("snapshot").path("items").get(0).path("result").asText());
        oldSnapshot.put("allowMutation",true).put("currentCaseId",oldSnapshot.path("items").get(1).path("caseId").asText());
        jdbc.update("UPDATE test_execution_runs SET snapshot=?::jsonb WHERE run_id=?",oldSnapshot.toString(),UUID.fromString(run));
        var safeContinuation=mapper.readTree(mvc.perform(post(executionBase+"/"+run+"/resume").header("X-Member-Id",member)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("run").path("snapshot");
        assertEquals(1,safeContinuation.path("resumeNeedsReview").size());assertEquals(1,safeContinuation.path("preservedCount").asInt());
        for(var item:safeContinuation.path("items"))assertNotEquals(oldSnapshot.path("currentCaseId"),item.path("caseId"));
        jdbc.update("UPDATE test_execution_runs SET snapshot=?::jsonb WHERE run_id=?",snapshot.toString(),UUID.fromString(run));
        UUID progressAttempt=UUID.randomUUID();
        jdbc.update("UPDATE test_execution_runs SET status='RUNNING',attempt=? WHERE run_id=?",progressAttempt,UUID.fromString(otherRun));
        String progressBody=mapper.writeValueAsString(Map.of("attempt",progressAttempt,"stage","PLANNING","round",1));
        String progressEndpoint="/api/internal/test-executions/"+otherRun+"/progress";
        mvc.perform(post(progressEndpoint).header("X-Internal-Token","invalid").contentType(MediaType.APPLICATION_JSON).content(progressBody)).andExpect(status().isForbidden());
        mvc.perform(post(progressEndpoint).header("X-Internal-Token","test-token").contentType(MediaType.APPLICATION_JSON).content(progressBody)).andExpect(status().isOk());
        assertEquals("PLANNING",executions.status(submission,member,UUID.fromString(otherRun)).path("run").path("snapshot").path("progress").path("stage").asText());
        jdbc.update("UPDATE test_execution_runs SET status='PREPARED',attempt=NULL WHERE run_id=?",UUID.fromString(otherRun));
        mvc.perform(post(progressEndpoint).header("X-Internal-Token","test-token").contentType(MediaType.APPLICATION_JSON).content(progressBody)).andExpect(status().isConflict());
        // Exercise the actual persistence worker with a deterministic AI transport, separately from live model QA.
        var fakeAi=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var aiHttpStatus=new java.util.concurrent.atomic.AtomicInteger(200);
        fakeAi.createContext("/api/v1/test-executions/feature",exchange->{
            // Uvicorn's HTTP/1 endpoint must not receive Java's default h2c upgrade.
            if(exchange.getRequestHeaders().containsKey("Upgrade")){exchange.sendResponseHeaders(400,-1);exchange.close();return;}
            if(aiHttpStatus.get()!=200){exchange.sendResponseHeaders(aiHttpStatus.get(),-1);exchange.close();return;}
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
            assertTrue(finished.path("snapshot").path("groupResults").get(0).path("evidence").isEmpty());
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM test_execution_evidence WHERE run_id=?",Integer.class,workerRunId));
            mvc.perform(get(executionBase+"/"+workerRunId+"/evidence").param("caseId",caseId).header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.evidence[0].text").value("테스트 증거"));
            mvc.perform(get(executionBase+"/"+workerRunId+"/evidence").param("caseId",caseId).header("X-Member-Id",member+1)).andExpect(status().isForbidden());
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
            aiHttpStatus.set(422);
            var rejected=executions.prepare(submission,member,UUID.fromString(set),"https://example.com","",List.of(caseId),false).path("run");
            UUID rejectedId=UUID.fromString(rejected.path("runId").asText());executions.transition(submission,member,rejectedId,false);worker.processNext();
            var rejectedResult=executions.status(submission,member,rejectedId).path("run");
            assertEquals("FAILED",rejectedResult.path("status").asText());
            assertEquals("EXECUTION_AI_REQUEST_REJECTED",rejectedResult.path("snapshot").path("errorCode").asText());
            assertEquals(422,rejectedResult.path("snapshot").path("errorHttpStatus").asInt());
            var traceRun=executions.prepare(submission,member,UUID.fromString(set),"https://example.com","",List.of(caseId),false).path("run");
            UUID traceId=UUID.fromString(traceRun.path("runId").asText()),traceAttempt=UUID.randomUUID();
            jdbc.update("UPDATE test_execution_runs SET status='RUNNING',attempt=?,snapshot=jsonb_set(snapshot,'{currentCaseId}',to_jsonb(?::text)) WHERE run_id=?",traceAttempt,caseId,traceId);
            String progressUrl="/api/internal/test-executions/"+traceId+"/progress";
            var trace=mapper.createObjectNode().put("attempt",traceAttempt.toString()).put("stage","ACTING").put("round",1).put("caseId",caseId);
            trace.putArray("trace").addObject().put("planner","QWEN").put("decision","EXECUTE").put("explanation","관찰된 검색 버튼을 클릭");
            mvc.perform(post(progressUrl).header("X-Internal-Token","test-token").contentType(MediaType.APPLICATION_JSON).content(trace.toString())).andExpect(status().isOk());
            assertEquals("QWEN",executions.status(submission,member,traceId).path("run").path("snapshot").path("progress").path("trace").get(0).path("planner").asText());
            trace.put("caseId","another-case");
            mvc.perform(post(progressUrl).header("X-Internal-Token","test-token").contentType(MediaType.APPLICATION_JSON).content(trace.toString())).andExpect(status().isConflict());
            trace.put("caseId",caseId).put("attempt",UUID.randomUUID().toString());
            mvc.perform(post(progressUrl).header("X-Internal-Token","test-token").contentType(MediaType.APPLICATION_JSON).content(trace.toString())).andExpect(status().isConflict());
            jdbc.update("UPDATE test_execution_runs SET snapshot=jsonb_set(snapshot,'{items,0,executionStatus}','\"EXCLUDED\"'::jsonb) WHERE run_id=?",traceId);
            var selectionCounts=executions.status(submission,member,traceId).path("run");
            assertEquals(1,selectionCounts.path("excludedCount").asInt());
            assertEquals(0,selectionCounts.path("notExecutedCount").asInt());
            assertTrue(selectionCounts.path("snapshot").path("items").get(0).path("result").isNull());
            executions.transition(submission,member,traceId,true);
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
