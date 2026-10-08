package com.autotest.test_management_service.application.productdescription;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionJobRepository.DeliveryVerdict;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmissionType;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProductDescriptionService {
    @Value("${product-description.generation-enabled:false}")
    private boolean generationEnabled;

    private void requireGenerationEnabled() {
        if (!generationEnabled) {
            throw new ProductDescriptionException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PRODUCT_DESCRIPTION_DISABLED", "제품 설명 생성은 품질 개선 전까지 비활성화되어 있습니다.");
        }
    }
    private static final Logger logger = LoggerFactory.getLogger(ProductDescriptionService.class);

    private final SubmissionRepository submissionRepository;
    private final SubmissionPersistenceService submissions;
    private final ProductDescriptionJobRepository jobs;
    private final ProductDescriptionWorker worker;
    private final FileStoragePort storage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final Duration lease;

    public ProductDescriptionService(
            SubmissionRepository submissionRepository,
            SubmissionPersistenceService submissions,
            ProductDescriptionJobRepository jobs,
            ProductDescriptionWorker worker,
            FileStoragePort storage,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${product-description.lease-seconds:30}") long leaseSeconds
    ) {
        this.submissionRepository = submissionRepository;
        this.submissions = submissions;
        this.jobs = jobs;
        this.worker = worker;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.lease = Duration.ofSeconds(leaseSeconds);
    }

    /** In-memory workers do not survive a restart, so any job still marked active at startup is interrupted. */
    @EventListener(ApplicationReadyEvent.class)
    void recoverAfterRestart() {
        int recovered = jobs.recoverAllActive();
        if (recovered > 0) {
            logger.warn("Closed {} interrupted product description job(s) after startup", recovered);
        }
    }

    public StartResult start(UUID submissionId, long memberId) {
        requireGenerationEnabled();
        jobs.recoverStale(lease);
        StartResult result;
        try {
            result = transactions.execute(status -> {
                Submission submission = lockOwned(submissionId, memberId);
                Optional<ProductDescriptionJob> active = jobs.findActive(submissionId);
                if (active.isPresent()) {
                    return new StartResult(active.get(), false);
                }
                Optional<ProductDescriptionJob> memberActive = jobs.findActiveForMember(memberId);
                if (memberActive.isPresent()) {
                    throw new ProductDescriptionException(HttpStatus.CONFLICT, "MEMBER_JOB_ALREADY_ACTIVE",
                            "이미 제품 설명 문서를 생성 중입니다. 진행 중인 작업을 마친 뒤 새 작업을 시작해 주세요.");
                }
                Verified verified = verify(submission);
                Optional<ProductDescriptionJob> resumable = jobs.findResumable(submissionId, memberId);
                if (resumable.isPresent() && resumable.get().inputDigest().equals(verified.digest())) {
                    ProductDescriptionJob previous = resumable.get();
                    jobs.resumeContent(previous.jobId()).orElseThrow();
                    return new StartResult(jobs.find(previous.jobId()).orElseThrow(), true);
                }
                UUID jobId = UUID.randomUUID();
                jobs.insert(jobId, submissionId, memberId, verified.digest(), verified.decision());
                return new StartResult(jobs.find(jobId).orElseThrow(), true);
            });
        } catch (DuplicateKeyException memberAlreadyRunning) {
            throw new ProductDescriptionException(HttpStatus.CONFLICT, "MEMBER_JOB_ALREADY_ACTIVE",
                    "이미 제품 설명 문서를 생성 중입니다. 진행 중인 작업을 마친 뒤 새 작업을 시작해 주세요.");
        }
        if (result.created()) {
            worker.submit(result.job());
        }
        return new StartResult(jobs.find(result.job().jobId()).orElse(result.job()), result.created());
    }

    public StatusView status(UUID submissionId, long memberId) {
        authorize(submissionId, memberId);
        jobs.recoverStale(lease);
        return view(submissionId);
    }

    public JobView activeForMember(long memberId) {
        jobs.recoverStale(lease);
        return jobs.findActiveForMember(memberId).map(JobView::from).orElse(null);
    }

    public StatusView cancel(UUID submissionId, UUID jobId, long memberId) {
        authorize(submissionId, memberId);
        ProductDescriptionJob job = ownedJob(submissionId, jobId);
        if (jobs.cancel(job.jobId())) {
            worker.abort(job.jobId());
        }
        return view(submissionId);
    }

    /** Re-runs only the output stage from the stored content; the LLM is not called again. */
    public StartResult rerender(UUID submissionId, UUID jobId, long memberId) {
        requireGenerationEnabled();
        authorize(submissionId, memberId);
        jobs.recoverStale(lease);
        ProductDescriptionJob job = ownedJob(submissionId, jobId);
        ProductDescriptionJob reopened = transactions.execute(status -> {
            lockOwned(submissionId, memberId);
            try {
                return jobs.beginRerender(job.jobId()).flatMap(attempt -> jobs.find(job.jobId())).orElse(null);
            } catch (DuplicateKeyException activeJobExists) {
                throw new ProductDescriptionException(HttpStatus.CONFLICT, "JOB_ALREADY_ACTIVE",
                        "이미 진행 중인 생성 작업이 있습니다.");
            }
        });
        if (reopened == null) {
            throw new ProductDescriptionException(HttpStatus.CONFLICT, "NOT_RERENDERABLE",
                    "PDF 출력 실패 상태의 작업만 다시 출력할 수 있습니다.");
        }
        worker.submit(reopened);
        return new StartResult(jobs.find(reopened.jobId()).orElse(reopened), true);
    }

    public Download download(UUID submissionId, UUID jobId, long memberId) {
        authorize(submissionId, memberId);
        ProductDescriptionJob job = ownedJob(submissionId, jobId);
        if (!ProductDescriptionJob.COMPLETED.equals(job.status()) || job.outputPath() == null) {
            throw new ProductDescriptionException(HttpStatus.CONFLICT, "NOT_COMPLETED",
                    "완료된 문서만 다운로드할 수 있습니다.");
        }
        try (InputStream stream = storage.load(new StoredPath(job.outputPath()))) {
            return new Download(stream.readAllBytes(), job.outputFilename());
        } catch (IOException | RuntimeException failure) {
            logger.warn("Generated product description {} could not be read", jobId);
            throw new ProductDescriptionException(HttpStatus.INTERNAL_SERVER_ERROR, "OUTPUT_UNAVAILABLE",
                    "생성된 문서를 읽지 못했습니다.");
        }
    }

    private StatusView view(UUID submissionId) {
        Optional<ProductDescriptionJob> latest = jobs.findLatest(submissionId);
        Optional<ProductDescriptionJob> completed = latest.filter(job -> ProductDescriptionJob.COMPLETED.equals(job.status()))
                .or(() -> jobs.findLatestCompleted(submissionId));
        return new StatusView(latest.map(JobView::from).orElse(null), completed.map(JobView::from).orElse(null));
    }

    private ProductDescriptionJob ownedJob(UUID submissionId, UUID jobId) {
        return jobs.find(jobId).filter(job -> job.submissionId().equals(submissionId))
                .orElseThrow(() -> new ProductDescriptionException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND",
                        "생성 작업을 찾을 수 없습니다."));
    }

    private Submission authorize(UUID submissionId, long memberId) {
        Submission submission = submissions.findById(SubmissionId.of(submissionId))
                .orElseThrow(() -> new ProductDescriptionException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                        "문서 세트를 찾을 수 없습니다."));
        requireOwner(submission, memberId);
        return submission;
    }

    private Submission lockOwned(UUID submissionId, long memberId) {
        Submission submission = submissionRepository.findByIdForUpdate(SubmissionId.of(submissionId))
                .orElseThrow(() -> new ProductDescriptionException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                        "문서 세트를 찾을 수 없습니다."));
        requireOwner(submission, memberId);
        return submission;
    }

    private void requireOwner(Submission submission, long memberId) {
        if (!submission.memberId().value().equals(memberId)) {
            throw new ProductDescriptionException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 문서 세트에 접근할 수 없습니다.");
        }
    }

    /** The server decides eligibility from stored state only; nothing the client sends is trusted. */
    private Verified verify(Submission submission) {
        List<SubmittedDocument> documents = submissions.findDocumentsBySubmissionId(submission.submissionId());
        EnumSet<SubmissionType> roles = EnumSet.noneOf(SubmissionType.class);
        boolean ready = documents.size() == 3;
        for (SubmittedDocument document : documents) {
            ready &= roles.add(document.role()) && document.status() == SubmissionStatus.PARSED
                    && document.extractedText() != null && !document.extractedText().isBlank();
        }
        if (!ready || roles.size() != 3) {
            throw new ProductDescriptionException(HttpStatus.CONFLICT, "DOCUMENTS_NOT_READY",
                    "세 문서가 모두 정상 파싱된 뒤에 생성할 수 있습니다.");
        }
        DeliveryVerdict verdict = jobs.findDeliveryVerdict(submission.submissionId().value()).orElse(null);
        String deliveryStatus = verdict == null ? "NOT_READY" : verdict.status();
        switch (deliveryStatus) {
            case "PENDING" -> throw new ProductDescriptionException(HttpStatus.CONFLICT, "PREFLIGHT_IN_PROGRESS",
                    "문서 검증이 진행 중입니다.");
            case "BLOCKED" -> throw new ProductDescriptionException(HttpStatus.CONFLICT, "PREFLIGHT_BLOCKED",
                    "문서 검증에서 차단되어 생성할 수 없습니다.");
            case "DELIVERED" -> { }
            default -> throw new ProductDescriptionException(HttpStatus.CONFLICT, "PREFLIGHT_NOT_VERIFIED",
                    "문서 검증이 완료되지 않았습니다.");
        }
        String decision = decisionOf(verdict.preflightJson());
        if (!"READY".equals(decision) && !"READY_WITH_WARNINGS".equals(decision)) {
            throw new ProductDescriptionException(HttpStatus.CONFLICT, "PREFLIGHT_BLOCKED",
                    "문서 검증 결과가 생성 조건을 만족하지 않습니다.");
        }
        String digest = DocumentDigest.of(documents);
        if (!digest.equals(verdict.documentDigest())) {
            throw new ProductDescriptionException(HttpStatus.CONFLICT, "DOCUMENT_VERSION_MISMATCH",
                    "검증된 문서와 현재 문서가 일치하지 않습니다. 문서를 다시 검증해 주세요.");
        }
        return new Verified(decision, digest);
    }

    private String decisionOf(String preflightJson) {
        if (preflightJson == null) {
            return "";
        }
        try {
            return objectMapper.readTree(preflightJson).path("decision").asText("");
        } catch (JsonProcessingException unreadable) {
            return "";
        }
    }

    private record Verified(String decision, String digest) {
    }

    public record StartResult(ProductDescriptionJob job, boolean created) {
    }

    public record Download(byte[] content, String filename) {
    }

    public record StatusView(JobView job, JobView lastCompleted) {
    }

    public record JobView(UUID jobId, String status, String stage, boolean active, boolean resumable,
                          int completedChunks, int totalChunks, String errorCode,
                          String errorMessage, String generationMode, String generationLabel, String templateId,
                          String templateVersion, String outputFormat, boolean downloadable, boolean canRerender,
                          Instant createdAt, Instant updatedAt, Instant completedAt) {
        public static JobView from(ProductDescriptionJob job) {
            String stage = switch (job.status()) {
                case ProductDescriptionJob.GENERATING_CONTENT -> "CONTENT";
                case ProductDescriptionJob.RENDERING -> "OUTPUT";
                default -> "DONE";
            };
            return new JobView(job.jobId(), job.status(), stage, job.active(), job.resumable(),
                    job.completedChunks(), job.totalChunks(), job.errorCode(), job.errorMessage(),
                    job.generationMode(), job.generationLabel(), job.templateId(), job.templateVersion(),
                    job.outputFormat(), ProductDescriptionJob.COMPLETED.equals(job.status()),
                    ProductDescriptionJob.RENDER_FAILED.equals(job.status()) && job.hasContent(),
                    job.createdAt(), job.updatedAt(), job.completedAt());
        }
    }
}
