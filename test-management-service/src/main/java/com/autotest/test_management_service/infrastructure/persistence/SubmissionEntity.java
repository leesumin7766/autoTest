package com.autotest.test_management_service.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.vo.MemberId;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "submissions")
public class SubmissionEntity {
    @Id
    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "member_id", nullable = false))
    private MemberId memberId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "product_id", nullable = false))
    private ProductId productId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SubmissionStatus status;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected SubmissionEntity() {
    }

    private SubmissionEntity(Submission submission) {
        this.id = submission.submissionId().value();
        this.memberId = submission.memberId();
        this.productId = submission.productId();
        this.status = submission.status();
        this.uploadedAt = submission.uploadedAt().orElseGet(Instant::now);
    }

    static SubmissionEntity fromDomain(Submission submission) {
        return new SubmissionEntity(submission);
    }

    Submission toDomain() {
        return Submission.reconstitute(new SubmissionId(id), memberId, productId,
            status, uploadedAt);
    }
}