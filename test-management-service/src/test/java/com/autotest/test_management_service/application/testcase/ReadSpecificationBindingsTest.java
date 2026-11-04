package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReadSpecificationBindingsTest {
    final ObjectMapper mapper = new ObjectMapper();
    ObjectNode tc() throws Exception {
        var tc=(ObjectNode)mapper.readTree("""
            {"id":"read-1","ruleId":"FEATURE-01","featurePath":["Reports"],"scenario":"Open reports",
             "inputs":"No filter is required","expectedResult":"Report list","evidence":[{"quote":"No filter is required"}],
             "executionPlan":{"targetUrl":"https://example.test","menuKeys":["reports"],"steps":["open","prepare","wait","compare"],
               "selection":{"scopeOperation":"READ"},"specification":{"status":"NEEDS_CONTEXT","timeoutSeconds":60,
                 "steps":[{},{},{},{}],"checks":[{"requirementQuote":"Report list","method":"REQUIRES_BINDING","status":"UNRESOLVED"}]}}}
            """);
        ((ObjectNode)tc.path("executionPlan")).put("tcContentDigest",MenuTcGenerator.hash("Open reportsNo filter is requiredReport list"));
        TcExecutionSpecification.upgradeRead(mapper,tc,mapper.createObjectNode().put("functionName","Reports"));
        return tc;
    }
    JsonNode hints() throws Exception {return mapper.readTree("""
        [{"menuKey":"reports","href":"https://example.test/reports","label":"Reports"}]
        """);}
    ObjectNode request() throws Exception {return (ObjectNode)mapper.readTree("""
        {"target":{"menuKey":"reports","href":"https://example.test/reports","heading":"Reports"},
         "readContext":{"mode":"NO_FILTER","confirmed":true,"oracle":{"kind":"TC_INPUT","quote":"No filter is required"}},
         "checks":[{"id":"C1","expected":"Report list","oracle":{"kind":"TC_EXPECTED","quote":"Report list"}}]}
        """);}
    @Test void completeBindingsPreserveTcAndBindEveryStep() throws Exception {
        var tc=tc();var before=tc.deepCopy();
        var result=ReadSpecificationBindings.apply(mapper,tc,request(),hints());
        assertEquals(before,tc);
        var spec=result.path("executionPlan").path("specification");
        assertEquals("READY",spec.path("status").asText());
        assertTrue(spec.path("missingBindings").isEmpty());
        for(var step:spec.path("steps"))assertEquals("BOUND",step.path("status").asText());
        var contract=mapper.createObjectNode();contract.set("tc",result.deepCopy());contract.set("unbound",before.deepCopy());contract.set("hints",hints());
        var file=java.nio.file.Path.of("build/test-fixtures/read-bindings.json");java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file,mapper.writeValueAsString(contract));
        result.remove("executionPlan");before.remove("executionPlan");assertEquals(before,result);
    }
    @Test void incompleteBindingsRemainSaveableButCannotRun() throws Exception {
        var result=ReadSpecificationBindings.apply(mapper,tc(),mapper.createObjectNode(),hints());
        assertEquals("NEEDS_CONTEXT",result.path("executionPlan").path("specification").path("status").asText());
        assertEquals(3,result.path("executionPlan").path("specification").path("missingBindings").size());
        assertFalse(TcExecutionSpecification.preparationReason(result).isBlank());
    }
    @Test void forgedTargetAndOracleAreRejected() throws Exception {
        var request=request();((ObjectNode)request.path("target")).put("href","https://attacker.test");
        var wrongTarget=request;assertThrows(RuntimeException.class,()->ReadSpecificationBindings.apply(mapper,tc(),wrongTarget,hints()));
        request=request();((ObjectNode)request.path("readContext").path("oracle")).put("quote","invented");
        var forged=request;assertThrows(RuntimeException.class,()->ReadSpecificationBindings.apply(mapper,tc(),forged,hints()));
    }
    @Test void semanticExpectedResultCannotBeDowngradedToTextPresence() throws Exception {
        var tc=tc();tc.put("expectedResult","목록이 정상적으로 표시된다");
        var plan=(ObjectNode)tc.path("executionPlan");var spec=(ObjectNode)plan.path("specification");
        String hash=MenuTcGenerator.hash(tc.path("scenario").asText()+tc.path("inputs").asText()+tc.path("expectedResult").asText());
        plan.put("tcContentDigest",hash);spec.put("sourceTcDigest",hash);
        ((ObjectNode)spec.path("checks").get(0)).put("requirementQuote",tc.path("expectedResult").asText());
        var request=request();var check=(ObjectNode)request.path("checks").get(0);
        check.put("expected",tc.path("expectedResult").asText());
        ((ObjectNode)check.path("oracle")).put("quote",tc.path("expectedResult").asText());
        var result=ReadSpecificationBindings.apply(mapper,tc,request,hints());
        assertEquals("NEEDS_CONTEXT",result.path("executionPlan").path("specification").path("status").asText());
    }
    @Test void changedSourceAndMissingCheckCannotBecomeReady() throws Exception {
        var changed=tc();changed.put("inputs","changed");
        assertThrows(RuntimeException.class,()->ReadSpecificationBindings.apply(mapper,changed,request(),hints()));
        var request=request();request.putArray("checks");
        assertEquals("NEEDS_CONTEXT",ReadSpecificationBindings.apply(mapper,tc(),request,hints()).path("executionPlan").path("specification").path("status").asText());
    }
}
