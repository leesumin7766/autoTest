package com.autotest.test_management_service.infrastructure.parser;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.FileMetadata;
import com.autotest.test_management_service.domain.submission.FileParser;
import com.autotest.test_management_service.domain.submission.ParsedContent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.writer.HWPWriter;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.writer.HWPXWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileParserTest {
    private final PdfFileParser pdfParser = new PdfFileParser();
    private final DocxFileParser docxParser = new DocxFileParser();
    private final ExcelFileParser excelParser = new ExcelFileParser();
    private final HwpFileParser hwpParser = new HwpFileParser();
    private final HwpxFileParser hwpxParser = new HwpxFileParser();
    private final DocumentFileParserFactory factory = new DocumentFileParserFactory(
            List.of(pdfParser, docxParser, excelParser, hwpParser, hwpxParser)
    );

    @Test
    void factorySelectsParserForEachSupportedFormat() {
        assertInstanceOf(PdfFileParser.class, factory.getParser(FileFormat.PDF));
        assertInstanceOf(DocxFileParser.class, factory.getParser(FileFormat.DOC));
        assertInstanceOf(DocxFileParser.class, factory.getParser(FileFormat.DOCX));
        assertInstanceOf(ExcelFileParser.class, factory.getParser(FileFormat.XLSX));
        assertInstanceOf(ExcelFileParser.class, factory.getParser(FileFormat.XLS));
        assertInstanceOf(HwpFileParser.class, factory.getParser(FileFormat.HWP));
        assertInstanceOf(HwpxFileParser.class, factory.getParser(FileFormat.HWPX));
    }

    @Test
    void parsesPdfFixtureWithText() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.beginText();
                stream.newLineAtOffset(100, 700);
                stream.showText("Hello PDF World");
                stream.endText();
            }
            doc.save(out);
        }

        byte[] bytes = out.toByteArray();
        FileMetadata metadata = new FileMetadata("test.pdf", bytes.length, "dummy", "application/pdf");
        ParsedContent content = pdfParser.parse(metadata, new ByteArrayInputStream(bytes));

        assertEquals(FileFormat.PDF, content.format());
        assertTrue(content.extractedText().contains("Hello PDF World"));
    }

    @Test
    void parsesXlsxFixtureWithText() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("TestSheet");
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue("Cell Text 123");
            wb.write(out);
        }

        byte[] bytes = out.toByteArray();
        FileMetadata metadata = new FileMetadata("test.xlsx", bytes.length, "dummy", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        ParsedContent content = excelParser.parse(metadata, new ByteArrayInputStream(bytes));

        assertEquals(FileFormat.XLSX, content.format());
        assertTrue(content.extractedText().contains("Cell Text 123"));
        assertTrue(content.extractedText().contains("TestSheet"));
    }

    @Test
    void rejectsXlsxWithSheetButNoCellContent() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("EmptySheet");
            wb.write(out);
        }

        byte[] bytes = out.toByteArray();
        FileMetadata metadata = new FileMetadata("empty.xlsx", bytes.length, "dummy", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        DocumentParsingException exception = assertThrows(DocumentParsingException.class, () ->
                excelParser.parse(metadata, new ByteArrayInputStream(bytes))
        );

        assertEquals("추출할 셀 내용이 없습니다", exception.getMessage());
    }

    @Test
    void parsesDocxFixtureWithText() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("Hello Word Docx");
            doc.write(out);
        }

        byte[] bytes = out.toByteArray();
        FileMetadata metadata = new FileMetadata("test.docx", bytes.length, "dummy", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ParsedContent content = docxParser.parse(metadata, new ByteArrayInputStream(bytes));

        assertEquals(FileFormat.DOCX, content.format());
        assertTrue(content.extractedText().contains("Hello Word Docx"));
    }

    @Test
    void parsesLegacyDocFixtureWithKnownBodyText() throws Exception {
        byte[] bytes;
        try (InputStream fixture = getClass().getResourceAsStream("/documents/SampleDoc.doc")) {
            assertNotNull(fixture);
            bytes = fixture.readAllBytes();
        }

        FileMetadata metadata = new FileMetadata("SampleDoc.doc", bytes.length, "dummy", "application/msword");
        ParsedContent content = docxParser.parse(metadata, new ByteArrayInputStream(bytes));

        assertEquals(FileFormat.DOC, content.format());
        assertTrue(content.extractedText().contains("I am a test document"));
        assertTrue(content.extractedText().contains("This is page two"));
    }

    @Test
    void throwsExceptionOnEmptyPdf() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(out);
        }

        byte[] bytes = out.toByteArray();
        FileMetadata metadata = new FileMetadata("empty.pdf", bytes.length, "dummy", "application/pdf");

        DocumentParsingException ex = assertThrows(DocumentParsingException.class, () ->
                pdfParser.parse(metadata, new ByteArrayInputStream(bytes))
        );
        assertTrue(ex.getMessage().contains("OCR"));
    }

    @Test
    void throwsExceptionOnCorruptedDocument() {
        byte[] corruptedBytes = "This is not a real document file".getBytes();
        FileMetadata metadata = new FileMetadata("test.pdf", corruptedBytes.length, "dummy", "application/pdf");

        assertThrows(DocumentParsingException.class, () ->
                pdfParser.parse(metadata, new ByteArrayInputStream(corruptedBytes))
        );
    }
}