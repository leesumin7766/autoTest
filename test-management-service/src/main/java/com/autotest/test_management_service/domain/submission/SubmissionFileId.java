package com.autotest.test_management_service.domain.submission;

import java.util.Objects;
import java.util.UUID;

public record SubmissionFileId(UUID value) {
    public SubmissionFileId {
        Objects.requireNonNull(value, "value");
    }

    public static SubmissionFileId generate() {
        return new SubmissionFileId(UUID.randomUUID());
    }
}