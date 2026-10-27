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
}
