package com.autotest.test_management_service.application.testcase;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SelfLlmMenuContractTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void requestBoundReferencesRestoreExactEvidenceAndValidateThenExport() throws Exception {
        var contract=new SelfLlmMenuContract(mapper);UUID job=UUID.randomUUID();
        var snapshot=mapper.createObjectNode().put("productName","제품");
        var evidence=snapshot.putArray("evidence");evidence.addObject().put("id","doc").put("kind","DOCUMENT").put("text","사용자 목록을 조회하는 기능");
        var screen=evidence.addObject().put("id","menu").put("kind","MENU").put("menuKey","users").put("text","사용자 목록").put("location","SIDEBAR");screen.putArray("webPath").add("관리");
        var web=snapshot.putObject("webStructure").put("version","web-first-v1");web.putArray("nodes").addObject().putArray("path").add("관리");
        var description=contract.describe(job,snapshot);assertEquals("self-llm-v3",description.path("instructionVersion").asText());
        assertTrue(description.path("prompt").asText().contains("evidenceCatalog"));assertEquals(description,contract.describe(job,snapshot));
        String prompt=description.path("prompt").asText();
        assertTrue(prompt.contains("등록된 메뉴트리 공통 지침 self-llm-v3"));
        assertFalse(prompt.contains("# 메뉴트리 공통 작성 지침"));
        assertFalse(prompt.contains("# 출력 JSON 스키마"));
        var payload=mapper.readTree(prompt.substring(prompt.indexOf('{')));
        assertEquals(description.path("requestId"),payload.path("requestId"));
        assertEquals(description.path("evidenceCatalog"),payload.path("evidenceCatalog"));
        assertEquals(snapshot.path("webStructure"),payload.path("webStructure"));
        var rows=mapper.createArrayNode();var row=rows.addObject().put("functionName","사용자 조회").put("menuDescription","사용자 목록을 조회하는 기능").put("location","SIDEBAR");
        row.putArray("menuPath").add("관리");row.putArray("evidenceRefIds").add("E0001").add("E0002");row.putArray("reviewNotes");
        var restored=contract.restore(job,snapshot,description.path("requestId").asText(),rows);
        assertEquals("사용자 목록을 조회하는 기능",restored.get(0).path("evidence").get(0).path("quote").asText());
        assertEquals("users",restored.get(0).path("matchedMenuKeys").get(0).asText());
        var validated=new FunctionalMenuValidator(mapper).validate(restored,snapshot,null);
        assertEquals("FUNCTION",validated.get(0).path("rowType").asText());
        var output=mapper.createObjectNode();output.set("rows",validated);
        byte[] excel=new FunctionalMenuExcel().export("제품",output,snapshot,true);
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel))){assertEquals("메뉴트리",book.getSheetAt(0).getSheetName());assertEquals(6,book.getSheetAt(0).getRow(0).getLastCellNum());}
        assertThrows(ProductDescriptionException.class,()->contract.restore(UUID.randomUUID(),snapshot,description.path("requestId").asText(),rows));
        rows.add(row.deepCopy());
        assertThrows(ProductDescriptionException.class,()->contract.restore(job,snapshot,description.path("requestId").asText(),rows));
        rows.remove(1);
        var coverage=contract.coverage(snapshot,restored);
        assertFalse(coverage.path("semanticCoverageVerified").asBoolean());
        assertEquals(1,coverage.path("referencedDocumentSourceCount").asInt());
        row.withArray("evidenceRefIds").add("E9999");
        assertThrows(ProductDescriptionException.class,()->contract.restore(job,snapshot,description.path("requestId").asText(),rows));
    }
}
