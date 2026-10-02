package com.autotest.test_management_service.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.infrastructure.persistence.S3CleanupOutboxRepository;

@Component
public class S3CleanupOutboxWorker {
    private static final Logger logger = LoggerFactory.getLogger(S3CleanupOutboxWorker.class);

    private final S3CleanupOutboxRepository outboxRepository;
    private final FileStoragePort fileStoragePort;

    public S3CleanupOutboxWorker(
            S3CleanupOutboxRepository outboxRepository,
            FileStoragePort fileStoragePort
    ) {
        this.outboxRepository = outboxRepository;
        this.fileStoragePort = fileStoragePort;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 5_000)
    public void processPending() {
        for (S3CleanupOutboxRepository.CleanupJob job : outboxRepository.claimDue(20)) {
            if (outboxRepository.isReferenced(job.storedPath())) {
                outboxRepository.markCompleted(job.cleanupId(), "SKIPPED_STILL_REFERENCED");
                continue;
            }
            try {
                fileStoragePort.delete(job.storedPath());
                outboxRepository.markCompleted(job.cleanupId(), "DELETED");
            } catch (RuntimeException cleanupFailure) {
                outboxRepository.markFailed(job.cleanupId(), cleanupFailure.getClass().getSimpleName());
                logger.warn("S3 cleanup deferred ({})", cleanupFailure.getClass().getSimpleName());
            }
        }
    }
}