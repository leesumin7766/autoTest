package com.autotest.test_management_service.application.testcase;

import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class FunctionalMenuExcel {
    private static class Group { final Map<String,Group> children=new LinkedHashMap<>(); final List<JsonNode> rows=new ArrayList<>(); }
    private void flatten(Group group,List<JsonNode> rows) { rows.addAll(group.rows);group.children.values().forEach(child->flatten(child,rows)); }
    public byte[] export(String product,JsonNode output,JsonNode snapshot,boolean approved) {
        try(var book=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
            var sheet=book.createSheet(approved?"메뉴트리":"메뉴트리(초안)");
            var font=book.createFont();font.setFontName("맑은 고딕");font.setFontHeightInPoints((short)10);
            var body=book.createCellStyle();body.setFont(font);body.setWrapText(true);body.setVerticalAlignment(VerticalAlignment.CENTER);
            body.setBorderTop(BorderStyle.THIN);body.setBorderBottom(BorderStyle.THIN);body.setBorderLeft(BorderStyle.THIN);body.setBorderRight(BorderStyle.THIN);
            var centered=book.createCellStyle();centered.cloneStyleFrom(body);centered.setAlignment(HorizontalAlignment.CENTER);
            var header=book.createCellStyle();header.cloneStyleFrom(centered);header.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var bold=book.createFont();bold.setFontName("맑은 고딕");bold.setBold(true);header.setFont(bold);
            var top=sheet.createRow(0);top.setHeightInPoints(26);
            String[] titles={"1depth","2depth","3depth","4depth","5depth","메뉴 설명"};
            int[] widths={18,23,23,26,30,82};
            for(int i=0;i<6;i++){cell(top,i,titles[i],header);sheet.setColumnWidth(i,widths[i]*256);}
            var root=new Group();
            for(JsonNode row:output.path("rows")) if(row.path("included").asBoolean(true)) {
                Group current=root;for(JsonNode part:row.path("menuPath")) current=current.children.computeIfAbsent(part.asText(),key->new Group());current.rows.add(row);
            }
            List<JsonNode> ordered=new ArrayList<>();flatten(root,ordered);
            var evidence=book.createSheet("근거·검토");
            String[] evidenceHeaders={"메뉴트리 행","기능 ID","위치","확인 상태","입력 근거","인용문","검토 메모"};
            var evidenceTop=evidence.createRow(0);for(int i=0;i<evidenceHeaders.length;i++){cell(evidenceTop,i,evidenceHeaders[i],header);evidence.setColumnWidth(i,(i<4?20:70)*256);}
            Map<String,JsonNode> sources=new HashMap<>();for(JsonNode source:snapshot.path("evidence")) sources.put(source.path("id").asText(),source);
            List<List<String>> paths=new ArrayList<>();int rowIndex=1, evidenceIndex=1;
            for(JsonNode item:ordered) {
                var row=sheet.createRow(rowIndex);var values=new ArrayList<String>();values.add(product);
                for(int c=0;c<3;c++) values.add(item.path("menuPath").path(c).asText("-"));values.add(item.path("functionName").asText());paths.add(values);
                for(int c=0;c<5;c++) cell(row,c,values.get(c),centered);
                String description=item.path("menuDescription").asText();cell(row,5,description,body);
                int height=lines(description,widths[5]);for(int c=0;c<5;c++)height=Math.max(height,lines(values.get(c),widths[c]));row.setHeightInPoints(Math.min(409,Math.max(28,height*16+8)));
                for(JsonNode ref:item.path("evidence")) {
                    JsonNode source=sources.get(ref.path("sourceId").asText());var er=evidence.createRow(evidenceIndex++);er.setHeightInPoints(60);
                    String provenance=source==null?ref.path("sourceId").asText():source.path("kind").asText()+" · "+source.path("filename").asText(source.path("pageIndex").asText())+" · "+source.path("id").asText();
                    String[] v={Integer.toString(rowIndex+1),item.path("rowId").asText(),item.path("location").asText(),item.path("verificationStatus").asText(),provenance,ref.path("quote").asText(),item.path("reviewNotes").toString()};
                    int evidenceHeight=1;for(int c=0;c<v.length;c++){cell(er,c,v[c],body);evidenceHeight=Math.max(evidenceHeight,lines(v[c],c<4?20:70));}er.setHeightInPoints(Math.min(409,evidenceHeight*16+8));
                }
                rowIndex++;
            }
            // Merge only adjacent cells whose complete ancestor prefixes are equal. '-' is never merged.
            for(int c=0;c<4;c++) { int start=0;while(start<paths.size()){int end=start+1;while(end<paths.size() && !"-".equals(paths.get(start).get(c)) && paths.get(start).subList(0,c+1).equals(paths.get(end).subList(0,c+1)))end++;
                if(end-start>1)sheet.addMergedRegion(new CellRangeAddress(start+1,end,c,c));start=end;} }
            sheet.createFreezePane(0,1);evidence.createFreezePane(0,1);sheet.setAutobreaks(true);
            sheet.getPrintSetup().setLandscape(true);sheet.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);
            sheet.getFooter().setLeft(approved?"사용자 승인 메뉴트리":"검토 전 초안 · "+output.path("generationMode").asText());sheet.getFooter().setRight("&P / &N");
            book.getProperties().getCoreProperties().setTitle(product+" 메뉴트리");
            book.write(bytes);return bytes.toByteArray();
        } catch(IOException error){throw new IllegalStateException("MENU_EXPORT_FAILED",error);}
    }
    private void cell(Row row,int index,String value,CellStyle style){var cell=row.createCell(index,CellType.STRING);cell.setCellValue(value);cell.setCellStyle(style);}
    private int lines(String value,int width){int count=0;for(String line:value.split("\n",-1)){int units=line.codePoints().map(point->point>255?2:1).sum();count+=Math.max(1,(units+width-3)/(width-2));}return count;}
}
