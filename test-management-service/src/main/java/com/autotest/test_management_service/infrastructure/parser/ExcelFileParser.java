package com.autotest.test_management_service.infrastructure.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;

@Component
public final class ExcelFileParser implements FileParser {

    @Override
    public boolean supports(FileFormat format) {
        return format == FileFormat.XLS || format == FileFormat.XLSX;
    }

    @Override
    public ParsedContent parse(FileMetadata metadata, InputStream content) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(content, "content");

        FileFormat format = metadata.originalName().toLowerCase().endsWith(".xls") ? FileFormat.XLS : FileFormat.XLSX;

        try {
            byte[] bytes = readAllBytes(content);
            try (Workbook workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
                DataFormatter formatter = new DataFormatter();
                StringBuilder builder = new StringBuilder();
                boolean hasCellContent = false;

                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    String sheetName = sheet.getSheetName();
                    if (builder.length() > 0) {
                        builder.append("\n\n");
                    }
                    builder.append("=== Sheet: ").append(sheetName).append(" ===");

                    for (Row row : sheet) {
                        StringBuilder rowBuilder = new StringBuilder();
                        for (Cell cell : row) {
                            String cellValue = formatter.formatCellValue(cell).trim();
                            if (!cellValue.isEmpty()) {
                                hasCellContent = true;
                                if (rowBuilder.length() > 0) {
                                    rowBuilder.append("\t");
                                }
                                rowBuilder.append(cellValue);
                            }
                        }
                        if (rowBuilder.length() > 0) {
                            builder.append("\n").append(rowBuilder);
                        }
                    }
                }

                if (!hasCellContent) {
                    throw new DocumentParsingException("추출할 셀 내용이 없습니다");
                }

                String extractedText = builder.toString().trim();
                return new ParsedContent(metadata, format, extractedText, bytes.length);
            } catch (EncryptedDocumentException e) {
                throw new DocumentParsingException("Encrypted Excel document cannot be parsed", e);
            }
        } catch (DocumentParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to extract text from Excel document: " + e.getMessage(), e);
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}