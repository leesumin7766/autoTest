package com.autotest.test_management_service.application.crawler;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest(properties = {"s3.enabled=false", "s3.access-key=test", "s3.secret-key=test",
        "product-description.internal-token=local-check-test",
        "playwright.credentials-key-file=./build/test-playwright/credentials.key"})
@AutoConfigureMockMvc
@Transactional
class PlaywrightSetupIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = System.getenv().getOrDefault("AUTOTEST_TEST_DATABASE_URL", "jdbc:postgresql://localhost:5432/autotest_test");
            if ("/autotest".equalsIgnoreCase(URI.create(url.substring(5)).getPath()))
                throw new IllegalStateException("Tests require an isolated database");
            return url;
        });
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired SubmissionPersistenceService persistence;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired PlaywrightCredentialCipher cipher;
    @Autowired RemoteCrawlService remote;
    @org.springframework.test.context.bean.override.mockito.MockitoBean LocalCrawlDispatcher dispatcher;
    @Autowired jakarta.persistence.EntityManager entityManager;
    UUID id;
    String endpoint;

    @BeforeEach void seed() {
        id = UUID.randomUUID();
        endpoint = "/api/submissions/" + id + "/playwright-setup";
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES (?,8101,1,'PARSED',now())", id);
        for (String role : List.of("FUNCTION_LIST", "MANUAL"))
            jdbc.update("""
                    INSERT INTO submission_files(file_id,submission_id,role,file_type,file_format,original_filename,
                        mime_type,stored_path,extracted_text,status,uploaded_at)
                    VALUES (?, ?, ?, 'PDF', 'PDF', ?, 'application/pdf', 's3://autotest-docs/test', ?, 'PARSED', now())
                    """, UUID.randomUUID(), id, role, role + ".pdf", "test content " + role);
        String digest = DocumentDigest.of(persistence.findDocumentsBySubmissionId(SubmissionId.of(id)));
        jdbc.update("""
                INSERT INTO submission_ai_deliveries(submission_id,status,attempt_count,preflight_result,verified_document_digest,updated_at)
                VALUES (?,'DELIVERED',1,'{"decision":"READY_WITH_WARNINGS","warnings":[]}'::jsonb,?,now())
                """, id, digest);
    }

    ObjectNode request() {
        ObjectNode body = mapper.createObjectNode();
        body.put("targetUrl", "https://test.example/dashboard"); body.put("loginMethod", "FORM_AUTO");
        body.put("testEnvironmentConfirmed", true); body.put("password", "test-secret-password");
        ObjectNode config = mapper.valueToTree(CrawlAutomation.defaults());
        config.put("username", "qa-admin"); config.put("productVersion", "2.0");
        config.put("loginUrl", "https://test.example/login");
        body.set("automation", config);
        return body;
    }

    String save(ObjectNode body) throws Exception {
        return mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.setup.passwordConfigured").value(true))
                .andReturn().getResponse().getContentAsString();
    }

    @Test void storesEncryptedCredentialReturnsOnlyFlagAndPreservesPasswordOnEdit() throws Exception {
        String response = save(request());
        assertFalse(response.contains("test-secret-password"));
        assertFalse(response.contains("ciphertext"));
        String ciphertext = jdbc.queryForObject("SELECT password_ciphertext FROM playwright_crawl_setups WHERE submission_id=?", String.class, id);
        assertNotEquals("test-secret-password", ciphertext);
        assertEquals("test-secret-password", cipher.decrypt(id, ciphertext));
        var edit = request(); edit.put("password", ""); edit.put("crawlScopeNotes", "dashboard");
        save(edit);
        assertEquals(ciphertext, jdbc.queryForObject("SELECT password_ciphertext FROM playwright_crawl_setups WHERE submission_id=?", String.class, id));
        mvc.perform(get(endpoint).header("X-Member-Id", "8101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.setup.automation.productVersion").value("2.0"))
                .andExpect(jsonPath("$.setup.password").doesNotExist())
                .andExpect(jsonPath("$.setup.passwordConfigured").value(true));
        mvc.perform(get(endpoint).header("X-Member-Id", "8102")).andExpect(status().isForbidden());
    }

    @Test void accountOrLoginOriginChangeRequiresNewPasswordAndSwitchToNoLoginDeletesIt() throws Exception {
        save(request());
        var edit = request(); edit.put("password", ""); ((ObjectNode) edit.get("automation")).put("username", "other");
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(edit))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAYWRIGHT_PASSWORD_REQUIRED"));
        edit = request(); edit.put("password", "");
        ((ObjectNode) edit.get("automation")).put("loginUrl", "https://login.example/login")
                .putArray("allowedOrigins").add("https://login.example");
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(edit))).andExpect(status().isBadRequest());
        edit = request(); edit.put("loginMethod", "NONE"); edit.remove("password");
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(edit))).andExpect(status().isOk())
                .andExpect(jsonPath("$.setup.passwordConfigured").value(false));
        assertNull(jdbc.queryForObject("SELECT password_ciphertext FROM playwright_crawl_setups WHERE submission_id=?", String.class, id));
    }

    @Test void rejectsUnlistedLoginHostExcessiveCrawlLimitsAndBlockedPreflight() throws Exception {
        for (int seconds : new int[]{1, 60}) {
            var timed = request(); ((ObjectNode) timed.get("automation")).put("pageLoadTimeoutSeconds", seconds);
            mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(timed))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.setup.automation.pageLoadTimeoutSeconds").value(seconds));
        }
        for (int seconds : new int[]{0, 61}) {
            var timed = request(); ((ObjectNode) timed.get("automation")).put("pageLoadTimeoutSeconds", seconds);
            mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(timed))).andExpect(status().isBadRequest());
        }
        var body = request(); ((ObjectNode) body.get("automation")).put("loginUrl", "https://other.example/login");
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        body = request(); ((ObjectNode) body.get("automation")).put("maxPages", 501);
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE submission_ai_deliveries SET status='BLOCKED' WHERE submission_id=?", id);
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request()))).andExpect(status().isConflict());
    }

    private com.fasterxml.jackson.databind.JsonNode channel(RemoteCrawlService.Provisioned agent,String action,
            ObjectNode body,int expectedStatus) throws Exception {
        UUID nonce=UUID.randomUUID(); long timestamp=java.time.Instant.now().getEpochSecond();
        byte[] key=java.util.Base64.getUrlDecoder().decode(agent.token());
        var envelope=CrawlAgentWire.seal(key,CrawlAgentWire.requestAad(agent.agent().agentId(),action,timestamp,nonce),mapper.writeValueAsBytes(body));
        var result=mvc.perform(post("/api/crawl-agents/"+agent.agent().agentId()+"/channel/"+action)
                .header("X-Agent-Timestamp",timestamp).header("X-Agent-Nonce",nonce)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(envelope)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        assertFalse(result.contains("test-secret-password"));
        if(expectedStatus!=200) return mapper.readTree(result);
        return mapper.readTree(CrawlAgentWire.unseal(key,("response\n"+nonce).getBytes(java.nio.charset.StandardCharsets.UTF_8),mapper.readValue(result,CrawlAgentWire.Envelope.class)));
    }
    private RemoteCrawlService.Provisioned onlineAgent() throws Exception {
        var agent=remote.provision(8101,"isolated test PC");
        channel(agent,"heartbeat",mapper.createObjectNode(),200);
        return agent;
    }
    private void saveCrawlSetup() throws Exception {
        var body=request(); ((ObjectNode)body.get("automation")).put("successUrl","https://test.example/dashboard");
        save(body);
    }
    @Test void encryptedWorkerCapturesPagesIdempotentlyAndReturnsOwnedResults() throws Exception {
        saveCrawlSetup();
        var agent=onlineAgent();
        var started=remote.start(id,8101,null); // One online agent is selected automatically.
        assertEquals("QUEUED",started.path("job").path("status").asText());
        var job=channel(agent,"claim",mapper.createObjectNode(),200);
        assertEquals("test-secret-password",job.path("password").asText());
        assertFalse(remote.status(id,8101).toString().contains("test-secret-password"));
        var page=mapper.createObjectNode().put("event","page").put("jobId",job.path("jobId").asText())
                .put("claimToken",job.path("claimToken").asText()).put("pageIndex",0)
                .put("url","https://test.example/dashboard").put("title","Dashboard").put("bodyText","Overview");
        page.putArray("elements");page.putArray("links");
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB),"jpeg",bytes);
        page.put("screenshot",java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
        var menu=page.putArray("navigation").addObject().put("kind","LINK").put("url","https://test.example/dashboard")
                .put("frameUrl","https://test.example/dashboard").put("order",0);
        menu.putArray("path").add("System").add("Dashboard");
        String menuKey="a".repeat(64);
        menu.put("menuKey",menuKey);
        page.putObject("screenState").put("screenKey","b".repeat(64)).put("readiness","READY");
        page.putObject("transition").put("menuKey",menuKey).put("outcome","CONFIRMED");
        menu.put("url","https://outside.example/blocked");
        channel(agent,"event",page,400);
        menu.put("url","https://test.example/dashboard");
        channel(agent,"event",page,200);channel(agent,"event",page,200);
        assertEquals(1,remote.status(id,8101).path("job").path("pageCount").asInt());
        UUID jobId=UUID.fromString(job.path("jobId").asText());
        String menuEndpoint="/api/submissions/"+id+"/playwright-crawls/"+jobId+"/menu-tree";
        mvc.perform(get(menuEndpoint).header("X-Member-Id",8101)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].children[0].title").value("Dashboard"))
                .andExpect(jsonPath("$.nodes[0].children[0].status").value("CAPTURED"))
                .andExpect(jsonPath("$.nodes[0].children[0].pageIndex").value(0));
        mvc.perform(get(menuEndpoint).header("X-Member-Id",8102)).andExpect(status().isForbidden());
        mvc.perform(get("/api/submissions/"+id+"/playwright-crawls/"+UUID.randomUUID()+"/menu-tree").header("X-Member-Id",8101))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/submissions/"+id+"/playwright-crawls/"+jobId+"/pages/0/screenshot").header("X-Member-Id",8101))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        mvc.perform(get("/api/submissions/"+id+"/playwright-crawls/"+jobId+"/pages/0/screenshot").header("X-Member-Id",8102))
                .andExpect(status().isForbidden());
        var complete=mapper.createObjectNode().put("jobId",jobId.toString()).put("claimToken",job.path("claimToken").asText())
                .put("event","complete").put("stopReason","PAGE_LIMIT");
        channel(agent,"event",complete,200);
        assertEquals("COMPLETED",remote.status(id,8101).path("job").path("status").asText());
        assertNull(jdbc.queryForObject("SELECT password_ciphertext FROM playwright_crawl_jobs WHERE job_id=?",String.class,jobId));
        channel(agent,"event",page,409);
    }
    @Test void workerCannotReplayRequestsUseOtherAgentOrFinishCanceledJob() throws Exception {
        saveCrawlSetup(); var agent=onlineAgent();remote.start(id,8101,agent.agent().agentId());
        var job=channel(agent,"claim",mapper.createObjectNode(),200);
        var event=mapper.createObjectNode().put("jobId",job.path("jobId").asText()).put("claimToken",job.path("claimToken").asText()).put("event","heartbeat");
        var other=onlineAgent();channel(other,"event",event,409);
        UUID nonce=UUID.randomUUID();long timestamp=java.time.Instant.now().getEpochSecond();
        var envelope=CrawlAgentWire.seal(java.util.Base64.getUrlDecoder().decode(agent.token()),
                CrawlAgentWire.requestAad(agent.agent().agentId(),"heartbeat",timestamp,nonce),"{}".getBytes());
        for(int expected:new int[]{200,401})
            mvc.perform(post("/api/crawl-agents/"+agent.agent().agentId()+"/channel/heartbeat")
                    .header("X-Agent-Timestamp",timestamp).header("X-Agent-Nonce",nonce).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(envelope))).andExpect(status().is(expected));
        remote.cancel(id,8101,UUID.fromString(job.path("jobId").asText()));
        channel(agent,"event",event,409);
        remote.revoke(8101,agent.agent().agentId());
        channel(agent,"heartbeat",mapper.createObjectNode(),401);
    }
    @Test void workerRejectsExpiredLeaseAndRevalidatesDocumentBeforeClaim() throws Exception {
        saveCrawlSetup();var agent=onlineAgent();remote.start(id,8101,agent.agent().agentId());
        var job=channel(agent,"claim",mapper.createObjectNode(),200);
        UUID jobId=UUID.fromString(job.path("jobId").asText());
        jdbc.update("UPDATE playwright_crawl_jobs SET lease_expires_at=now()-interval '1 second' WHERE job_id=?",jobId);
        channel(agent,"event",mapper.createObjectNode().put("event","heartbeat").put("jobId",jobId.toString())
                .put("claimToken",job.path("claimToken").asText()),409);
        assertEquals("FAILED",remote.status(id,8101).path("job").path("status").asText());
        remote.start(id,8101,agent.agent().agentId());
        jdbc.update("UPDATE submission_files SET extracted_text='changed after queuing' WHERE submission_id=? AND role='MANUAL'",id);
        entityManager.clear();
        assertFalse(channel(agent,"claim",mapper.createObjectNode(),200).has("password"));
        assertEquals("DOCUMENT_VERSION_MISMATCH",remote.status(id,8101).path("job").path("errorCode").asText());
    }
    @Test void connectionCheckNeedsOnlyUrlAndUsesLocalRunnerWithoutRegisteringAgent() throws Exception {
        var draft=new PlaywrightSetupService.SetupRequest("https://test.example/login","NONE","","","","",true);
        var status=remote.start(id,8101,null,"LOCAL","CONNECTION",draft);
        var check=status.path("checks").path("CONNECTION");
        assertEquals("RUNNING",check.path("status").asText());
        assertEquals("현재 서버",check.path("agentName").asText());
        var payload=org.mockito.ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        org.mockito.Mockito.verify(dispatcher).dispatch(payload.capture(),org.mockito.ArgumentMatchers.any());
        assertFalse(payload.getValue().has("password"));
        assertEquals("CONNECTION",payload.getValue().path("purpose").asText());
        var event=mapper.createObjectNode().put("event","complete").put("jobId",check.path("jobId").asText())
                .put("claimToken",payload.getValue().path("claimToken").asText());
        event.putObject("result").put("httpStatus",401).put("elapsedMs",150);
        mvc.perform(post("/api/internal/playwright-crawls/events").header("X-Internal-Token","wrong")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/playwright-crawls/events").header("X-Internal-Token","local-check-test")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isOk());
        var completed=remote.status(id,8101).path("checks").path("CONNECTION");
        assertEquals("COMPLETED",completed.path("status").asText());
        assertEquals(401,completed.path("result").path("httpStatus").asInt());
        assertTrue(remote.status(id,8101).path("pages").isEmpty());
    }
    @Test void remoteLoginProbeAcceptsDraftWithoutSavingAndRequiresLoginProof() throws Exception {
        var agent=onlineAgent();
        var draft=mapper.treeToValue(request(),PlaywrightSetupService.SetupRequest.class);
        remote.start(id,8101,agent.agent().agentId(),"REMOTE","LOGIN",draft);
        var payload=channel(agent,"claim",mapper.createObjectNode(),200);
        assertEquals("LOGIN",payload.path("purpose").asText());
        assertEquals("test-secret-password",payload.path("password").asText());
        assertNull(mvc.perform(get(endpoint).header("X-Member-Id",8101)).andReturn().getResponse().getErrorMessage());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM playwright_crawl_setups WHERE submission_id=?",Integer.class,id));
        var event=mapper.createObjectNode().put("event","complete").put("jobId",payload.path("jobId").asText()).put("claimToken",payload.path("claimToken").asText());
        event.putObject("result").put("httpStatus",200).put("loginVerified",false);
        channel(agent,"event",event,200);
        assertEquals("FAILED",remote.status(id,8101).path("checks").path("LOGIN").path("status").asText());
    }
    @Test void integratedFunctionalMenuValidatesEvidenceReviewsAndExportsSixColumns() throws Exception {
        UUID crawl=UUID.randomUUID();String digest=jdbc.queryForObject("SELECT verified_document_digest FROM submission_ai_deliveries WHERE submission_id=?",String.class,id);
        jdbc.update("INSERT INTO playwright_crawl_jobs(job_id,submission_id,member_id,status,setup_snapshot,document_digest,page_count,execution_mode,purpose) VALUES (?,?,8101,'COMPLETED','{}'::jsonb,?,1,'LOCAL','CRAWL')",crawl,id,digest);
        String key="d".repeat(64);
        var nav=mapper.createArrayNode();nav.addObject().put("kind","BUTTON").put("menuKey",key).put("location","HEADER").putArray("path").add("도움말");
        jdbc.update("INSERT INTO playwright_crawl_pages(job_id,page_index,url,title,body_text,elements,links,screenshot,navigation) VALUES (?,0,'https://test.example/','Test','도움말을 조회할 수 있습니다.','[]'::jsonb,'[]'::jsonb,?,?::jsonb)",crawl,new byte[]{(byte)255,(byte)216,(byte)255},nav.toString());
        String base="/api/submissions/"+id+"/functional-menu";
        var start=mapper.createObjectNode().put("crawlJobId",crawl.toString()).put("productName","검증 제품").put("mode","EXTERNAL");
        var created=mapper.readTree(mvc.perform(post(base).header("X-Member-Id",8101).contentType(MediaType.APPLICATION_JSON).content(start.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String job=created.path("job").path("jobId").asText();String endpoint=base+"/"+job;
        var rows=mapper.createArrayNode();var row=rows.addObject().put("functionName","도움말 조회").put("menuDescription","도움말을 조회하는 기능").put("location","HEADER");
        row.putArray("menuPath").add("도움말");row.putArray("matchedMenuKeys").add(key);row.putArray("reviewNotes");row.putArray("evidence").addObject().put("sourceId","menu:"+key).put("quote","거짓 근거");
        var imported=mapper.createObjectNode().put("revision",0);imported.set("rows",rows);
        mvc.perform(post(endpoint+"/import").header("X-Member-Id",8101).contentType(MediaType.APPLICATION_JSON).content(imported.toString())).andExpect(status().isBadRequest());
        ((ObjectNode)row.path("evidence").get(0)).put("quote","도움말");
        var response=mapper.readTree(mvc.perform(post(endpoint+"/import").header("X-Member-Id",8101).contentType(MediaType.APPLICATION_JSON).content(imported.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var edit=mapper.createObjectNode().put("revision",response.path("job").path("revision").asInt());edit.set("rows",response.path("job").path("output").path("rows"));
        mvc.perform(put(endpoint+"/approval").header("X-Member-Id",8101).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.job.status").value("APPROVED"));
        mvc.perform(put(endpoint+"/draft").header("X-Member-Id",8101).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isConflict());
        byte[] bytes=mvc.perform(get(endpoint+"/download").header("X-Member-Id",8101)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))){assertEquals(6,book.getSheetAt(0).getRow(0).getLastCellNum());assertEquals("메뉴 설명",book.getSheetAt(0).getRow(0).getCell(5).getStringCellValue());assertEquals("검증 제품",book.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());assertEquals("도움말을 조회하는 기능",book.getSheetAt(0).getRow(1).getCell(5).getStringCellValue());}
        mvc.perform(get(endpoint+"/download").header("X-Member-Id",8102)).andExpect(status().isForbidden());
        jdbc.update("UPDATE submission_files SET extracted_text='changed' WHERE submission_id=? AND role='MANUAL'",id);entityManager.clear();
        mvc.perform(get(endpoint+"/download").header("X-Member-Id",8101)).andExpect(status().isConflict());
    }
}
