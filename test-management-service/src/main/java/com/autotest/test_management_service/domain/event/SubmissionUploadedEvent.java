package com.autotest.test_management_service.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.SubmissionId;

public record SubmissionUploadedEvent(SubmissionId submissionId, ProductId productId, Instant uploadedAt) {
    public SubmissionUploadedEvent {
        Objects.requireNonNull(submissionId, "submissionId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(uploadedAt, "uploadedAt");
    }
}