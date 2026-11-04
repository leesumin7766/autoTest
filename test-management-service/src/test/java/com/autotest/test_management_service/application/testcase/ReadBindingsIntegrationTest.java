package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"execution.worker.enabled=false","product-description.internal-token=test-token","s3.enabled=false","s3.access-key=test","s3.secret-key=test","playwright.credentials-key-file=./build/test-playwright/credentials.key"})
@AutoConfigureMockMvc
@Transactional
class ReadBindingsIntegrationTest {
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
    @Test void ownedBindingsPersistWithOptimisticRevisionAndLeaveRunSnapshotsUntouched() throws Exception {
        UUID submission=UUID.randomUUID(),menu=UUID.randomUUID(),set=UUID.randomUUID(),run=UUID.randomUUID();long member=92461;
        var fixture=new ReadSpecificationBindingsTest();var tc=fixture.tc().put("featureId","reports");
        tc.putObject("source").put("menuJobId",menu.toString());
        var output=mapper.createObjectNode();output.putArray("testCases").add(tc);
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES(?,?,1,'PARSED',now())",submission,member);
        jdbc.update("INSERT INTO functional_menu_jobs(job_id,submission_id,member_id,document_digest,product_name,rules_version,status,generation_mode,revision,snapshot,output) VALUES(?,?,?,?,'Synthetic','functional-v1','APPROVED','REAL',1,?::jsonb,'{}'::jsonb)",
            menu,submission,member,"a".repeat(64),"{\"evidence\":[{\"menuKey\":\"reports\",\"url\":\"https://example.test/reports\",\"webPath\":[\"Reports\"]}]}");
        jdbc.update("INSERT INTO menu_tc_sets(set_id,submission_id,member_id,menu_job_id,menu_revision,input_digest,template_version,output) VALUES(?,?,?,?,1,?,'test',?::jsonb)",set,submission,member,menu,"b".repeat(64),output.toString());
        jdbc.update("INSERT INTO test_execution_runs(run_id,submission_id,member_id,tc_set_id,status,snapshot) VALUES(?,?,?,?,'PREPARED',?::jsonb)",run,submission,member,set,output.toString());
        var menuStatus=mapper.createObjectNode();menuStatus.putObject("job").put("status","APPROVED").put("revision",1).put("stale",false);
        when(menus.status(submission,member,menu)).thenReturn(menuStatus);
        when(setups.configurationVersion(submission,member)).thenReturn("fixture-setup");
        var unbound=executions.prepare(submission,member,set,"https://example.test","",List.of("read-1"),false);
        assertEquals("NEEDS_CONTEXT",unbound.path("run").path("snapshot").path("items").get(0).path("executionStatus").asText());
        String url="/api/submissions/"+submission+"/menu-test-cases/"+set+"/cases/read-1/read-bindings";
        mvc.perform(get(url).header("X-Member-Id",member+1)).andExpect(status().isForbidden());
        mvc.perform(get(url).header("X-Member-Id",member)).andExpect(status().isOk()).andExpect(jsonPath("$.targets[0].label").value("Reports"));
        var request=fixture.request().put("revision",0).put("sourceTcDigest",tc.path("executionPlan").path("specification").path("sourceTcDigest").asText());
        mvc.perform(put(url).header("X-Member-Id",member+1).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isForbidden());
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tc.executionPlan.specification.status").value("READY")).andExpect(jsonPath("$.tc.executionPlan.specification.bindingRevision").value(1));
        mvc.perform(get(url).header("X-Member-Id",member)).andExpect(jsonPath("$.tc.executionPlan.specification.bindingRevision").value(1));
        var prepared=executions.prepare(submission,member,set,"https://example.test","",List.of("read-1"),false);
        var preparedItem=prepared.path("run").path("snapshot").path("items").get(0);
        assertEquals("PENDING",preparedItem.path("executionStatus").asText());
        assertEquals(1,preparedItem.path("tc").path("executionPlan").path("specification").path("bindingRevision").asInt());
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isConflict());
        assertEquals(output,mapper.readTree(jdbc.queryForObject("SELECT snapshot::text FROM test_execution_runs WHERE run_id=?",String.class,run)));
        var saved=mapper.readTree(jdbc.queryForObject("SELECT output::text FROM menu_tc_sets WHERE set_id=?",String.class,set));
        var before=tc.deepCopy();before.remove("executionPlan");var after=(ObjectNode)saved.path("testCases").get(0).deepCopy();after.remove("executionPlan");assertEquals(before,after);
        request.put("revision",1);((ObjectNode)request.path("target")).put("href","https://forged.test");
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isBadRequest());
        ((ObjectNode)menuStatus.path("job")).put("stale",true);
        mvc.perform(put(url).header("X-Member-Id",member).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isConflict());
        mvc.perform(get(url.replace(set.toString(),UUID.randomUUID().toString())).header("X-Member-Id",member)).andExpect(status().isNotFound());
    }
}
