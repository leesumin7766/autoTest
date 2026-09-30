package com.autotest.test_management_service.domain.submission;

import java.util.Objects;

public final class SubmissionFactory {
    private SubmissionFactory() {
    }

    public static Submission create(ProductId productId) {
        Objects.requireNonNull(productId, "productId");
        return Submission.draft(productId);
    }
}