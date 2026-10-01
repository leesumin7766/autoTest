package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Component
public final class HwpFileParser implements FileParser {

    @Override
    public boolean supports(FileFormat format) {
        return format == FileFormat.HWP;
    }

    @Override
    public ParsedContent parse(FileMetadata metadata, InputStream content) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(content, "content");

        try {
            byte[] bytes = readAllBytes(content);
            HWPFile hwpFile = HWPReader.fromInputStream(new ByteArrayInputStream(bytes));
            if (hwpFile == null) {
                throw new DocumentParsingException("Failed to parse HWP document");
            }

            String extractedText = TextExtractor.extract(hwpFile, TextExtractMethod.InsertControlTextBetweenParagraphText);
            if (extractedText != null) {
                extractedText = extractedText.trim();
            }

            if (extractedText == null || extractedText.isBlank()) {
                throw new DocumentParsingException("텍스트를 추출할 수 없음/OCR 필요");
            }

            return new ParsedContent(metadata, FileFormat.HWP, extractedText, bytes.length);
        } catch (DocumentParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to extract text from HWP document: " + e.getMessage(), e);
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