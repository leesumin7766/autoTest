package com.autotest.test_management_service.application.testcase;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcExecutionDecisionsTest {
    final ObjectMapper mapper=new ObjectMapper();

    ObjectNode output(){
        var output=mapper.createObjectNode();var cases=output.putArray("testCases");
        cases.addObject().put("id","a").put("automationMode","BROWSER_CANDIDATE");
        cases.addObject().put("id","m").put("automationMode","MANUAL_OR_TOOL");
        cases.addObject().put("id","e").put("automationMode","ADDITIONAL_EVIDENCE");
        return output;
    }
    ObjectNode request(int revision,String id,String mode,String reason){
        var request=mapper.createObjectNode().put("revision",revision);var d=request.putArray("decisions").addObject().put("caseId",id).put("mode",mode);
        if(reason!=null)d.put("reason",reason);return request;
    }

    @Test void templateFlagsDecideTheDefault(){
        var o=output();
        assertEquals("AUTO",TcExecutionDecisions.mode(o.path("testCases").get(0)));
        assertEquals("MANUAL",TcExecutionDecisions.mode(o.path("testCases").get(1)));
        assertEquals("MANUAL",TcExecutionDecisions.mode(o.path("testCases").get(2)),"화면만으로 판정할 수 없는 추가 증거 TC 도 수동");
    }

    @Test void savedDecisionWinsAndUnknownSavedModeFallsBack(){
        var o=output();var tc=(ObjectNode)o.path("testCases").get(0);
        tc.putObject("executionDecision").put("mode","MANUAL");assertEquals("MANUAL",TcExecutionDecisions.mode(tc));
        tc.putObject("executionDecision").put("mode","WHATEVER");assertEquals("AUTO",TcExecutionDecisions.mode(tc));
    }

    @Test void applyValidatesAndBumpsRevision(){
        var o=output();
        assertThrows(ProductDescriptionException.class,()->TcExecutionDecisions.apply(o,request(1,"a","MANUAL",null),1),"오래된 revision");
        assertThrows(ProductDescriptionException.class,()->TcExecutionDecisions.apply(o,request(0,"a","EXCLUDED","  "),1),"제외는 사유 필수");
        assertThrows(ProductDescriptionException.class,()->TcExecutionDecisions.apply(o,request(0,"a","SKIP",null),1));
        assertThrows(ProductDescriptionException.class,()->TcExecutionDecisions.apply(o,request(0,"a","MANUAL","x".repeat(501)),1));
        assertThrows(ProductDescriptionException.class,()->TcExecutionDecisions.apply(o,mapper.createObjectNode().put("revision",0),1));
        assertEquals(0,o.path("decisionRevision").asInt(),"실패한 요청은 아무것도 바꾸지 않는다");
        TcExecutionDecisions.apply(o,request(0,"a","MANUAL","로그인 정책 확인은 사람이"),7);
        var saved=o.path("testCases").get(0).path("executionDecision");
        assertEquals("MANUAL",saved.path("mode").asText());assertEquals(7,saved.path("decidedBy").asLong());assertEquals(1,o.path("decisionRevision").asInt());
    }

    @Test void decorateAddsViewFieldsThatStripRemoves(){
        var o=output();TcExecutionDecisions.decorate(o);
        assertEquals(1,o.path("decisionSummary").path("AUTO").asInt());assertEquals(2,o.path("decisionSummary").path("MANUAL").asInt());
        assertEquals("DEFAULT",o.path("testCases").get(1).path("decision").path("source").asText());
        TcExecutionDecisions.strip(o);
        assertFalse(o.has("decisionSummary"));assertFalse(o.path("testCases").get(0).has("decision"));
    }
}
