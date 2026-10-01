package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.application.port.FileParser;
import com.autotest.test_management_service.domain.submission.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base parser for text-based source files (Java, Python, JavaScript).
 * Parses @test annotations in comments to extract TestCase input/expected pairs.
 *
 * Supported comment formats:
 * <pre>
 * // @test input: 1 2
 * // @test expected: 3
 * # @test input: hello
 * # @test expected: olleh
 * </pre>
 */
abstract class AbstractTextTestCaseParser implements FileParser {

    private static final Pattern TEST_CASE_PATTERN = Pattern.compile(
            "@test\\s+input:\\s*(.+?)\\s*(?:\\r?\\n|$).*?@test\\s+expected:\\s*(.+?)\\s*(?:\\r?\\n|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public List<TestCase> parse(InputStream input, String filename) {
        String content = readFully(input);
        List<TestCase> testCases = new ArrayList<>();
        Matcher matcher = TEST_CASE_PATTERN.matcher(content);

        while (matcher.find()) {
            String inputValue = matcher.group(1).trim();
            String expectedValue = matcher.group(2).trim();
            testCases.add(TestCase.createParsed(inputValue, expectedValue));
        }

        return testCases;
    }

    @Override
    public String extractText(InputStream input) {
        return readFully(input);
    }

    private String readFully(InputStream input) {
        try {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read source file", exception);
        }
    }
}
