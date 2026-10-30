package com.autotest.test_management_service.application.testcase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExecutionFeatureClassificationTest {
    @Test void loginHistoryIsNotAuthenticationButUserOverrideIsRespected(){
        var mapper=new ObjectMapper();var generator=new MenuTcGenerator(mapper);
        // History is not authentication; the existing 조회 suffix rule classifies it as CRUD.
        assertEquals("CRUD",generator.classify(mapper.createObjectNode().put("functionName","로그인 기록 조회")));
        assertEquals("로그인",generator.classify(mapper.createObjectNode().put("functionName","로그인/로그아웃")));
        assertEquals("로그인",generator.classify(mapper.createObjectNode().put("functionName","로그인 기록 조회").put("featureType","로그인")));
    }
    @Test void genericReadTemplateNeedsAnIndependentBaselineButScreenCheckDoesNot(){
        var mapper=new ObjectMapper();
        var generic=mapper.createObjectNode().put("ruleId","CRUD-01")
            .put("inputs","조회 조건과 비교할 기대 데이터를 준비한다.")
            .put("expectedResult","표시 결과가 기준 시점의 기대 데이터에 일치한다.");
        generic.putObject("executionPlan").putObject("selection").put("scopeOperation","READ");
        assertTrue(TestExecutionService.needsReadBaseline(generic));
        generic.put("ruleId","CRUD-VIEW-01");
        assertFalse(TestExecutionService.needsReadBaseline(generic));
        generic.put("ruleId","CRUD-01").put("inputs","대상 ID 42, 기대 상태 ACTIVE").put("expectedResult","상태 ACTIVE가 표시된다.");
        assertFalse(TestExecutionService.needsReadBaseline(generic));
    }
}
