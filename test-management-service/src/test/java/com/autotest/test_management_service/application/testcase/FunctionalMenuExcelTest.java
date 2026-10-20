package com.autotest.test_management_service.application.testcase;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import com.fasterxml.jackson.databind.ObjectMapper;

class FunctionalMenuExcelTest {
    @Test void mergesOnlyCompleteAncestorPathsAndKeepsDescriptionsAndTextCells() throws Exception {
        var mapper=new ObjectMapper();var output=mapper.createObjectNode().put("generationMode","REAL");var rows=output.putArray("rows");
        for(String[] value:new String[][]{{"A","같은 이름","조회"},{"A","같은 이름","삭제"},{"B","같은 이름","=1+1"}}){
            var row=rows.addObject().put("rowId",value[0]+value[2]).put("included",true).put("functionName",value[2]).put("menuDescription",value[2]+"하는 기능").put("location","CONTENT");
            row.putArray("menuPath").add(value[0]).add(value[1]);row.putArray("evidence");row.putArray("reviewNotes");
        }
        byte[] bytes=new FunctionalMenuExcel().export("제품",output,mapper.createObjectNode().set("evidence",mapper.createArrayNode()),true);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            var sheet=book.getSheetAt(0);assertEquals("메뉴트리",sheet.getSheetName());assertEquals(6,sheet.getRow(0).getLastCellNum());
            assertEquals(CellType.STRING,sheet.getRow(3).getCell(4).getCellType());assertEquals("=1+1",sheet.getRow(3).getCell(4).getStringCellValue());
            assertTrue(sheet.getMergedRegions().stream().anyMatch(range->range.getFirstColumn()==2&&range.getFirstRow()==1&&range.getLastRow()==2));
            assertFalse(sheet.getMergedRegions().stream().anyMatch(range->range.getFirstColumn()==2&&range.getLastRow()==3));
            assertEquals("-",sheet.getRow(1).getCell(3).getStringCellValue());
        }
        var path=java.nio.file.Path.of("build/test-artifacts/functional-menu-example.xlsx");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,bytes);
    }
}
