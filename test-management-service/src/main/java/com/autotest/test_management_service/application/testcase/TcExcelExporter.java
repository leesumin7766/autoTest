package com.autotest.test_management_service.application.testcase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.JsonNode;

/** Exports stored drafts only; never re-runs the LLM or turns examples into reported defects. */
@Component
public class TcExcelExporter {
    private static final String[] HEADERS = {"1depth", "2depth", "3depth", "4depth", "5depth", "TC-ID",
            "품질특성", "하위 특성", "테스트 시나리오", "입력 (사전조건 포함)", "기대 출력 (사후조건 포함)",
            "결과", "결함요약 (작성 예시)", "결함정도 (작성 예시)", "결함내용 (작성 예시)"};
    private static final int[] WIDTHS = {16, 18, 18, 18, 18, 18, 18, 18, 48, 48, 56, 10, 36, 14, 56};

    public byte[] export(JsonNode job) {
        if (!"COMPLETED".equals(job.path("status").asText()) || !job.path("output").path("testCases").isArray()
                || job.path("output").path("testCases").isEmpty()) {
            throw new ProductDescriptionException(HttpStatus.CONFLICT, "TC_NOT_COMPLETED", "생성 완료된 TC만 다운로드할 수 있습니다.");
        }
        JsonNode cases = job.path("output").path("testCases");
        if (cases.size() > 1_048_575) throw new ProductDescriptionException(HttpStatus.UNPROCESSABLE_ENTITY,
                "TC_EXPORT_TOO_LARGE", "Excel 한 시트의 최대 행 수를 초과했습니다.");
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var header = book.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);
            var font = book.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);
            var body = book.createCellStyle(); body.setWrapText(true); body.setVerticalAlignment(VerticalAlignment.TOP);
            body.setBorderBottom(BorderStyle.THIN); body.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            var resultStyle = book.createCellStyle(); resultStyle.cloneStyleFrom(body);
            resultStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            resultStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            var sheet = book.createSheet("TC");
            var titles = sheet.createRow(0); titles.setHeightInPoints(38);
            for (int c = 0; c < HEADERS.length; c++) {
                text(titles, c, HEADERS[c], header); sheet.setColumnWidth(c, WIDTHS[c] * 256);
            }
            var details = book.createSheet("절차 및 근거");
            String[] detailHeaders = {"TC-ID", "실행 구분", "시험 절차", "검토 메모", "문서 근거"};
            var detailTitle = details.createRow(0); detailTitle.setHeightInPoints(30);
            for (int c = 0; c < detailHeaders.length; c++) {
                text(detailTitle, c, detailHeaders[c], header); details.setColumnWidth(c, (c == 0 ? 18 : c == 1 ? 28 : 70) * 256);
            }
            int index = 1;
            for (var tc : cases) {
                var row = sheet.createRow(index); row.setHeightInPoints(100);
                for (int c = 0; c < 5; c++) text(row, c, tc.path("featurePath").path(c).asText(""), body);
                text(row, 5, tc.path("tcId").asText(), body);
                text(row, 6, tc.path("qualityCharacteristic").asText(), body);
                text(row, 7, tc.path("subCharacteristic").asText(), body);
                text(row, 8, tc.path("scenario").asText(), body);
                text(row, 9, "[사전조건]\n" + lines(tc.path("preconditions"), false) + "\n[입력]\n" + tc.path("inputs").asText(), body);
                text(row, 10, tc.path("expectedResult").asText(), body);
                // Leave genuinely blank. No P/F/N/A verdict is inferred by exporting a draft.
                row.createCell(11, CellType.BLANK).setCellStyle(resultStyle);
                text(row, 12, tc.path("defectExampleSummary").asText(), body);
                text(row, 13, tc.path("defectExampleSeverity").asText(), body);
                text(row, 14, tc.path("defectExampleContent").asText(), body);
                var detail = details.createRow(index++); detail.setHeightInPoints(130);
                text(detail, 0, tc.path("tcId").asText(), body);
                text(detail, 1, "MANUAL".equals(tc.path("executionMode").asText()) ? "수동 시험" : "브라우저 실행 가능 여부 검토 필요", body);
                text(detail, 2, lines(tc.path("steps"), true), body);
                text(detail, 3, lines(tc.path("reviewNotes"), false), body);
                List<String> sources = new ArrayList<>();
                for (var source : tc.path("sources")) sources.add(source.path("role").asText() + " / "
                        + source.path("fileId").asText() + " / 문자 " + source.path("characterStart").asInt() + "–"
                        + source.path("characterEnd").asInt() + "\n" + source.path("quote").asText());
                text(detail, 4, String.join("\n\n", sources), body);
            }
            sheet.createFreezePane(6, 1); sheet.setAutoFilter(new CellRangeAddress(0, index - 1, 0, 14));
            details.createFreezePane(1, 1); details.setAutoFilter(new CellRangeAddress(0, index - 1, 0, 4));
            var validation = sheet.getDataValidationHelper().createValidation(
                    sheet.getDataValidationHelper().createExplicitListConstraint(new String[]{"P", "F", "N/A"}),
                    new CellRangeAddressList(1, index - 1, 11, 11));
            validation.setEmptyCellAllowed(true); validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox("시험 결과", "P, F, N/A 중 하나를 선택하세요. 미실행 항목은 비워 두세요.");
            sheet.addValidationData(validation);
            var info = book.createSheet("안내"); info.setColumnWidth(0, 110 * 256);
            String[] notes = {"검토용 TC 초안 — 시험 결과 미기록", "결함 관련 열은 리포트 작성 예시이며 실제 발견된 결함이 아닙니다.",
                    "담당자가 시험 후 TC 시트의 결과 열에 P/F/N/A를 기록하세요. Excel에서 입력한 결과는 서버에 자동 반영되지 않습니다.",
                    "보안·성능 항목은 수동 시험입니다. 자동 실행은 수행되지 않았습니다.",
                    "생성 모드: " + job.path("output").path("generationMode").asText(),
                    "생성 작업: " + job.path("jobId").asText(), "승인 시각(UTC): " + job.path("approvedAt").asText()};
            for (int r = 0; r < notes.length; r++) { var row = info.createRow(r); row.setHeightInPoints(36); text(row, 0, notes[r], body); }
            book.write(bytes); return bytes.toByteArray();
        } catch (IOException failed) {
            throw new ProductDescriptionException(HttpStatus.INTERNAL_SERVER_ERROR, "TC_EXPORT_FAILED", "Excel 파일을 만들지 못했습니다.");
        }
    }

    private static String lines(JsonNode values, boolean numbered) {
        List<String> lines = new ArrayList<>(); int n = 1;
        for (var value : values) lines.add((numbered ? (n++) + ". " : "") + value.asText());
        return String.join("\n", lines);
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        if (value.length() > 32_767) throw new ProductDescriptionException(HttpStatus.UNPROCESSABLE_ENTITY,
                "TC_EXPORT_CELL_TOO_LONG", "Excel 셀 최대 길이를 초과한 TC가 있습니다. 내용을 줄인 뒤 다시 시도하세요.");
        // Explicit STRING cells prevent model-controlled strings from becoming formulas.
        var cell = row.createCell(column, CellType.STRING); cell.setCellValue(value); cell.setCellStyle(style);
    }
}
