package com.autotest.test_management_service.domain.submission;

import java.util.Objects;

public record StoredPath(String value) {
    public StoredPath {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public boolean isS3() {
        return value.startsWith("s3://");
    }

    public boolean isLocal() {
        return value.startsWith("local:");
    }
}