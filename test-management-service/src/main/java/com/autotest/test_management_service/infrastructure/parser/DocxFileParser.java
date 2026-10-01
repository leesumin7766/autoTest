package com.autotest.test_management_service.infrastructure.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.springframework.stereotype.Component;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;

@Component
public final class DocxFileParser implements FileParser {

    @Override
    public boolean supports(FileFormat format) {
        return format == FileFormat.DOC || format == FileFormat.DOCX;
    }

    @Override
    public ParsedContent parse(FileMetadata metadata, InputStream content) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(content, "content");

        FileFormat format = metadata.originalName().toLowerCase().endsWith(".doc") ? FileFormat.DOC : FileFormat.DOCX;

        try {
            byte[] bytes = readAllBytes(content);
            try (POITextExtractor extractor = ExtractorFactory.createExtractor(new ByteArrayInputStream(bytes))) {
                String extractedText = extractor.getText();
                if (extractedText != null) {
                    extractedText = extractedText.trim();
                }

                if (extractedText == null || extractedText.isBlank()) {
                    throw new DocumentParsingException("텍스트를 추출할 수 없음/OCR 필요");
                }

                return new ParsedContent(metadata, format, extractedText, bytes.length);
            } catch (EncryptedDocumentException e) {
                throw new DocumentParsingException("Encrypted Word document cannot be parsed", e);
            }
        } catch (DocumentParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to extract text from Word document: " + e.getMessage(), e);
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