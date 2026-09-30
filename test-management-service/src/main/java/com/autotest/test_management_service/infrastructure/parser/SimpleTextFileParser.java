package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.application.port.FileParser;
import com.autotest.test_management_service.domain.submission.TestCase;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Component
public final class SimpleTextFileParser implements FileParser {
    private static final Set<SubmissionType> SUPPORTED_TYPES = Set.of(
            SubmissionType.JAVA,
            SubmissionType.PYTHON,
            SubmissionType.JAVASCRIPT
    );

    @Override
    public boolean supports(SubmissionType type) {
        return SUPPORTED_TYPES.contains(type);
    }

    @Override
    public List<TestCase> parse(InputStream input, String filename) {
        return List.of();
    }

    @Override
    public String extractText(InputStream input) {
        try {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read source file", exception);
        }
    }
}