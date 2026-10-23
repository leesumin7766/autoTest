package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TcProcedureWriterTest {
    final ObjectMapper mapper=new ObjectMapper();
    JsonNode generate(String name,String type) {
        var features=mapper.createArrayNode();var f=features.addObject().put("featureId","test").put("functionName",name).put("featureType",type).put("description",name+" 기능");
        f.putArray("featurePath").add("제품").add("관리").add(name);f.putArray("evidence");
        return new MenuTcGenerator(mapper).generate("제품",features,mapper.createObjectNode());
    }
    JsonNode rule(JsonNode output,String id) {for(var tc:output.path("testCases"))if(id.equals(tc.path("ruleId").asText()))return tc;throw new AssertionError(id);}
    @Test void crudPreservesAllTemplatesAndMakesIndependentMutationChecks() {
        var output=generate("전역 속성 수정","CRUD");assertEquals(84,output.path("caseCount").asInt());
        var update=rule(output,"CRUD-07");
        assertTrue(update.path("inputs").asText().contains("새로고침"));
        assertTrue(update.path("expectedResult").asText().contains("대상 외"));
        var cancel=rule(output,"CRUD-16");assertTrue(cancel.path("inputs").asText().contains("취소한 뒤"));assertTrue(cancel.path("expectedResult").asText().contains("기존 값"));
        var missing=rule(output,"CRUD-13");assertTrue(missing.path("inputs").asText().contains("하나를 비운다"));assertTrue(missing.path("expectedResult").asText().contains("생성되지 않거나"));
        // Read-only feature still retains CREATE/UPDATE templates, marked for selection review.
        var read=generate("사용자 조회","CRUD");assertTrue(rule(read,"CRUD-06").path("executionPlan").path("selection").path("exclusionCandidate").asBoolean());
    }
    @Test void noInventedBindingsOrPrematureVerdictsAndStepsMatchExportedProcedure() {
        for(var output:List.of(generate("사용자 조회","CRUD"),generate("로그인","로그인"),generate("작업 실행","주요기능")))for(var tc:output.path("testCases")) {
            assertTrue(tc.path("inputs").asText().contains("\n1. "));
            assertTrue(tc.path("expectedResult").asText().startsWith("1. "));
            assertTrue(tc.path("result").isNull());
            var plan=tc.path("executionPlan");assertEquals("NEEDS_CONTEXT",plan.path("specification").path("status").asText());
            assertEquals(plan.path("steps").size(),plan.path("specification").path("steps").size());
            for(var step:plan.path("specification").path("steps")){assertEquals("UNRESOLVED",step.path("status").asText());assertFalse(step.has("selector"));}
        }
    }
    @Test void loginLockRequiresEachSubmissionAndDoesNotBorrowExamplePolicy() {
        var output=generate("로그인","로그인");var lock=rule(output,"LOGIN-17");
        assertTrue(lock.path("inputs").asText().contains("비밀번호 입력 → 로그인 제출"));
        assertFalse(lock.path("inputs").asText().contains("5회"));
        assertTrue(lock.path("inputs").asText().contains("정책 미확정이면"));
        assertTrue(rule(output,"LOGIN-04").path("expectedResult").asText().contains("해당 역할"));
        assertTrue(rule(output,"LOGIN-04").path("executionPlan").path("steps").get(0).asText().startsWith("시험 접속 주소"));
        assertEquals(TcProcedureWriter.VERSION,output.path("procedureVersion").asText());
    }
}
