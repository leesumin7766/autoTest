package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** TC 수행 방식(자동·수동·제외): 저장 → 시험 준비 반영 → 수동 결과 입력 → 보고서 */
@SpringBootTest(properties={"execution.worker.enabled=false","product-description.internal-token=test-token","s3.enabled=false","s3.access-key=test","s3.secret-key=test","playwright.credentials-key-file=./build/test-playwright/credentials.key"})
@AutoConfigureMockMvc
@Transactional
class TcExecutionDecisionsIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->{
            String url=System.getenv().getOrDefault("AUTOTEST_TEST_DATABASE_URL","jdbc:postgresql://localhost:5432/autotest_test");
            if(!URI.create(url.substring(5)).getPath().endsWith("_test"))throw new IllegalStateException("An isolated *_test database is required");return url;
        });
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired TestExecutionService executions;
    @MockitoBean FunctionalMenuService menus;
    @MockitoBean com.autotest.test_management_service.application.crawler.PlaywrightSetupService setups;

    ObjectNode tc(String id,String feature,String automation){
        var tc=mapper.createObjectNode().put("id",id).put("tcId","TC-"+id).put("featureId",feature).put("ruleId","LOGIN-04")
            .put("scenario","시나리오 "+id).put("inputs","입력").put("expectedResult","기대").put("automationMode",automation).put("scopeDescription","범위");
        tc.putArray("featurePath").add("제품").add(id);tc.putObject("executionPlan").putArray("requiredContext").add("대상 기능의 구체적인 입력값·기대값과 독립 비교 근거");
        return tc;
    }

    @Test void decisionsPersistAndDriveRunPreparationAndManualResults() throws Exception {
        UUID submission=UUID.randomUUID(),menu=UUID.randomUUID(),set=UUID.randomUUID();long member=92471;
        var output=mapper.createObjectNode().put("productName","합성 제품");
        var cases=output.putArray("testCases");
        cases.add(tc("a1","f1","BROWSER_CANDIDATE"));cases.add(tc("a2","f2","BROWSER_CANDIDATE"));cases.add(tc("c1","scope:common","MANUAL_OR_TOOL"));
        var original=output.deepCopy();
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES(?,?,1,'PARSED',now())",submission,member);
        jdbc.update("INSERT INTO functional_menu_jobs(job_id,submission_id,member_id,document_digest,product_name,rules_version,status,generation_mode,revision,snapshot,output) VALUES(?,?,?,?,'Synthetic','functional-v1','APPROVED','REAL',1,'{}'::jsonb,'{}'::jsonb)",
            menu,submission,member,"a".repeat(64));
        jdbc.update("INSERT INTO menu_tc_sets(set_id,submission_id,member_id,menu_job_id,menu_revision,input_digest,template_version,output) VALUES(?,?,?,?,1,?,'test',?::jsonb)",set,submission,member,menu,"b".repeat(64),output.toString());
        var menuStatus=mapper.createObjectNode();menuStatus.putObject("job").put("status","APPROVED").put("revision",1).put("stale",false);
        when(menus.status(submission,member,menu)).thenReturn(menuStatus);
        when(setups.configurationVersion(submission,member)).thenReturn("fixture-setup");
        String base="/api/submissions/"+submission+"/menu-test-cases";

        // 기본값: 템플릿이 '수동·도구'로 표시한 공통 TC 는 수동, 나머지는 자동
        mvc.perform(get(base).header("X-Member-Id",member)).andExpect(status().isOk())
            .andExpect(jsonPath("$.set.output.decisionRevision").value(0))
            .andExpect(jsonPath("$.set.output.decisionSummary.AUTO").value(2)).andExpect(jsonPath("$.set.output.decisionSummary.MANUAL").value(1))
            .andExpect(jsonPath("$.set.output.testCases[2].decision.mode").value("MANUAL")).andExpect(jsonPath("$.set.output.testCases[2].decision.source").value("DEFAULT"));

        String url=base+"/"+set+"/decisions";
        String exclude="{\"revision\":0,\"decisions\":[{\"caseId\":\"a2\",\"mode\":\"EXCLUDED\",\"reason\":\"다국어 미제공\"}]}";
        mvc.perform(put(url).header("X-Member-Id",member+1).contentType(MediaType.APPLICATION_JSON).content(exclude)).andExpect(status().isForbidden());
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"decisions\":[{\"caseId\":\"a2\",\"mode\":\"EXCLUDED\"}]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TC_DECISION_REASON_REQUIRED"));
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"decisions\":[{\"caseId\":\"nope\",\"mode\":\"AUTO\"}]}"))
            .andExpect(status().isNotFound());
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(exclude)).andExpect(status().isOk())
            .andExpect(jsonPath("$.set.output.decisionRevision").value(1)).andExpect(jsonPath("$.set.output.decisionSummary.EXCLUDED").value(1))
            .andExpect(jsonPath("$.set.output.testCases[1].decision.reason").value("다국어 미제공"));
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(exclude)).andExpect(status().isConflict());

        // 저장본: 화면용 필드는 없고, TC 본문은 그대로
        var saved=(ObjectNode)mapper.readTree(jdbc.queryForObject("SELECT output::text FROM menu_tc_sets WHERE set_id=?",String.class,set));
        assertFalse(saved.has("decisionSummary"));
        for(var node:saved.path("testCases"))assertFalse(node.has("decision"));
        assertEquals("EXCLUDED",saved.path("testCases").get(1).path("executionDecision").path("mode").asText());
        var withoutDecision=saved.deepCopy();((ObjectNode)withoutDecision.path("testCases").get(1)).remove("executionDecision");withoutDecision.remove("decisionRevision");
        assertEquals(original,withoutDecision);

        // 기본값으로 되돌리면 기록을 지운다
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"decisions\":[{\"caseId\":\"a2\",\"mode\":\"AUTO\"}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.set.output.testCases[1].decision.source").value("DEFAULT"));
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(exclude.replace("\"revision\":0","\"revision\":2"))).andExpect(status().isOk());

        // 시험 준비: 자동은 대기, 수동은 MANUAL(실행기로 보내지 않음), 제외는 EXCLUDED
        var mixed=executions.prepare(submission,member,set,"https://example.test","",List.of("a1","a2","c1"),false).path("run");
        assertEquals("PREPARED",mixed.path("status").asText());
        Map<String,JsonNode> items=new HashMap<>();for(var item:mixed.path("snapshot").path("items"))items.put(item.path("caseId").asText(),item);
        assertEquals("PENDING",items.get("a1").path("executionStatus").asText());
        assertEquals("EXCLUDED",items.get("a2").path("executionStatus").asText());
        assertEquals("제외: 다국어 미제공",items.get("a2").path("reason").asText());
        assertEquals("MANUAL",items.get("c1").path("executionStatus").asText());
        assertEquals(1,mixed.path("manualCount").asInt());assertEquals(1,mixed.path("excludedCount").asInt());assertEquals(1,mixed.path("pendingCount").asInt());

        // 수동만 있는 실행은 실행기 없이 바로 닫혀 결과를 입력할 수 있다
        var manual=executions.prepare(submission,member,set,"https://example.test","",List.of("c1"),false).path("run");
        assertEquals("COMPLETED",manual.path("status").asText());
        String run=manual.path("runId").asText();
        mvc.perform(post("/api/submissions/"+submission+"/executions/"+run+"/review").header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"c1\",\"revision\":0,\"result\":\"P\",\"reason\":\"매뉴얼 3장과 화면 대조 확인\",\"disposition\":\"PENDING\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.run.manualDoneCount").value(1)).andExpect(jsonPath("$.run.passCount").value(1));
        String report=mvc.perform(get("/api/submissions/"+submission+"/executions/"+run+"/report").header("X-Member-Id",member)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(report.contains("수행 방식: 수동(시험원 입력)"));
        assertTrue(report.contains("판정: P"));

        // 시험 자료: 자동 TC 에 실행 시 필요한 정보를 적으면 상태가 바뀌고 시험 준비 스냅샷에 실린다
        String context=base+"/"+set+"/cases/a1/context";
        String body="{\"revision\":0,\"items\":[{\"need\":\"대상 기능의 구체적인 입력값·기대값과 독립 비교 근거\",\"value\":\"아이디 qa01 입력 후 목록에 qa01 표시\"}],\"note\":\"일반 사용자 권한\"}";
        mvc.perform(put(context).header("X-Member-Id",member+1).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(context).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(body.replace("아이디 qa01","password=abc12345"))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TC_CONTEXT_SECRET"));
        mvc.perform(put(context).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.set.output.testCases[0].context.status").value("COMPLETE"))
            .andExpect(jsonPath("$.set.output.testCases[0].context.revision").value(1));
        mvc.perform(put(context).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        var withContext=executions.prepare(submission,member,set,"https://example.test","",List.of("a1"),false).path("run").path("snapshot").path("items").get(0);
        assertEquals("아이디 qa01 입력 후 목록에 qa01 표시",withContext.path("tc").path("executionContext").path("items").get(0).path("value").asText());
        var stored=(ObjectNode)mapper.readTree(jdbc.queryForObject("SELECT output::text FROM menu_tc_sets WHERE set_id=?",String.class,set));
        assertFalse(stored.path("testCases").get(0).has("context"),"화면용 context 는 저장하지 않는다");
        assertEquals(original.path("testCases").get(0).path("inputs"),stored.path("testCases").get(0).path("inputs"),"TC 본문은 그대로");
    }
}
