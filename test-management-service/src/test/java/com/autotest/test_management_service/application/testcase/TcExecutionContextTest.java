package com.autotest.test_management_service.application.testcase;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcExecutionContextTest {
    final ObjectMapper mapper=new ObjectMapper();
    static final String DATA="필드별 유효값·필수값·제약과 저장 전후 기대 데이터", ROLE="저장된 접속 설정과 시험 계정의 역할·권한";

    ObjectNode tc(){
        var tc=mapper.createObjectNode().put("id","a");
        tc.putObject("executionPlan").putArray("requiredContext").add(ROLE).add(DATA);
        return tc;
    }
    ObjectNode request(int revision,String data,String role,String note){
        var r=mapper.createObjectNode().put("revision",revision);var items=r.putArray("items");
        if(data!=null)items.addObject().put("need",DATA).put("value",data);
        if(role!=null)items.addObject().put("need",ROLE).put("value",role);
        if(note!=null)r.put("note",note);
        return r;
    }

    @Test void statusFollowsHowManyNeedsAreAnswered(){
        var tc=tc();
        assertEquals(TcExecutionContext.MISSING,TcExecutionContext.status(tc));
        TcExecutionContext.apply(tc,request(0,"아이디 qa01, 이름 시험",null,null),7);
        assertEquals(TcExecutionContext.PARTIAL,TcExecutionContext.status(tc));
        TcExecutionContext.apply(tc,request(1,"아이디 qa01, 이름 시험","일반 사용자 계정",null),7);
        assertEquals(TcExecutionContext.COMPLETE,TcExecutionContext.status(tc));
        assertEquals(2,tc.path("executionContext").path("revision").asInt());
        assertEquals(TcExecutionContext.NOT_NEEDED,TcExecutionContext.status(mapper.createObjectNode().put("id","x")));
    }

    @Test void rejectsUnknownNeedsOldRevisionSecretsAndLongText(){
        var tc=tc();
        var unknown=mapper.createObjectNode().put("revision",0);unknown.putArray("items").addObject().put("need","다른 항목").put("value","x");
        assertThrows(ProductDescriptionException.class,()->TcExecutionContext.apply(tc,unknown,1));
        assertThrows(ProductDescriptionException.class,()->TcExecutionContext.apply(tc,request(3,"x",null,null),1));
        assertThrows(ProductDescriptionException.class,()->TcExecutionContext.apply(tc,request(0,"비밀번호: Abc123!",null,null),1));
        assertThrows(ProductDescriptionException.class,()->TcExecutionContext.apply(tc,request(0,null,null,"token=abcd1234"),1));
        assertThrows(ProductDescriptionException.class,()->TcExecutionContext.apply(tc,request(0,"x".repeat(1001),null,null),1));
        assertFalse(tc.has("executionContext"),"실패한 요청은 저장하지 않는다");
        TcExecutionContext.apply(tc,request(0,"비밀번호 변경 화면에서 확인",null,null),1);
        assertEquals(TcExecutionContext.PARTIAL,TcExecutionContext.status(tc),"'비밀번호'라는 단어 자체는 허용");
    }

    @Test void readSpecificationTcsUseTheirOwnBindingEditor(){
        var tc=tc();((ObjectNode)tc.path("executionPlan")).put("specificationVersion",TcExecutionSpecification.VERSION);
        assertEquals(TcExecutionContext.READ_BINDING,TcExecutionContext.status(tc));
        assertThrows(ProductDescriptionException.class,()->TcExecutionContext.apply(tc,request(0,"x",null,null),1));
    }

    @Test void userContextSendsOnlyFilledValuesAndNote(){
        var tc=tc();
        assertNull(TcExecutionContext.userContext(tc,mapper));
        TcExecutionContext.apply(tc,request(0,"아이디 qa01",null,"관리자 메뉴는 보이지 않아야 함"),1);
        var sent=TcExecutionContext.userContext(tc,mapper);
        assertEquals("TESTER_SUPPLIED",sent.path("source").asText());
        assertEquals(1,sent.path("items").size());assertEquals(DATA,sent.path("items").get(0).path("need").asText());
        assertEquals("관리자 메뉴는 보이지 않아야 함",sent.path("note").asText());
        assertFalse(sent.has("updatedBy"),"작성자·시각은 실행기로 보내지 않는다");
    }
}
