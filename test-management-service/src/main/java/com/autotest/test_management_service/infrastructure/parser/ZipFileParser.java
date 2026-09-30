package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.application.port.FileParser;
import com.autotest.test_management_service.domain.submission.TestCase;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public final class ZipFileParser implements FileParser {
    @Override
    public boolean supports(SubmissionType type) {
        return type == SubmissionType.ZIP;
    }

    @Override
    public List<TestCase> parse(InputStream input, String filename) {
        return List.of();
    }

    @Override
    public String extractText(InputStream input) {
        try (ZipInputStream zipInput = new ZipInputStream(input)) {
            ZipEntry entry = zipInput.getNextEntry();
            return entry == null ? "" : entry.getName();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read ZIP file", exception);
        }
    }
}