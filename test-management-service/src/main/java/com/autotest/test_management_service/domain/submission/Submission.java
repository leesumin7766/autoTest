package com.autotest.test_management_service.domain.submission;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.autotest.test_management_service.domain.event.SubmissionUploadedEvent;
import com.autotest.test_management_service.domain.vo.MemberId;

public final class Submission {
    private final SubmissionId submissionId;
    private final MemberId memberId;
    private final ProductId productId;
    private final List<TestCase> testCases;
    private final SubmissionStatus status;
    private final Instant uploadedAt;
    private final List<Object> domainEvents;

    private Submission(
            SubmissionId submissionId,
            MemberId memberId,
            ProductId productId,
            List<TestCase> testCases,
            SubmissionStatus status,
            Instant uploadedAt,
            List<Object> domainEvents
    ) {
        this.submissionId = Objects.requireNonNull(submissionId, "submissionId");
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.testCases = List.copyOf(testCases);
        this.status = Objects.requireNonNull(status, "status");
        this.uploadedAt = uploadedAt;
        this.domainEvents = List.copyOf(domainEvents);
    }

    public static Submission create(MemberId memberId, ProductId productId) {
        return new Submission(SubmissionId.generate(), memberId, productId, List.of(),
            SubmissionStatus.UPLOADED, Instant.now(), List.of());
    }

    public static Submission reconstitute(
            SubmissionId submissionId,
            MemberId memberId,
            ProductId productId,
            SubmissionStatus status,
            Instant uploadedAt
    ) {
            return new Submission(submissionId, memberId, productId, List.of(), status,
                uploadedAt, List.of());
    }

    static Submission draft(MemberId memberId, ProductId productId) {
        return new Submission(SubmissionId.generate(), memberId, productId, List.of(),
            SubmissionStatus.DRAFT, null, List.of());
    }

    public Submission markAsUploaded() {
        requireStatus(SubmissionStatus.DRAFT);
        Instant uploadTime = Instant.now();
        return new Submission(submissionId, memberId, productId, testCases, SubmissionStatus.UPLOADED,
                uploadTime, List.of(new SubmissionUploadedEvent(submissionId, productId, uploadTime)));
    }

    public Submission withProcessingStatus(SubmissionStatus nextStatus) {
        if (nextStatus != SubmissionStatus.UPLOADED
                && nextStatus != SubmissionStatus.PARSED
                && nextStatus != SubmissionStatus.FAILED) {
            throw new IllegalArgumentException("Unsupported submission processing status: " + nextStatus);
        }
        return new Submission(submissionId, memberId, productId, testCases, nextStatus,
            uploadedAt, domainEvents);
    }

    public SubmissionId submissionId() {
        return submissionId;
    }

    public ProductId productId() {
        return productId;
    }

    public MemberId memberId() {
        return memberId;
    }

    public List<TestCase> testCases() {
        return List.copyOf(testCases);
    }

    public SubmissionStatus status() {
        return status;
    }

    public Optional<Instant> uploadedAt() {
        return Optional.ofNullable(uploadedAt);
    }

    public List<Object> domainEvents() {
        return List.copyOf(domainEvents);
    }

    private void requireStatus(SubmissionStatus requiredStatus) {
        if (status != requiredStatus) {
            throw new IllegalStateException("Expected status " + requiredStatus + " but was " + status);
        }
    }
}