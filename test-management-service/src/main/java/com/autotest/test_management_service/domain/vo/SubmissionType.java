package com.autotest.test_management_service.domain.vo;

import java.util.Locale;

public enum SubmissionType {
    JAVA("java"),
    PYTHON("py"),
    JAVASCRIPT("js"),
    ZIP("zip"),
    UNKNOWN("unknown");

    private final String extension;

    SubmissionType(String extension) {
        this.extension = extension;
    }

    public static SubmissionType fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return UNKNOWN;
        }

        String normalizedExtension = extension.trim().toLowerCase(Locale.ROOT);
        if (normalizedExtension.startsWith(".")) {
            normalizedExtension = normalizedExtension.substring(1);
        }

        for (SubmissionType type : values()) {
            if (type.extension.equals(normalizedExtension)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public String extension() {
        return extension;
    }
}