package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuTcGeneratorTest {
    final ObjectMapper mapper=new ObjectMapper();
    final MenuTcGenerator generator=new MenuTcGenerator(mapper);
    final MenuTcWorkbook workbook=new MenuTcWorkbook(mapper);
    @Test void fallbackClassificationUsesFinalWordAfterLoginAndPreservesExplicitType() {
        for(String word:List.of("조회","생성","삭제","등록","수정")) {
            var feature=mapper.createObjectNode().put("functionName","  사용자 "+word+"  ");
            assertEquals("CRUD",generator.classify(feature));
            feature.put("featureType","부가기능");assertEquals("부가기능",generator.classify(feature));
            feature.put("featureType","주요기능");assertEquals("주요기능",generator.classify(feature));
        }
        for(String name:List.of("작업 실행","삭제 취소","사용자조회","등록 내역 확인",""))
            assertEquals("주요기능",generator.classify(mapper.createObjectNode().put("functionName",name)));
        for(String name:List.of("로그인"," LOGIN ","로그인 생성"))
            assertEquals("로그인",generator.classify(mapper.createObjectNode().put("functionName",name)));
        assertEquals("CRUD",generator.classify(mapper.createObjectNode().put("functionName","로그인 기록 조회")));
        assertEquals("CRUD",generator.classify(mapper.createObjectNode().put("functionName","삭제")));
        var rows=feature(null);((ObjectNode)rows.get(0)).put("functionName","사용자 삭제");
        assertEquals(84,generator.generate("제품",rows,mapper.createObjectNode()).path("caseCount").asInt());
    }
    ArrayNode feature(String type) {
        var rows=mapper.createArrayNode();var row=rows.addObject().put("featureId","feature-1").put("functionName","파일 업로드").put("description","파일을 업로드하는 기능");
        if(type!=null)row.put("featureType",type);row.putArray("featurePath").add("제품").add("업로드");row.putArray("evidence");return rows;
    }
    @Test void deterministicAndBlankVerdictsAndIndependentDefectExamples() {
        var source=mapper.createObjectNode().put("kind","TEST");
        var first=generator.generate("제품",feature("주요기능"),source);
        assertEquals(first,generator.generate("제품",feature("주요기능"),source));
        assertEquals(77,first.path("caseCount").asInt());
        assertEquals(95,generator.generate("제품",feature("로그인"),source).path("caseCount").asInt());
        Set<String> ids=new HashSet<>();for(var tc:first.path("testCases")) {
            assertTrue(ids.add(tc.path("id").asText()));assertTrue(tc.path("result").isNull());
            assertEquals("NEEDS_CONTEXT",tc.path("executionReadiness").asText());
            assertFalse(tc.path("defectExample").path("content").asText().isBlank());
            assertFalse(tc.path("inputs").asText().isBlank());
            assertFalse(tc.path("expectedResult").asText().isBlank());
            assertTrue(tc.path("executionPlan").path("steps").size()>=3);
        }
        var changed=generator.generate("제품",feature("주요기능"),mapper.createObjectNode().put("kind","OTHER"));
        assertNotEquals(first.path("testCases").get(0).path("id"),changed.path("testCases").get(0).path("id"));
    }
    @Test void invalidAndDuplicateFeaturesAreRejectedWithoutSilentLoss() {
        assertThrows(RuntimeException.class,()->generator.generate("제품",feature("오타"),mapper.createObjectNode()));
        var rows=feature("주요기능");rows.add(rows.get(0).deepCopy());
        assertThrows(RuntimeException.class,()->generator.generate("제품",rows,mapper.createObjectNode()));
        var tooDeep=feature("주요기능");((ArrayNode)tooDeep.get(0).path("featurePath")).add("3").add("4").add("5").add("6");
        assertThrows(RuntimeException.class,()->generator.generate("제품",tooDeep,mapper.createObjectNode()));
    }
    @Test void approvedMenuGateRejectsDraftPartialMockStaleAndIgnoresStructureOnlyRows() {
        for(String status:List.of("DRAFT","GENERATING","FAILED"))assertThrows(RuntimeException.class,()->MenuTcService.requireApproved(mapper.createObjectNode().put("status",status)));
        for(String flag:List.of("stale","mock","partial")) {
            var menu=mapper.createObjectNode().put("status","APPROVED").put("stale",flag.equals("stale")).put("generationMode",flag.equals("mock")?"MOCK":"REAL");
            menu.putObject("output").put("completionStatus",flag.equals("partial")?"PARTIAL":"COMPLETE");
            assertThrows(RuntimeException.class,()->MenuTcService.requireApproved(menu));
        }
        MenuTcService.requireApproved(mapper.createObjectNode().put("status","APPROVED").put("generationMode","REAL"));
    }
    @Test void fourProductSampleSheetsProduceIndependentTraceableTcWorkbooks() throws Exception {
        Path sample=Path.of("../sample/menu sample/menu sample.xlsx");assertTrue(Files.exists(sample),"User sample workbook required");
        byte[] bytes=Files.readAllBytes(sample);String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        List<ObjectNode> inputs=workbook.read(new ByteArrayInputStream(bytes),digest);assertEquals(4,inputs.size());
        int[] featureCounts={133,86,172,44},caseCounts={1283,842,1670,671};
        Path directory=Path.of("build/test-artifacts/menu-tc");Files.createDirectories(directory);
        var report=mapper.createObjectNode().put("sourceFile","sample/menu sample/menu sample.xlsx").put("sourceSha256",digest).put("templateVersion",MenuTcGenerator.VERSION);
        var products=report.putArray("products");Set<String> allIds=new HashSet<>();
        for(int p=0;p<inputs.size();p++) {
            var input=inputs.get(p);var output=generator.generate(input.path("productName").asText(),input.path("features"),input.path("source"));
            assertEquals(featureCounts[p],output.path("featureCount").asInt());assertEquals(caseCounts[p],output.path("caseCount").asInt());
            Set<String> sourceFeatures=new HashSet<>();for(JsonNode f:input.path("features"))sourceFeatures.add(f.path("featureId").asText());
            Set<String> covered=new HashSet<>();
            for(JsonNode tc:output.path("testCases")) {
                assertTrue(allIds.add(tc.path("id").asText()));assertTrue(tc.path("result").isNull());
                if(!tc.path("featureId").asText().startsWith("scope:"))covered.add(tc.path("featureId").asText());
                assertEquals(input.path("source").path("sheet"),tc.path("source").path("sheet"));
            }
            assertEquals(sourceFeatures,covered);
            byte[] excel=workbook.export(output);
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(excel))) {
                var sheet=book.getSheet("TestCase");assertEquals(caseCounts[p],sheet.getLastRowNum());
                assertEquals(16,sheet.getRow(0).getLastCellNum());
                for(int r=1;r<=sheet.getLastRowNum();r++)assertEquals("",sheet.getRow(r).getCell(12).getStringCellValue());
                assertArrayEquals(new String[]{"P","F","N/A"},sheet.getDataValidations().getFirst().getValidationConstraint().getExplicitListValues());
                assertEquals(caseCounts[p],book.getSheet("실행 정보").getLastRowNum());
                for(var sh:book)for(var row:sh)for(var cell:row)assertNotEquals(CellType.FORMULA,cell.getCellType());
            }
            String prefix="product-"+(p+1);Files.write(directory.resolve(prefix+".xlsx"),excel);
            Files.writeString(directory.resolve(prefix+".json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
            var product=products.addObject().put("sheet",input.path("source").path("sheet").asText()).put("features",featureCounts[p]).put("testCases",caseCounts[p]).put("coveredFeatures",covered.size()).put("allResultsBlank",true).put("workbook",prefix+".xlsx").put("json",prefix+".json");
            product.set("classificationCounts",output.path("summary"));product.set("skippedRows",input.path("skippedRows"));
        }
        Files.writeString(directory.resolve("verification.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        assertEquals(4466,allIds.size());
    }
    @Test void samePathDifferentFunctionsStaySeparateAndFormulaLikeTextIsLiteral() {
        var features=feature("CRUD");var other=features.get(0).deepCopy();((ObjectNode)other).put("featureId","other").put("description","=1+1");features.add(other);
        var output=generator.generate("제품",features,mapper.createObjectNode());assertEquals(100,output.path("caseCount").asInt());
        assertEquals(2,output.path("featureCount").asInt());
    }
    @Test void fullCrudRecipesRemainWhileSelectionAndProductBindingsAreSeparate() throws Exception {
        var rows=feature("CRUD");((ObjectNode)rows.get(0)).put("functionName","사용자 삭제").put("description","선택한 사용자를 삭제하는 기능");
        var output=generator.generate("제품",rows,mapper.createObjectNode());
        assertEquals(84,output.path("caseCount").asInt());
        var featureCases=new ArrayList<JsonNode>();for(var tc:output.path("testCases"))if(tc.path("featureId").asText().equals("feature-1"))featureCases.add(tc);
        assertEquals(16,featureCases.size());
        assertTrue(featureCases.stream().anyMatch(tc->tc.path("ruleId").asText().equals("CRUD-16")));
        var create=featureCases.stream().filter(tc->tc.path("ruleId").asText().equals("CRUD-06")).findFirst().orElseThrow();
        var delete=featureCases.stream().filter(tc->tc.path("ruleId").asText().equals("CRUD-09")).findFirst().orElseThrow();
        assertTrue(create.path("executionPlan").path("selection").path("exclusionCandidate").asBoolean());
        assertFalse(delete.path("executionPlan").path("selection").path("exclusionCandidate").asBoolean());
        assertEquals("PENDING_LLM",create.path("executionPlan").path("selection").path("status").asText());
        assertTrue(create.path("result").isNull());
        for(var tc:featureCases){assertTrue(tc.has("templateOriginal"));assertFalse(tc.path("defectExample").toString().contains("AAA"));assertTrue(tc.path("executionPlan").path("specification").path("checks").isArray());}
        assertEquals("",TcTemplateBinding.operation("사용자 추가/수정/삭제"));
        assertEquals("DELETE",TcTemplateBinding.operation("사용자 삭제"));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook.export(output)))) {
            assertEquals(16,book.getSheet("TestCase").getRow(0).getLastCellNum());
            assertEquals(20,book.getSheet("실행 정보").getRow(0).getLastCellNum());
            assertEquals("고유 TC ID",book.getSheet("실행 정보").getRow(0).getCell(11).getStringCellValue());
        }
    }
}
