package com.autotest.test_management_service.application.productdescription;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionGateway.ContentCall;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionGateway.ContentResult;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionGateway.GatewayException;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

/** Runs the content stage and the PDF stage of a job off the request thread. Never logs document text. */
@Component
public class ProductDescriptionWorker {
    private static final Logger logger = LoggerFactory.getLogger(ProductDescriptionWorker.class);
    private static final String OUTPUT_FORMAT = "PDF";
    private static final String GENERATED_PREFIX = "generated/product-descriptions/";

    private final ProductDescriptionJobRepository jobs;
    private final ProductDescriptionGateway gateway;
    private final SubmissionPersistenceService submissions;
    private final FileStoragePort storage;
    private final ObjectMapper objectMapper;
    private final long heartbeatSeconds;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "product-description-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, Future<?>> running = new ConcurrentHashMap<>();

    public ProductDescriptionWorker(
            ProductDescriptionJobRepository jobs,
            ProductDescriptionGateway gateway,
            SubmissionPersistenceService submissions,
            FileStoragePort storage,
            ObjectMapper objectMapper,
            @Value("${product-description.worker-threads:2}") int workerThreads,
            @Value("${product-description.queue-capacity:20}") int queueCapacity,
            @Value("${product-description.heartbeat-seconds:5}") long heartbeatSeconds
    ) {
        this.jobs = jobs;
        this.gateway = gateway;
        this.submissions = submissions;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.heartbeatSeconds = heartbeatSeconds;
        this.executor = new ThreadPoolExecutor(workerThreads, workerThreads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity), task -> {
                    Thread thread = new Thread(task, "product-description-worker");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public void submit(ProductDescriptionJob job) {
        UUID jobId = job.jobId();
        int attempt = job.attempt();
        String stage = job.status();
        try {
            Future<?> future = executor.submit(() -> run(jobId, attempt));
            running.put(jobId, future);
            if (future.isDone()) {
                running.remove(jobId);
            }
        } catch (RejectedExecutionException rejected) {
            fail(jobId, attempt, stage, "QUEUE_FULL", "생성 요청이 많아 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /** Best-effort abort of the outbound request; a late result is still rejected by the status fence. */
    public void abort(UUID jobId) {
        Future<?> future = running.get(jobId);
        if (future != null) {
            future.cancel(true);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        heartbeats.shutdownNow();
    }

    void run(UUID jobId, int attempt) {
        Optional<ProductDescriptionJob> loaded = jobs.find(jobId);
        if (loaded.isEmpty() || loaded.get().attempt() != attempt || !loaded.get().active()) {
            running.remove(jobId);
            return;
        }
        ProductDescriptionJob job = loaded.get();
        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(
                () -> jobs.touch(jobId, attempt), heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
        String stage = job.status();
        try {
            JsonNode snapshot;
            JsonNode document;
            if (ProductDescriptionJob.GENERATING_CONTENT.equals(stage)) {
                ContentResult content = generateContent(job);
                if (content == null) {
                    return;
                }
                String snapshotJson = objectMapper.writeValueAsString(content.template());
                String documentJson = objectMapper.writeValueAsString(content.document());
                if (!jobs.markContentReady(jobId, attempt, snapshotJson, documentJson, content.mode(),
                        content.label(), content.templateId(), content.templateVersion())) {
                    return;
                }
                stage = ProductDescriptionJob.RENDERING;
                snapshot = content.template();
                document = content.document();
            } else {
                Optional<ProductDescriptionJobRepository.StoredContent> stored = jobs.loadContent(jobId);
                if (stored.isEmpty()) {
                    fail(jobId, attempt, stage, "CONTENT_MISSING", "저장된 문서 내용을 찾을 수 없습니다.");
                    return;
                }
                snapshot = objectMapper.readTree(stored.get().snapshotJson());
                document = objectMapper.readTree(stored.get().contentJson());
            }
            byte[] pdf = gateway.render(OUTPUT_FORMAT, document, snapshot.path("presentation"));
            String fileName = "product-description-" + jobId + ".pdf";
            StoredPath path = storage.storeUnderPrefix(GENERATED_PREFIX + job.submissionId(),
                    new ByteArrayInputStream(pdf), fileName, "application/pdf");
            if (!jobs.markCompleted(jobId, attempt, path.value(), fileName)) {
                // Canceled, interrupted, or superseded while rendering: the late file must never become a result.
                submissions.scheduleCleanup(path);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (GatewayException failure) {
            fail(jobId, attempt, stage, failure.code(), failure.getMessage());
        } catch (JsonProcessingException failure) {
            fail(jobId, attempt, stage, "CONTENT_INVALID", "생성된 문서 데이터를 처리하지 못했습니다.");
        } catch (RuntimeException failure) {
            logger.warn("Product description job {} failed ({})", jobId, failure.getClass().getSimpleName());
            fail(jobId, attempt, stage, "INTERNAL_ERROR", "문서 생성 중 내부 오류가 발생했습니다.");
        } finally {
            heartbeat.cancel(false);
            running.remove(jobId);
        }
    }

    private ContentResult generateContent(ProductDescriptionJob job) throws GatewayException, InterruptedException {
        List<SubmittedDocument> documents = submissions.findDocumentsBySubmissionId(SubmissionId.of(job.submissionId()));
        if (!job.inputDigest().equals(DocumentDigest.of(documents))) {
            fail(job.jobId(), job.attempt(), job.status(), "INPUT_CHANGED",
                    "검증 이후 문서가 변경되어 생성을 중단했습니다.");
            return null;
        }
        JsonNode warnings = preflightWarnings(job.submissionId());
        return gateway.generateContent(new ContentCall(
                job.submissionId(), submissions.findById(SubmissionId.of(job.submissionId())).orElseThrow()
                        .productId().value(),
                job.preflightDecision(), warnings,
                documents.stream().map(document -> new ProductDescriptionGateway.SourceDocument(
                        document.fileId(), document.role().name(), document.originalFilename(),
                        document.format().name(), document.extractedText())).toList()));
    }

    private JsonNode preflightWarnings(UUID submissionId) {
        try {
            return jobs.findDeliveryVerdict(submissionId)
                    .map(ProductDescriptionJobRepository.DeliveryVerdict::preflightJson)
                    .map(this::readTree)
                    .map(preflight -> preflight.path("warnings"))
                    .filter(JsonNode::isArray)
                    .orElseGet(objectMapper::createArrayNode);
        } catch (IllegalStateException unreadable) {
            return objectMapper.createArrayNode();
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored preflight result is not valid JSON", exception);
        }
    }

    private void fail(UUID jobId, int attempt, String stage, String code, String message) {
        String failedStatus = ProductDescriptionJob.RENDERING.equals(stage)
                ? ProductDescriptionJob.RENDER_FAILED : ProductDescriptionJob.CONTENT_FAILED;
        try {
            jobs.markFailed(jobId, attempt, stage, failedStatus, code, message);
        } catch (RuntimeException unrecorded) {
            // The lease recovery will close the job if even this write cannot be made.
            logger.warn("Product description job {} failure could not be recorded", jobId);
        }
    }
}
