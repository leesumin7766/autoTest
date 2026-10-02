package com.autotest.test_management_service.application.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockMultipartFile;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import com.autotest.test_management_service.infrastructure.parser.DocumentFileParserFactory;
import com.autotest.test_management_service.infrastructure.parser.PdfFileParser;

class SubmissionServiceTest {
    private static final MemberId MEMBER_ID = new MemberId(17L);
    private static final ProductId PRODUCT_ID = new ProductId(23L);

    @Test
    void storesS3PathParsesAndPersistsSubmission() throws Exception {
        StoredPath s3Path = new StoredPath("s3://autotest-docs/uuid-document.pdf");
        SubmissionId submissionId = submitWithStoredPath(s3Path);

        assertTrue(submissionId.value() != null);
    }

    @Test
    void persistsSubmissionWhenStorageReturnsLocalFallbackPath() throws Exception {
        StoredPath localPath = new StoredPath("local:/tmp/autotest-docs/uuid-document.pdf");
        SubmissionId submissionId = submitWithStoredPath(localPath);

        assertTrue(submissionId.value() != null);
    }

    @Test
    void rejectsUnsupportedUploadFormatBeforeStorage() {
        SubmissionPersistenceService submissionPersistenceService = mock(SubmissionPersistenceService.class);
        FileStoragePort fileStoragePort = mock(FileStoragePort.class);
        SubmissionService service = new SubmissionService(
                submissionPersistenceService,
                fileStoragePort,
                new DocumentFileParserFactory(java.util.List.of(new PdfFileParser())),
                new FileTypeResolver()
        );
        MockMultipartFile file = new MockMultipartFile("file", "document.txt", "text/plain", "not allowed".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.submit(file, MEMBER_ID, PRODUCT_ID));

        assertEquals("허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다.", exception.getMessage());
        verifyNoInteractions(submissionPersistenceService, fileStoragePort);
    }

        @Test
        void rejectsExtensionAndMimeMismatchBeforeStorage() {
        SubmissionPersistenceService submissionPersistenceService = mock(SubmissionPersistenceService.class);
        FileStoragePort fileStoragePort = mock(FileStoragePort.class);
        SubmissionService service = new SubmissionService(
            submissionPersistenceService,
            fileStoragePort,
            new DocumentFileParserFactory(java.util.List.of(new PdfFileParser())),
            new FileTypeResolver()
        );
        MockMultipartFile file = new MockMultipartFile(
            "file", "document.docx", "application/pdf", "not a Word file".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> service.submit(file, MEMBER_ID, PRODUCT_ID));

        assertEquals("허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다.", exception.getMessage());
        verifyNoInteractions(submissionPersistenceService, fileStoragePort);
        }

            @Test
            void replacesFailedDocumentAfterSuccessfulReparseAndDeletesOldObject() throws Exception {
            SubmissionPersistenceService persistence = mock(SubmissionPersistenceService.class);
            FileStoragePort storage = mock(FileStoragePort.class);
            SubmissionId id = SubmissionId.generate();
            StoredPath oldPath = new StoredPath("s3://autotest-docs/failed.pdf");
            StoredPath newPath = new StoredPath("s3://autotest-docs/replacement.pdf");
            UUID oldFileId = UUID.randomUUID();
            Submission failedSubmission = Submission.reconstitute(id, MEMBER_ID, PRODUCT_ID,
                com.autotest.test_management_service.domain.vo.SubmissionType.PDF,
                oldPath, "", SubmissionStatus.FAILED, "parse failed", Instant.now());
            SubmittedDocument failedDocument = submittedDocument(id, oldFileId, oldPath, SubmissionStatus.FAILED, "");
            byte[] pdf = createPdfContent("Recovered contract scope");
            when(persistence.findById(id)).thenReturn(java.util.Optional.of(failedSubmission));
            when(persistence.findDocumentsBySubmissionId(id)).thenReturn(List.of(failedDocument));
            when(storage.store(any(InputStream.class), eq("replacement.pdf"), eq("application/pdf"))).thenReturn(newPath);
            when(storage.load(newPath)).thenReturn(new java.io.ByteArrayInputStream(pdf));
            when(persistence.replaceFailedDocument(eq(oldFileId), eq(oldPath), any(SubmittedDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
            SubmissionService service = new SubmissionService(persistence, storage,
                new DocumentFileParserFactory(List.of(new PdfFileParser())), new FileTypeResolver());

            SubmittedDocument replacement = service.replaceFailedDocument(id,
                new MockMultipartFile("file", "replacement.pdf", "application/pdf", pdf),
                com.autotest.test_management_service.domain.submission.SubmissionType.AGREEMENT, MEMBER_ID);

            assertEquals(SubmissionStatus.PARSED, replacement.status());
            assertTrue(replacement.extractedText().contains("Recovered contract scope"));
            verify(persistence).replaceFailedDocument(eq(oldFileId), eq(oldPath), eq(replacement));
            verify(storage).delete(oldPath);
            verify(storage, org.mockito.Mockito.never()).delete(newPath);
            }

            @Test
            void failedReplacementParsingPreservesExistingDocumentAndDeletesNewObject() throws Exception {
            SubmissionPersistenceService persistence = mock(SubmissionPersistenceService.class);
            FileStoragePort storage = mock(FileStoragePort.class);
            SubmissionId id = SubmissionId.generate();
            StoredPath oldPath = new StoredPath("s3://autotest-docs/failed.pdf");
            StoredPath newPath = new StoredPath("s3://autotest-docs/empty.pdf");
            UUID oldFileId = UUID.randomUUID();
            Submission failedSubmission = Submission.reconstitute(id, MEMBER_ID, PRODUCT_ID,
                com.autotest.test_management_service.domain.vo.SubmissionType.PDF,
                oldPath, "", SubmissionStatus.FAILED, "parse failed", Instant.now());
            when(persistence.findById(id)).thenReturn(java.util.Optional.of(failedSubmission));
            when(persistence.findDocumentsBySubmissionId(id)).thenReturn(List.of(
                submittedDocument(id, oldFileId, oldPath, SubmissionStatus.FAILED, "")));
            when(storage.store(any(InputStream.class), eq("empty.pdf"), eq("application/pdf"))).thenReturn(newPath);
            when(storage.load(newPath)).thenReturn(new java.io.ByteArrayInputStream(createPdfContent("")));
            SubmissionService service = new SubmissionService(persistence, storage,
                new DocumentFileParserFactory(List.of(new PdfFileParser())), new FileTypeResolver());

            assertThrows(IllegalArgumentException.class, () -> service.replaceFailedDocument(id,
                new MockMultipartFile("file", "empty.pdf", "application/pdf", createPdfContent("")),
                com.autotest.test_management_service.domain.submission.SubmissionType.AGREEMENT, MEMBER_ID));

            verify(persistence, org.mockito.Mockito.never())
                .replaceFailedDocument(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            verify(storage).delete(newPath);
            verify(storage, org.mockito.Mockito.never()).delete(oldPath);
            }

            private SubmittedDocument submittedDocument(
                SubmissionId id,
                UUID fileId,
                StoredPath storedPath,
                SubmissionStatus status,
                String extractedText
            ) {
            return new SubmittedDocument(fileId, id,
                com.autotest.test_management_service.domain.submission.SubmissionType.AGREEMENT,
                SubmissionType.PDF, com.autotest.test_management_service.domain.submission.FileFormat.PDF,
                "failed.pdf", "application/pdf", storedPath, extractedText, status,
                status == SubmissionStatus.FAILED ? "parse failed" : null, Instant.now());
            }

            private byte[] createPdfContent(String text) throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
                document.addPage(page);
                if (!text.isBlank()) {
                try (org.apache.pdfbox.pdmodel.PDPageContentStream stream =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                    stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                    stream.beginText();
                    stream.newLineAtOffset(70, 700);
                    stream.showText(text);
                    stream.endText();
                }
                }
                document.save(out);
            }
            return out.toByteArray();
            }

    private SubmissionId submitWithStoredPath(StoredPath storedPath) throws Exception {
        SubmissionPersistenceService submissionPersistenceService = mock(SubmissionPersistenceService.class);
        FileStoragePort fileStoragePort = mock(FileStoragePort.class);
        java.util.concurrent.atomic.AtomicReference<Submission> uploadedRef = new java.util.concurrent.atomic.AtomicReference<>();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                stream.beginText();
                stream.newLineAtOffset(100, 700);
                stream.showText("Sample PDF text for test");
                stream.endText();
            }
            doc.save(out);
        }
        byte[] source = out.toByteArray();
        MockMultipartFile file = new MockMultipartFile("file", "document.pdf", "application/pdf", source);

        when(fileStoragePort.store(any(InputStream.class), eq("document.pdf"), eq("application/pdf")))
                .thenReturn(storedPath);
        when(fileStoragePort.load(storedPath))
                .thenAnswer(inv -> new java.io.ByteArrayInputStream(source));
        when(submissionPersistenceService.saveUploaded(any(Submission.class)))
            .thenAnswer(invocation -> {
                Submission uploaded = invocation.getArgument(0);
                uploadedRef.set(uploaded);
                return uploaded;
            });
        when(submissionPersistenceService.markAsParsed(any(), any(String.class)))
            .thenAnswer(invocation -> uploadedRef.get().markAsParsed(invocation.getArgument(1)));

        SubmissionService service = new SubmissionService(
            submissionPersistenceService,
                fileStoragePort,
                new DocumentFileParserFactory(java.util.List.of(new PdfFileParser())),
            new FileTypeResolver()
        );

        Submission persistedSubmission = service.submit(file, MEMBER_ID, PRODUCT_ID);

        var submissionCaptor = org.mockito.ArgumentCaptor.forClass(Submission.class);
        verify(submissionPersistenceService).saveUploaded(submissionCaptor.capture());
        verify(submissionPersistenceService).markAsParsed(persistedSubmission.submissionId(), persistedSubmission.extractedText());
        var documentCaptor = org.mockito.ArgumentCaptor.forClass(
            com.autotest.test_management_service.domain.submission.SubmittedDocument.class);
        verify(submissionPersistenceService).saveDocument(documentCaptor.capture());
        verify(fileStoragePort).store(any(InputStream.class), eq("document.pdf"), eq("application/pdf"));
        verify(fileStoragePort).load(storedPath);

        Submission uploadedSubmission = submissionCaptor.getValue();
        assertEquals(com.autotest.test_management_service.domain.submission.SubmissionStatus.UPLOADED, uploadedSubmission.status());
        assertEquals("", uploadedSubmission.extractedText());
        assertEquals(MEMBER_ID, persistedSubmission.memberId());
        assertEquals(PRODUCT_ID, persistedSubmission.productId());
        assertEquals(SubmissionType.PDF, persistedSubmission.submissionType());
        assertEquals(storedPath, persistedSubmission.storedPath());
        assertEquals(com.autotest.test_management_service.domain.submission.SubmissionStatus.PARSED, persistedSubmission.status());
        assertTrue(persistedSubmission.extractedText().contains("Sample PDF text for test"));
        assertTrue(persistedSubmission.testCases().isEmpty());
        assertEquals(uploadedSubmission.submissionId(), persistedSubmission.submissionId());
        assertEquals(com.autotest.test_management_service.domain.submission.SubmissionType.AGREEMENT,
            documentCaptor.getValue().role());
        assertEquals(SubmissionType.PDF, documentCaptor.getValue().fileType());
        assertEquals(storedPath, documentCaptor.getValue().storedPath());
        assertTrue(documentCaptor.getValue().extractedText().contains("Sample PDF text for test"));
        var ordered = inOrder(submissionPersistenceService, fileStoragePort);
        ordered.verify(submissionPersistenceService).saveUploaded(any(Submission.class));
        ordered.verify(fileStoragePort).load(storedPath);
        ordered.verify(submissionPersistenceService).markAsParsed(any(), any(String.class));
        return persistedSubmission.submissionId();
    }
}