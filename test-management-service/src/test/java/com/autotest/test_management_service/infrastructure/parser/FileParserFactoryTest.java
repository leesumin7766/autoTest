package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileParserFactoryTest {
    private final PdfFileParser pdfParser = new PdfFileParser();
    private final DocxFileParser docxParser = new DocxFileParser();
    private final ExcelFileParser excelParser = new ExcelFileParser();
    private final HwpFileParser hwpParser = new HwpFileParser();
    private final FileParserFactory factory = new FileParserFactory(
            List.of(pdfParser, docxParser, excelParser, hwpParser)
    );

    @Test
    void factorySelectsParserForEachSupportedFormat() {
        assertInstanceOf(PdfFileParser.class, factory.getParser(FileFormat.PDF));
        assertInstanceOf(DocxFileParser.class, factory.getParser(FileFormat.DOCX));
        assertInstanceOf(ExcelFileParser.class, factory.getParser(FileFormat.XLSX));
        assertInstanceOf(ExcelFileParser.class, factory.getParser(FileFormat.XLS));
        assertInstanceOf(HwpFileParser.class, factory.getParser(FileFormat.HWP));
    }

    @Test
    void parserReturnsMetadataFormatAndActualSizeWithoutExtractingText() {
        FileMetadata metadata = new FileMetadata("report.xlsx", 4L, "checksum", "application/xlsx");
        FileParser parser = factory.getParser(FileFormat.XLSX);

        ParsedContent result = parser.parse(metadata, new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        assertEquals(metadata, result.metadata());
        assertEquals(FileFormat.XLSX, result.format());
        assertEquals("", result.extractedText());
        assertEquals(4L, result.actualSizeBytes());
    }

    @Test
    void parserRejectsMetadataAndStreamSizeMismatch() {
        FileMetadata metadata = new FileMetadata("report.pdf", 5L, "checksum", "application/pdf");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> pdfParser.parse(
                metadata,
                new ByteArrayInputStream(new byte[]{1, 2, 3, 4})
        ));
        assertEquals("Metadata size does not match actual size", exception.getMessage());
    }

    @Test
    void factoryFailsWhenNoParserSupportsRequestedFormat() {
        FileParserFactory pdfOnlyFactory = new FileParserFactory(List.of(pdfParser));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pdfOnlyFactory.getParser(FileFormat.HWP)
        );
        assertEquals("No parser supports format: HWP", exception.getMessage());
        assertTrue(pdfParser.supports(FileFormat.PDF));
    }
}