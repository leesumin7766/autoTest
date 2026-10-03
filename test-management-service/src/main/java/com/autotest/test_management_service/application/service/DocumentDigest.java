package com.autotest.test_management_service.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.autotest.test_management_service.domain.submission.SubmittedDocument;

/** Identifies the exact document set a preflight verdict applies to. Must match the SQL backfill in V9. */
public final class DocumentDigest {
    private DocumentDigest() {
    }

    public static String of(List<SubmittedDocument> documents) {
        String joined = documents.stream()
                .sorted(Comparator.comparing(document -> document.role().name()))
                .map(document -> document.role().name() + ":" + document.fileId() + ":" + sha256(document.extractedText()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return sha256(joined);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
