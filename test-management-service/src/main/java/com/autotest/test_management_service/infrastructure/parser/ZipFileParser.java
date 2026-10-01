package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.application.port.FileParser;
import com.autotest.test_management_service.domain.submission.TestCase;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parser for ZIP archive submissions.
 * Iterates through ZIP entries and delegates parsing of supported source files
 * (.java, .py, .js) to {@link AbstractTextTestCaseParser}'s parsing logic.
 */
@Component
public final class ZipFileParser implements FileParser {

    private static final Set<String> PARSEABLE_EXTENSIONS = Set.of(".java", ".py", ".js");

    /**
     * Internal parser instance to reuse AbstractTextTestCaseParser logic on individual entries.
     */
    private final AbstractTextTestCaseParser textParser = new AbstractTextTestCaseParser() {
        @Override
        public boolean supports(SubmissionType type) {
            return false; // not used directly
        }
    };

    @Override
    public boolean supports(SubmissionType type) {
        return type == SubmissionType.ZIP;
    }

    @Override
    public List<TestCase> parse(InputStream input, String filename) {
        List<TestCase> allTestCases = new ArrayList<>();

        try (ZipInputStream zipInput = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName().toLowerCase(Locale.ROOT);
                if (isParseableFile(entryName)) {
                    byte[] entryBytes = zipInput.readAllBytes();
                    ByteArrayInputStream entryStream = new ByteArrayInputStream(entryBytes);
                    List<TestCase> entryCases = textParser.parse(entryStream, entry.getName());
                    allTestCases.addAll(entryCases);
                }
                zipInput.closeEntry();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read ZIP file", exception);
        }

        return allTestCases;
    }

    @Override
    public String extractText(InputStream input) {
        StringJoiner fileNames = new StringJoiner(", ");

        try (ZipInputStream zipInput = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    fileNames.add(entry.getName());
                }
                zipInput.closeEntry();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read ZIP file", exception);
        }

        return fileNames.toString();
    }

    private boolean isParseableFile(String filename) {
        return PARSEABLE_EXTENSIONS.stream().anyMatch(filename::endsWith);
    }
}