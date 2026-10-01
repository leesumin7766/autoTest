package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.TestCase;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestCaseParserTest {

    @Test
    void simpleTextFileParserExtractsTestCasesFromComments() {
        String content = "// @test input: 1 2\n// @test expected: 3\n// @test input: 4 5\n// @test expected: 9";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        SimpleTextFileParser parser = new SimpleTextFileParser();
        List<TestCase> testCases = parser.parse(stream, "Solution.java");

        assertEquals(2, testCases.size());

        assertEquals("1 2", testCases.get(0).input());
        assertEquals("3", testCases.get(0).expectedOutput());
        assertNull(testCases.get(0).submissionId(), "submissionId should be null before service fills it");

        assertEquals("4 5", testCases.get(1).input());
        assertEquals("9", testCases.get(1).expectedOutput());
        assertNull(testCases.get(1).submissionId(), "submissionId should be null before service fills it");
    }

    @Test
    void zipFileParserExtractsTestCasesFromEntries() throws IOException {
        String javaContent = "// @test input: hello\n// @test expected: olleh\n";
        String pyContent = "# @test input: 10\n# @test expected: 20\n";

        byte[] zipBytes = createZip(
                new String[]{"Solution.java", "helper.py", "readme.txt"},
                new String[]{javaContent, pyContent, "just a text file"}
        );
        InputStream zipStream = new ByteArrayInputStream(zipBytes);

        ZipFileParser parser = new ZipFileParser();
        List<TestCase> testCases = parser.parse(zipStream, "submission.zip");

        assertEquals(2, testCases.size());

        assertEquals("hello", testCases.get(0).input());
        assertEquals("olleh", testCases.get(0).expectedOutput());

        assertEquals("10", testCases.get(1).input());
        assertEquals("20", testCases.get(1).expectedOutput());
    }

    private byte[] createZip(String[] names, String[] contents) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < names.length; i++) {
                zos.putNextEntry(new ZipEntry(names[i]));
                zos.write(contents[i].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
