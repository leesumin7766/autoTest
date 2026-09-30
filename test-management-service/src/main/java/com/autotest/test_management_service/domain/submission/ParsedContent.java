package com.autotest.test_management_service.domain.submission;

import java.util.Objects;

public record ParsedContent(
        FileMetadata metadata,
        FileFormat format,
        String extractedText,
        long actualSizeBytes
) {
    public ParsedContent {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(extractedText, "extractedText");

        if (metadata.size() != actualSizeBytes && actualSizeBytes > 0) {
            throw new IllegalArgumentException("Metadata size does not match actual size");
        }
        if (actualSizeBytes < 0) {
            throw new IllegalArgumentException("actualSizeBytes must not be negative");
        }
    }
}