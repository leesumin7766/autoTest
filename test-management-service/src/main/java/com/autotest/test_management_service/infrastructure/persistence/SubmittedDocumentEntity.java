package com.autotest.test_management_service.infrastructure.persistence;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.vo.SubmissionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submission_files")
public class SubmittedDocumentEntity {
    @Id
    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private com.autotest.test_management_service.domain.submission.SubmissionType role;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private SubmissionType fileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 20)
    private FileFormat format;

    @Column(name = "original_filename", nullable = false, columnDefinition = "text")
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, columnDefinition = "text")
    private String mimeType;

    @Column(name = "stored_path", nullable = false, columnDefinition = "text")
    private String storedPath;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected SubmittedDocumentEntity() {
    }

    private SubmittedDocumentEntity(SubmittedDocument document) {
        id = document.fileId();
        submissionId = document.submissionId().value();
        role = document.role();
        fileType = document.fileType();
        format = document.format();
        originalFilename = document.originalFilename();
        mimeType = document.mimeType();
        storedPath = document.storedPath().value();
        extractedText = document.extractedText();
        status = document.status();
        failureReason = document.failureReason();
        uploadedAt = document.uploadedAt();
    }

    static SubmittedDocumentEntity fromDomain(SubmittedDocument document) {
        return new SubmittedDocumentEntity(document);
    }

    SubmittedDocument toDomain() {
        return new SubmittedDocument(
                id,
                SubmissionId.of(submissionId),
                role,
                fileType,
                format,
                originalFilename,
                mimeType,
                new StoredPath(storedPath),
                extractedText,
                status,
                failureReason,
                uploadedAt
        );
    }
}