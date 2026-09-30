package com.autotest.test_management_service.domain.submission;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.autotest.test_management_service.domain.event.SubmissionUploadedEvent;

class SubmissionTest {
    private static final ProductId PRODUCT_ID = new ProductId(1L);
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;

    @Test
    void factoryCreatesDraftAndAddingFileReturnsUpdatedAggregate() {
        Submission draft = SubmissionFactory.create(PRODUCT_ID);

        Submission updated = draft.addFile(SubmissionType.AGREEMENT, metadata("agreement.pdf", 10L), FileFormat.PDF);

        assertEquals(SubmissionStatus.DRAFT, draft.status());
        assertTrue(draft.getFiles().isEmpty());
        assertEquals(1, updated.getFiles().size());
        assertEquals(FileFormat.PDF, updated.getFiles().getFirst().fileFormat());
        assertTrue(updated.getFiles().getFirst().storedPath().isEmpty());
    }

    @Test
    void addFileRejectsDuplicateSubmissionTypeAndFilesOver100Mib() {
        Submission draft = SubmissionFactory.create(PRODUCT_ID)
                .addFile(SubmissionType.AGREEMENT, metadata("agreement.pdf", MAX_FILE_SIZE_BYTES), FileFormat.PDF);

        assertThrows(IllegalArgumentException.class, () -> draft.addFile(
                SubmissionType.AGREEMENT,
                metadata("other.pdf", 10L),
                FileFormat.PDF
        ));
        assertThrows(IllegalArgumentException.class, () -> SubmissionFactory.create(PRODUCT_ID).addFile(
                SubmissionType.MANUAL,
                metadata("manual.pdf", MAX_FILE_SIZE_BYTES + 1),
                FileFormat.PDF
        ));
    }

    @Test
    void uploadedTransitionRequiresStoredFilesAndRegistersEvent() {
        Submission withFile = SubmissionFactory.create(PRODUCT_ID)
                .addFile(SubmissionType.FUNCTION_LIST, metadata("functions.xlsx", 100L), FileFormat.XLSX);

        assertThrows(IllegalStateException.class, withFile::markAsUploaded);

        Submission stored = withFile.assignStoredPath(
                withFile.getFiles().getFirst().id(),
                new StoredPath("submissions/functions.xlsx")
        );
        Submission uploaded = stored.markAsUploaded();

        assertEquals(SubmissionStatus.DRAFT, stored.status());
        assertEquals(SubmissionStatus.UPLOADED, uploaded.status());
        assertTrue(uploaded.uploadedAt().isPresent());
        assertEquals(1, uploaded.domainEvents().size());
        assertInstanceOf(SubmissionUploadedEvent.class, uploaded.domainEvents().getFirst());
        assertEquals(SubmissionStatus.PARSED, uploaded.markAsParsed().status());
    }

    @Test
    void removeFileSupportsIdAndSubmissionType() {
        Submission withFile = SubmissionFactory.create(PRODUCT_ID)
                .addFile(SubmissionType.MANUAL, metadata("manual.hwp", 50L), FileFormat.HWP);
        SubmissionFileId fileId = withFile.getFiles().getFirst().id();

        assertTrue(withFile.removeFile(fileId).getFiles().isEmpty());
        assertTrue(withFile.removeFileByType(SubmissionType.MANUAL).getFiles().isEmpty());
        assertFalse(withFile.getFiles().isEmpty());
    }

    @Test
    void getFilesReturnsAnUnmodifiableList() {
        Submission submission = SubmissionFactory.create(PRODUCT_ID)
                .addFile(SubmissionType.AGREEMENT, metadata("agreement.docx", 20L), FileFormat.DOCX);
        List<SubmissionFile> files = submission.getFiles();

        assertThrows(UnsupportedOperationException.class, files::clear);
    }

    private static FileMetadata metadata(String name, long size) {
        return new FileMetadata(name, size, "checksum", "application/octet-stream");
    }
}