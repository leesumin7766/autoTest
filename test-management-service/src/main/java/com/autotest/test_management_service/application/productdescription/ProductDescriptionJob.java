package com.autotest.test_management_service.application.productdescription;

import java.time.Instant;
import java.util.UUID;

/** Job metadata only; stored content and the template snapshot are loaded separately because they are large. */
public record ProductDescriptionJob(
        UUID jobId,
        UUID submissionId,
        long memberId,
        String status,
        int attempt,
        String errorCode,
        String errorMessage,
        String inputDigest,
        String preflightDecision,
        String generationMode,
        String generationLabel,
        String templateId,
        String templateVersion,
        String outputFormat,
        String outputPath,
        String outputFilename,
        boolean hasContent,
        int completedChunks,
        int totalChunks,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static final String GENERATING_CONTENT = "GENERATING_CONTENT";
    public static final String RENDERING = "RENDERING";
    public static final String COMPLETED = "COMPLETED";
    public static final String CONTENT_FAILED = "CONTENT_FAILED";
    public static final String CONTENT_PAUSED = "CONTENT_PAUSED";
    public static final String RENDER_FAILED = "RENDER_FAILED";
    public static final String CANCELED = "CANCELED";

    public boolean active() {
        return GENERATING_CONTENT.equals(status) || RENDERING.equals(status);
    }

    public boolean resumable() {
        return CONTENT_PAUSED.equals(status) || (CONTENT_FAILED.equals(status) && !hasContent);
    }
}
