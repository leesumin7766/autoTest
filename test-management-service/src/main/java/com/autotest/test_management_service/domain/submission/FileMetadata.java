package com.autotest.test_management_service.domain.submission;

import java.util.Objects;

public record FileMetadata(String originalName, long size, String checksum, String mimeType) {
    public FileMetadata {
        Objects.requireNonNull(originalName, "originalName");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(mimeType, "mimeType");

        if (originalName.isBlank()) {
            throw new IllegalArgumentException("originalName must not be blank");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        if (checksum.isBlank()) {
            throw new IllegalArgumentException("checksum must not be blank");
        }
        if (mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType must not be blank");
        }
    }
}