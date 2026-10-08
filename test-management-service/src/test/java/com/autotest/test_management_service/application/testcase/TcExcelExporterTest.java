package com.autotest.test_management_service.application.testcase;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;

class TcExcelExporterTest {
    private final TcExcelExporter exporter = new TcExcelExporter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void preservesDraftColumnsBlankResultAndEvidenceWithoutExecutingFormulas() throws Exception {
        var job = mapper.readTree("""
            {"jobId":"job","status":"COMPLETED","approvedAt":"2026-10-08T00:00:00Z","output":{"generationMode":"REAL","testCases":[
            {"tcId":"TC-001-001","featurePath":["보고서","내보내기"],"qualityCharacteristic":"기능적합성","subCharacteristic":"기능정확성",
             "scenario":"=HYPERLINK(\\\"https://invalid\\\",\\\"text\\\")","preconditions":["로그인"],"inputs":"CSV","expectedResult":"CSV 다운로드",
             "result":"P","steps":["보고서 열기","내보내기 클릭"],"executionMode":"BROWSER_CANDIDATE","reviewNotes":["초안 검토"],
             "defectExampleSummary":"다운로드 오류","defectExampleSeverity":"M","defectExampleContent":"예시 내용",
             "sources":[{"role":"MANUAL","fileId":"doc","characterStart":0,"characterEnd":8,"quote":"원문 근거 내용"}]}]}}
            """);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(job)))) {
            assertEquals(3, book.getNumberOfSheets());
            var tc = book.getSheet("TC");
            assertEquals(15, tc.getRow(0).getLastCellNum());
            assertEquals("보고서", tc.getRow(1).getCell(0).getStringCellValue());
            assertEquals("TC-001-001", tc.getRow(1).getCell(5).getStringCellValue());
            assertEquals(CellType.BLANK, tc.getRow(1).getCell(11).getCellType());
            assertEquals(CellType.STRING, tc.getRow(1).getCell(8).getCellType());
            assertTrue(tc.getRow(1).getCell(8).getStringCellValue().startsWith("=HYPERLINK"));
            assertArrayEquals(new String[]{"P", "F", "N/A"}, tc.getDataValidations().get(0).getValidationConstraint().getExplicitListValues());
            assertNotNull(tc.getPaneInformation());
            assertTrue(book.getSheet("절차 및 근거").getRow(1).getCell(4).getStringCellValue().contains("원문 근거 내용"));
            assertTrue(book.getSheet("안내").getRow(1).getCell(0).getStringCellValue().contains("실제 발견된 결함이 아닙니다"));
        }
    }

    @Test void refusesUnfinishedJobAndOversizedCells() throws Exception {
        var job = mapper.readTree("{\"status\":\"GENERATING\"}");
        assertEquals("TC_NOT_COMPLETED", assertThrows(ProductDescriptionException.class, () -> exporter.export(job)).code());
        var huge = mapper.createObjectNode(); huge.put("status", "COMPLETED");
        huge.putObject("output").putArray("testCases").addObject().put("scenario", "x".repeat(32768));
        assertEquals("TC_EXPORT_CELL_TOO_LONG", assertThrows(ProductDescriptionException.class, () -> exporter.export(huge)).code());
    }
}
