package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.reader.HWPXReader;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Component
public final class HwpxFileParser implements FileParser {

    @Override
    public boolean supports(FileFormat format) {
        return format == FileFormat.HWPX;
    }

    @Override
    public ParsedContent parse(FileMetadata metadata, InputStream content) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(content, "content");

        try {
            byte[] bytes = readAllBytes(content);
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("autotest-hwpx-", ".hwpx");
            try {
                java.nio.file.Files.write(tempFile, bytes);
                HWPXFile hwpxFile = HWPXReader.fromFile(tempFile.toFile());
                if (hwpxFile == null) {
                    throw new DocumentParsingException("Failed to parse HWPX document");
                }

                String extractedText = TextExtractor.extract(hwpxFile, TextExtractMethod.InsertControlTextBetweenParagraphText, true, null);
                if (extractedText != null) {
                    extractedText = extractedText.trim();
                }

                if (extractedText == null || extractedText.isBlank()) {
                    throw new DocumentParsingException("텍스트를 추출할 수 없음/OCR 필요");
                }

                return new ParsedContent(metadata, FileFormat.HWPX, extractedText, bytes.length);
            } finally {
                java.nio.file.Files.deleteIfExists(tempFile);
            }
        } catch (DocumentParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to extract text from HWPX document: " + e.getMessage(), e);
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
