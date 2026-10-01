package com.autotest.test_management_service.domain.submission;

import com.autotest.test_management_service.domain.vo.SubmissionType;

import java.time.Instant;
import java.util.UUID;

public record SubmittedDocument(
        UUID fileId,
        SubmissionId submissionId,
        com.autotest.test_management_service.domain.submission.SubmissionType role,
        SubmissionType fileType,
        FileFormat format,
        String originalFilename,
        String mimeType,
        StoredPath storedPath,
        String extractedText,
        SubmissionStatus status,
        String failureReason,
        Instant uploadedAt
) {
}