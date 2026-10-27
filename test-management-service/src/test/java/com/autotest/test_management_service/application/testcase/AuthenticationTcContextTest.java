package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationTcContextTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void savedLoginConfigurationAddsOneLoginGroupAtBaseOrDedicatedUrlWithoutSecrets() {
        for(String dedicated:new String[]{"","https://example.com/sign-in"}) {
            var context=mapper.createObjectNode().put("required",false);context.putArray("evidence");
            var setup=mapper.createObjectNode().put("targetUrl","https://example.com/").put("loginMethod","FORM_AUTO").put("password","secret");
            setup.putObject("automation").put("loginUrl",dedicated).put("username","private-user");
            AuthenticationTcContext.applySetup(context,setup,false);
            assertTrue(context.path("required").asBoolean());assertFalse(context.path("verifiedCrawlAuthentication").asBoolean());
            assertFalse(context.toString().contains("secret"));assertFalse(context.toString().contains("private-user"));
            var prepared=AuthenticationTcContext.prepare(mapper,"제품",features(),context);
            String expected=dedicated.isBlank()?"https://example.com/":dedicated;
            assertEquals(expected,prepared.get(1).path("targetUrl").asText());
            assertEquals(2,AuthenticationTcContext.prepare(mapper,"제품",prepared,context).size());
            var source=mapper.createObjectNode();source.set("authentication",context);
            var output=new MenuTcGenerator(mapper).generate("제품",prepared,source);
            assertEquals(27,java.util.stream.StreamSupport.stream(output.path("testCases").spliterator(),false).filter(tc->"로그인".equals(tc.path("featureType").asText())).count());
            assertEquals(expected,output.path("testCases").get(0).path("executionPlan").path("targetUrl").asText());
        }
        var none=mapper.createObjectNode().put("required",false);AuthenticationTcContext.applySetup(none,mapper.createObjectNode().put("targetUrl","https://example.com/").put("loginMethod","NONE"),false);assertFalse(none.path("required").asBoolean());
    }
    ObjectNode snapshot(String text) {
        var s=mapper.createObjectNode();s.putArray("evidence").addObject().put("id","doc:manual").put("kind","DOCUMENT").put("text",text);return s;
    }
    ArrayNode features() {
        var rows=mapper.createArrayNode();var row=rows.addObject().put("featureId","query").put("functionName","사용자 조회");
        row.putArray("featurePath").add("제품").add("조회");return rows;
    }
    @Test void missingSidebarLoginIsInsertedFirstAndDocumentPolicyUsed() {
        for(int count:new int[]{3,5,10}) {
            var context=AuthenticationTcContext.detect(mapper,snapshot("아이디와 비밀번호를 입력하여 로그인합니다.\n비밀번호 연속 "+count+"회 실패 시 계정 잠금"),mapper.createArrayNode());
            assertTrue(context.path("required").asBoolean());assertEquals(count,context.path("failedAttemptLimit").asInt());
            var source=mapper.createObjectNode();source.set("authentication",context);
            var prepared=AuthenticationTcContext.prepare(mapper,"제품",features(),context);
            var output=new MenuTcGenerator(mapper).generate("제품",prepared,source);
            assertEquals("LOGIN-04",output.path("testCases").get(0).path("ruleId").asText());
            assertEquals(27+16+68,output.path("caseCount").asInt()); // Login + READ CRUD + product scopes.
            JsonNode lock=null;for(var tc:output.path("testCases"))if("LOGIN-17".equals(tc.path("ruleId").asText()))lock=tc;
            assertNotNull(lock);assertTrue(lock.path("scenario").asText().contains(count+"회"));
            assertTrue(lock.path("executionPlan").path("requiredContext").toString().contains("전용 계정"));
            assertFalse(lock.path("evidence").isEmpty());
        }
    }
    @Test void absentUnknownAndConflictingPoliciesAreNotGuessed() {
        assertFalse(AuthenticationTcContext.detect(mapper,snapshot("로그인 기능은 없음"),mapper.createArrayNode()).path("required").asBoolean());
        var unknown=AuthenticationTcContext.detect(mapper,snapshot("아이디로 로그인합니다"),mapper.createArrayNode());
        assertTrue(unknown.path("required").asBoolean());assertFalse(unknown.has("failedAttemptLimit"));
        var conflict=AuthenticationTcContext.detect(mapper,snapshot("비밀번호 3회 실패 시 잠금\n비밀번호 5회 실패 시 잠금"),mapper.createArrayNode());
        assertFalse(conflict.has("failedAttemptLimit"));assertTrue(conflict.path("policyReviewRequired").asBoolean());
        assertEquals(1,AuthenticationTcContext.prepare(mapper,"제품",features(),mapper.createObjectNode()).size());
    }
    @Test void existingLoginIsNotDuplicatedAndLogoutIsNotLogin() {
        var rows=features();((ObjectNode)rows.get(0)).put("functionName","로그인");
        var context=mapper.createObjectNode().put("required",true);context.putArray("evidence");
        assertEquals(1,AuthenticationTcContext.prepare(mapper,"제품",rows,context).size());
        ((ObjectNode)rows.get(0)).put("functionName","로그아웃");
        assertEquals("주요기능",new MenuTcGenerator(mapper).classify(rows.get(0)));
    }
}
