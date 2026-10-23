package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Read template data without evaluating workbook formulas or executing embedded content. */
@Component
public class MenuTcWorkbook {
    private final ObjectMapper mapper;
    public MenuTcWorkbook(ObjectMapper mapper) { this.mapper = mapper; }
    private String text(Row row, int column) {
        return row == null || row.getCell(column) == null ? "" : new DataFormatter(Locale.ROOT).formatCellValue(row.getCell(column)).strip().replaceAll("\\s+", " ");
    }
    public List<ObjectNode> read(InputStream stream, String fileDigest) throws IOException {
        List<ObjectNode> result = new ArrayList<>();
        try (var book = WorkbookFactory.create(stream)) {
            for (Sheet sheet : book) {
                Row header = sheet.getRow(0); if (header == null) continue;
                int description = -1, classification = -1; List<Integer> paths = new ArrayList<>();
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    String name = text(header, c).toLowerCase(Locale.ROOT).replace(" ", "");
                    if (name.matches("[1-5]depth") || name.equals("프로그램")) paths.add(c);
                    if (name.equals("메뉴설명")) description = c;
                    if (name.equals("기능구분")) classification = c;
                }
                if (description < 0 || paths.isEmpty()) continue;
                if (paths.size() > 5) throw MenuTcGenerator.invalid("TC_FEATURE_PATH_INVALID", "최대 5단계 경로만 지원합니다: " + sheet.getSheetName());
                var input = mapper.createObjectNode().put("productName", sheet.getSheetName());
                var source = input.putObject("source").put("kind", "EXCEL_SAMPLE").put("sheet", sheet.getSheetName()).put("fileDigest", fileDigest);
                var features = input.putArray("features"); var skipped = input.putArray("skippedRows");
                var current = new ArrayList<String>(Collections.nCopies(paths.size(), ""));
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r); String desc = text(row, description);
                    for (int p = 0; p < paths.size(); p++) {
                        String value = text(row, paths.get(p));
                        if (!value.isBlank()) {
                            String normalized = value.equals("-") ? "" : value;
                            if (!normalized.equals(current.get(p))) {
                                current.set(p, normalized);
                                for (int child = p + 1; child < paths.size(); child++) current.set(child, "");
                            }
                        }
                    }
                    String featureType = classification >= 0 ? text(row, classification) : "";
                    if ((desc.isBlank() && featureType.isBlank()) || desc.matches("[.\\-·]+")) {
                        if (row != null) skipped.addObject().put("row", r + 1).put("reason", "빈 행·기능구분 없는 구조 행·구분 행");
                        continue;
                    }
                    List<String> path = current.stream().filter(v -> !v.isBlank()).toList();
                    if (path.isEmpty()) throw MenuTcGenerator.invalid("TC_FEATURE_PATH_INVALID", sheet.getSheetName() + " " + (r + 1) + "행에 기능 경로가 없습니다.");
                    var feature = features.addObject().put("featureId", "excel:" + MenuTcGenerator.hash(fileDigest + ":" + sheet.getSheetName() + ":" + (r + 1)))
                        .put("functionName", path.getLast()).put("description", desc.isBlank() ? path.getLast() + " (기능 설명 미기재)" : desc).put("sourceRow", r + 1).put("descriptionMissing", desc.isBlank());
                    feature.set("featurePath", mapper.valueToTree(path));
                    if (!featureType.isBlank()) feature.put("featureType", featureType);
                    feature.putArray("evidence").addObject().put("kind", "EXCEL_MENU").put("sheet", sheet.getSheetName()).put("row", r + 1).put("description", desc);
                }
                if (!features.isEmpty()) result.add(input);
                source.put("featureCount", features.size());
            }
        }
        if (result.isEmpty()) throw MenuTcGenerator.invalid("TC_MENU_SHEETS_REQUIRED", "메뉴 설명과 경로가 있는 메뉴트리 시트가 없습니다.");
        return result;
    }
    public byte[] export(JsonNode output) {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("TestCase");
            String[] headers = {"1depth", "2depth", "3depth", "4depth", "5depth", "TC-ID", "기능구분", "품질특성", "하위특성", "테스트 시나리오", "입력 (사전조건 포함)", "기대 출력 (사후조건 포함)", "결과", "결함요약 (예시)", "결함정도 (예시)", "결함내용 (예시)"};
            var font = book.createFont(); font.setFontName("맑은 고딕"); font.setFontHeightInPoints((short)10);
            var style = book.createCellStyle(); style.setFont(font); style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.TOP);
            var heading = book.createCellStyle(); heading.cloneStyleFrom(style); heading.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row top = sheet.createRow(0); top.setHeightInPoints(32); for (int i = 0; i < headers.length; i++) { cell(top, i, headers[i], heading); sheet.setColumnWidth(i, (i < 5 ? 21 : i >= 9 ? 55 : 22) * 256); }
            Sheet execution = book.createSheet("실행 정보");
            String[] detailHeaders = {"TC-ID", "기능 ID", "규칙 ID", "기능 설명", "전체 기능 경로", "시험 절차", "실행 준비", "필요 정보", "적용 조건·N/A 기준", "대상 URL", "근거", "고유 TC ID", "TC 내용 버전", "템플릿 버전", "자동화 방식", "실행 선택 상태", "선택 검토 사유", "구조화 실행 명세", "대기·재시도 정책", "정리·복구"};
            for (int i = 0; i < detailHeaders.length; i++) { cell(execution.getRow(0) == null ? execution.createRow(0) : execution.getRow(0), i, detailHeaders[i], heading); execution.setColumnWidth(i, 45 * 256); }
            int index = 1;
            for (JsonNode tc : output.path("testCases")) {
                Row row = sheet.createRow(index); row.setHeightInPoints(88);
                for (int i = 0; i < 5; i++) cell(row, i, tc.path("featurePath").path(i).asText("-"), style);
                String[] values = {tc.path("tcId").asText(), tc.path("featureType").asText(), tc.path("qualityCharacteristic").asText(), tc.path("subCharacteristic").asText(), tc.path("scenario").asText(), tc.path("inputs").asText(), tc.path("expectedResult").asText(), "", tc.path("defectExample").path("summary").asText(), tc.path("defectExample").path("severity").asText(), tc.path("defectExample").path("content").asText()};
                for (int i = 0; i < values.length; i++) cell(row, i + 5, values[i], style);
                row.getCell(12).setBlank();
                Row detail = execution.createRow(index++); detail.setHeightInPoints(90);
                JsonNode plan = tc.path("executionPlan");
                String[] details = {tc.path("tcId").asText(), tc.path("featureId").asText(), tc.path("ruleId").asText(), tc.path("scopeDescription").asText(), join(tc.path("featurePath"), " → "), join(plan.path("steps"), "\n"), plan.path("readinessReason").asText("환경·절차 구체화 필요"), join(plan.path("requiredContext"), "\n"), plan.path("applicability").asText() + "\n" + plan.path("naPolicy").asText(), plan.path("targetUrl").asText(), tc.path("evidence").toString(), tc.path("id").asText(), plan.path("tcContentDigest").asText(), tc.path("templateVersion").asText(), tc.path("automationMode").asText(), plan.path("selection").path("status").asText("PENDING_LLM"), plan.path("selection").path("reason").asText(), plan.path("specification").toString(), plan.path("specification").path("retryPolicy").asText(), plan.path("specification").path("cleanup").asText()};
                for (int i = 0; i < details.length; i++) cell(detail, i, details[i], style);
                fitHeight(sheet, row); fitHeight(execution, detail);
            }
            sheet.createFreezePane(6, 1); execution.createFreezePane(1, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, index - 1), 0, 15));
            var validation = sheet.getDataValidationHelper();
            var constraint = validation.createExplicitListConstraint(new String[]{"P", "F", "N/A"});
            var rule = validation.createValidation(constraint, new CellRangeAddressList(1, Math.max(1, index - 1), 12, 12));
            rule.setEmptyCellAllowed(true); rule.setShowErrorBox(true); sheet.addValidationData(rule);
            var metadata = book.createSheet("생성 정보");
            List<String> notes = List.of("생성 결과는 미실행 초안입니다. 결과 P/F/N/A는 실행 후 판정합니다.", "결함 열은 실패 시 작성 예시이며 실제 발견 결함이 아닙니다.", "로그인 27개·주요/부가 9개·CRUD 16개 템플릿 전체를 생성합니다. 실행 제외는 원본 TC 삭제나 N/A 확정이 아닙니다.", "구체적인 입력·선택자·계정·기대값은 이 메뉴트리만으로 확정할 수 없습니다.", "Excel 편집은 서버에 자동 반영되지 않습니다.", "CRUD 16번째 항목은 삭제 취소 시 원본 유지 검증입니다. 공통·문서 68개는 제품당 한 번 생성합니다.", "PENDING_LLM은 실제 적용 여부 판단 전 상태입니다. 정보 부족은 준비 필요이며 N/A와 다릅니다.", output.path("templateVersion").asText(), output.path("templateDigest").asText(), output.path("source").toString(), output.path("summary").toString());
            metadata.setColumnWidth(0, 110 * 256); for (int i = 0; i < notes.size(); i++) { var r = metadata.createRow(i); r.setHeightInPoints(40); cell(r, 0, notes.get(i), style); }
            book.write(bytes); return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("TC Excel export failed", failure); }
    }
    private String join(JsonNode array, String separator) { List<String> values = new ArrayList<>(); array.forEach(v -> values.add(v.asText())); return String.join(separator, values); }
    private void cell(Row row, int column, String value, CellStyle style) { var cell = row.createCell(column, CellType.STRING); cell.setCellValue(value); cell.setCellStyle(style); }
    private void fitHeight(Sheet sheet, Row row) {
        int lines = 1;
        for (Cell cell : row) {
            int count = 0, width = Math.max(8, sheet.getColumnWidth(cell.getColumnIndex()) / 256 - 2);
            for (String line : cell.getStringCellValue().split("\n", -1)) {
                int units = line.codePoints().map(point -> point > 255 ? 2 : 1).sum();
                count += Math.max(1, (units + width - 1) / width);
            }
            lines = Math.max(lines, count);
        }
        row.setHeightInPoints(Math.min(409, Math.max(38, lines * 14 + 10)));
    }
}
