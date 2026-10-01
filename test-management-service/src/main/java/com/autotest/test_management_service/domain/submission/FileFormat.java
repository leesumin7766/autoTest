package com.autotest.test_management_service.domain.submission;

import java.util.Locale;

public enum FileFormat {
    PDF,
    DOCX,
    HWP,
    XLSX,
    XLS;

    public static FileFormat fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("File extension must not be blank");
        }

        String normalizedExtension = extension.trim();
        if (normalizedExtension.startsWith(".")) {
            normalizedExtension = normalizedExtension.substring(1);
        }

        return switch (normalizedExtension.toUpperCase(Locale.ROOT)) {
            case "PDF" -> PDF;
            case "DOC", "DOCX" -> DOCX;
            case "HWP", "HWPX" -> HWP;
            case "XLSX" -> XLSX;
            case "XLS" -> XLS;
            default -> throw new IllegalArgumentException("Unsupported file extension: " + extension);
        };
    }
}