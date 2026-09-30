package com.autotest.test_management_service.domain.submission;

import java.util.Objects;
import java.util.List;

import com.autotest.test_management_service.domain.vo.MemberId;
import com.autotest.test_management_service.domain.vo.SubmissionType;

public final class SubmissionFactory {
    private SubmissionFactory() {
    }

    public static Submission draft(MemberId memberId, ProductId productId) {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(productId, "productId");
        return Submission.draft(memberId, productId);
    }

    public static Submission create(
            MemberId memberId,
            ProductId productId,
            SubmissionType submissionType,
            StoredPath storedPath,
            String extractedText,
            List<TestCase> testCases
    ) {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(productId, "productId");
        return Submission.create(memberId, productId, submissionType, storedPath, extractedText, testCases);
    }
}