package com.autotest.test_management_service.domain.submission;

import java.util.Objects;
import java.util.UUID;

public record SubmissionId(UUID value) {
    public SubmissionId {
        Objects.requireNonNull(value, "value");
    }

    public static SubmissionId generate() {
        return new SubmissionId(UUID.randomUUID());
    }

    public static SubmissionId of(UUID value) {
        return new SubmissionId(value);
    }
}