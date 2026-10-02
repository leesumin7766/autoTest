package com.autotest.test_management_service.application.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmissionType;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.submission.SubmittedDocumentRepository;
import com.autotest.test_management_service.infrastructure.persistence.S3CleanupOutboxRepository;

@Service
public class SubmissionPersistenceService {
    private static final List<SubmissionType> REQUIRED_ROLES = List.of(
            SubmissionType.AGREEMENT, SubmissionType.FUNCTION_LIST, SubmissionType.MANUAL);

    private final SubmissionRepository submissionRepository;
    private final SubmittedDocumentRepository submittedDocumentRepository;
    private final S3CleanupOutboxRepository cleanupOutboxRepository;
    private final JdbcTemplate jdbcTemplate;

    public SubmissionPersistenceService(
            SubmissionRepository submissionRepository,
            SubmittedDocumentRepository submittedDocumentRepository,
            S3CleanupOutboxRepository cleanupOutboxRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.submissionRepository = submissionRepository;
        this.submittedDocumentRepository = submittedDocumentRepository;
        this.cleanupOutboxRepository = cleanupOutboxRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Submission saveUploaded(Submission submission) {
        return submissionRepository.save(submission);
    }

    @Transactional(readOnly = true)
    public Optional<Submission> findById(SubmissionId id) {
        return submissionRepository.findById(id);
    }

    @Transactional
    public SubmittedDocument saveDocument(SubmittedDocument document) {
        SubmittedDocument saved = submittedDocumentRepository.save(document);
        refreshSetStatus(document.submissionId());
        return saved;
    }

    @Transactional
        public SubmittedDocument replaceFailedDocument(
            UUID previousFileId,
            StoredPath previousStoredPath,
            SubmittedDocument replacement
        ) {
            Submission submission = submissionRepository.findByIdForUpdate(replacement.submissionId())
                    .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
            if (hasStartedAiDelivery(replacement.submissionId())) {
                throw new IllegalStateException("Document replacement is closed after AI delivery starts");
            }
        if (!submittedDocumentRepository.replaceFailedDocument(previousFileId, replacement)) {
            throw new IllegalStateException("Failed document changed before replacement");
        }
            refreshSetStatus(submission, replacement.submissionId());
        cleanupOutboxRepository.schedule(previousStoredPath);
        return replacement;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleCleanup(StoredPath storedPath) {
        cleanupOutboxRepository.schedule(storedPath);
    }

    @Transactional(readOnly = true)
    public List<SubmittedDocument> findDocumentsBySubmissionId(SubmissionId id) {
        return submittedDocumentRepository.findBySubmissionId(id);
    }

    @Transactional(readOnly = true)
    public boolean hasDocumentForRole(SubmissionId id, SubmissionType role) {
        return submittedDocumentRepository.existsBySubmissionIdAndRole(id, role);
    }

    @Transactional
    public Submission refreshSetStatus(SubmissionId id) {
        Submission submission = submissionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        return refreshSetStatus(submission, id);
    }

    @Transactional
    public Optional<DeliveryClaim> claimAiDelivery(SubmissionId id) {
        Submission submission = submissionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        List<SubmittedDocument> documents = submittedDocumentRepository.findBySubmissionId(id);
        if (!hasAllReadyDocuments(documents)) {
            return Optional.empty();
        }
        jdbcTemplate.update("""
                INSERT INTO submission_ai_deliveries (submission_id, status, attempt_count, updated_at)
                VALUES (?, 'NOT_READY', 0, now())
                ON CONFLICT (submission_id) DO NOTHING
                """, id.value());
        List<Integer> attempts = jdbcTemplate.query("""
                UPDATE submission_ai_deliveries
                SET status = 'PENDING', attempt_count = attempt_count + 1, last_error = NULL, updated_at = now()
                WHERE submission_id = ? AND status <> 'DELIVERED'
                  AND (status <> 'PENDING' OR updated_at < now() - interval '30 seconds')
                RETURNING attempt_count
                """, (resultSet, rowNumber) -> resultSet.getInt(1), id.value());
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DeliveryClaim(submission, documents, attempts.getFirst()));
    }

    private Submission refreshSetStatus(Submission submission, SubmissionId id) {
        List<SubmittedDocument> documents = submittedDocumentRepository.findBySubmissionId(id);
        EnumSet<SubmissionType> parsedRoles = EnumSet.noneOf(SubmissionType.class);
        String failureReason = null;
        boolean failed = false;
        for (SubmittedDocument document : documents) {
            if (document.status() == SubmissionStatus.FAILED) {
                failed = true;
                if (failureReason == null) {
                    failureReason = document.failureReason();
                }
            } else if (document.status() == SubmissionStatus.PARSED
                    && document.extractedText() != null && !document.extractedText().isBlank()) {
                parsedRoles.add(document.role());
            }
        }
        SubmissionStatus status = failed ? SubmissionStatus.FAILED
                : parsedRoles.containsAll(REQUIRED_ROLES) ? SubmissionStatus.PARSED : SubmissionStatus.UPLOADED;
        return submissionRepository.save(submission.withProcessingStatus(status, failureReason));
    }

    private boolean hasAllReadyDocuments(List<SubmittedDocument> documents) {
        if (documents.size() != REQUIRED_ROLES.size()) {
            return false;
        }
        EnumSet<SubmissionType> roles = EnumSet.noneOf(SubmissionType.class);
        for (SubmittedDocument document : documents) {
            if (!REQUIRED_ROLES.contains(document.role()) || !roles.add(document.role())
                    || document.status() != SubmissionStatus.PARSED
                    || document.extractedText() == null || document.extractedText().isBlank()) {
                return false;
            }
        }
        return roles.containsAll(REQUIRED_ROLES);
    }

    private boolean hasStartedAiDelivery(SubmissionId id) {
        Boolean started = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM submission_ai_deliveries
                    WHERE submission_id = ? AND (attempt_count > 0 OR status IN ('PENDING', 'DELIVERED'))
                )
                """, Boolean.class, id.value());
        return Boolean.TRUE.equals(started);
    }

    public record DeliveryClaim(Submission submission, List<SubmittedDocument> documents, int attempt) {
        public DeliveryClaim {
            documents = List.copyOf(documents);
        }
    }
}