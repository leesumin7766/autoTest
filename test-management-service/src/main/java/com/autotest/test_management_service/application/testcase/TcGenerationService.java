package com.autotest.test_management_service.application.testcase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionJobRepository;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PreDestroy;

/** TC drafts and explicit approvals only. Does not execute tests or assign P/F/N/A. Single TMS instance. */
@Service
public class TcGenerationService {
    private static final java.util.Set<String> SAFE_FAILURE_CODES = java.util.Set.of(
            "TC_INPUT_BUDGET_INSUFFICIENT", "TC_EVIDENCE_INVALID", "TC_SCHEMA_INVALID", "TC_NO_GROUNDED_CASES",
            "TC_PROVIDER_NOT_IMPLEMENTED", "TC_GENERATION_FAILED", "LLM_OUTPUT_INCOMPLETE", "LLM_INPUT_BUDGET_EXCEEDED",
            "LLM_PROVIDER_UNAVAILABLE", "LLM_RESPONSE_INVALID_JSON", "DOCUMENT_VERSION_MISMATCH", "INTERNAL_AUTH_NOT_CONFIGURED");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SubmissionRepository repository;
    private final SubmissionPersistenceService submissions;
    private final ProductDescriptionJobRepository verdicts;
    private final TransactionTemplate transactions;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), task -> daemon(task, "tc-generation"));
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "tc-heartbeat"));
    private final ScheduledExecutorService progress = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "tc-progress"));
    private final Map<UUID, Future<?>> running = new ConcurrentHashMap<>();
    private final String baseUrl;
    private final String internalToken;
    private final Duration timeout;

    public TcGenerationService(JdbcTemplate jdbc, ObjectMapper mapper, SubmissionRepository repository,
            SubmissionPersistenceService submissions, ProductDescriptionJobRepository verdicts,
            PlatformTransactionManager transactionManager,
            @Value("${ai-service.base-url:http://localhost:8005}") String baseUrl,
            @Value("${product-description.internal-token:}") String internalToken,
            @Value("${ai-service.product-description.content-timeout-seconds:21600}") long timeoutSeconds) {
        this.jdbc = jdbc; this.mapper = mapper; this.repository = repository;
        this.submissions = submissions; this.verdicts = verdicts;
        this.transactions = new TransactionTemplate(transactionManager);
        this.baseUrl = baseUrl.replaceAll("/+$", ""); this.internalToken = internalToken;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recover() {
        jdbc.update("UPDATE tc_generation_jobs SET status='FAILED', error_code='INTERRUPTED', updated_at=now() WHERE status='GENERATING'");
    }

    private void recoverStale() {
        jdbc.update("UPDATE tc_generation_jobs SET status='FAILED', error_code='INTERRUPTED', updated_at=now() "
                + "WHERE status='GENERATING' AND updated_at < now() - interval '60 seconds'");
    }

    private ProductDescriptionException error(HttpStatus status, String code, String message) {
        return new ProductDescriptionException(status, code, message);
    }

    private void authorize(UUID id, long memberId) {
        var submission = submissions.findById(SubmissionId.of(id))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "문서 세트를 찾을 수 없습니다."));
        if (!submission.memberId().value().equals(memberId)) {
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 문서 세트에 접근할 수 없습니다.");
        }
    }

    private record Input(String digest, ObjectNode body) { }

    private Input verifiedInput(UUID id, long memberId) {
        authorize(id, memberId);
        var submission = submissions.findById(SubmissionId.of(id)).orElseThrow();
        var docs = submissions.findDocumentsBySubmissionId(SubmissionId.of(id));
        if (docs.size() != 3 || docs.stream().map(d -> d.role()).distinct().count() != 3
                || docs.stream().anyMatch(d -> d.status() != SubmissionStatus.PARSED
                    || d.extractedText() == null || d.extractedText().isBlank())) {
            throw error(HttpStatus.CONFLICT, "DOCUMENTS_NOT_READY", "세 문서가 모두 정상 파싱되어야 합니다.");
        }
        var verdict = verdicts.findDeliveryVerdict(id).orElse(null);
        if (verdict == null || !"DELIVERED".equals(verdict.status())) {
            throw error(HttpStatus.CONFLICT, "PREFLIGHT_NOT_VERIFIED", "문서 검증이 통과되지 않았습니다.");
        }
        try {
            var preflight = mapper.readTree(verdict.preflightJson() == null ? "{}" : verdict.preflightJson());
            String decision = preflight.path("decision").asText();
            if (!("READY".equals(decision) || "READY_WITH_WARNINGS".equals(decision))
                    || (preflight.path("generationGate").has("allowed")
                        && !preflight.path("generationGate").path("allowed").asBoolean())) {
                throw error(HttpStatus.CONFLICT, "PREFLIGHT_BLOCKED", "문서 검증에서 생성이 차단되었습니다.");
            }
            String digest = DocumentDigest.of(docs);
            if (!digest.equals(verdict.documentDigest())) {
                throw error(HttpStatus.CONFLICT, "DOCUMENT_VERSION_MISMATCH", "현재 문서를 다시 검증해야 합니다.");
            }
            ObjectNode body = mapper.createObjectNode();
            body.put("submissionId", id.toString()); body.put("productId", submission.productId().value());
            body.put("decision", decision);
            body.set("warnings", preflight.path("warnings").isArray() ? preflight.path("warnings") : mapper.createArrayNode());
            var documents = body.putArray("documents");
            docs.forEach(d -> documents.addObject().put("fileId", d.fileId().toString()).put("role", d.role().name())
                    .put("originalFilename", d.originalFilename()).put("format", d.format().name()).put("extractedText", d.extractedText()));
            return new Input(digest, body);
        } catch (ProductDescriptionException known) { throw known;
        } catch (Exception invalid) {
            throw error(HttpStatus.CONFLICT, "PREFLIGHT_NOT_VERIFIED", "문서 검증 기록을 확인할 수 없습니다.");
        }
    }

    public JsonNode start(UUID id, long memberId, boolean approved) {
        if (!approved) throw error(HttpStatus.BAD_REQUEST, "APPROVAL_REQUIRED", "TC 생성 승인이 필요합니다.");
        recoverStale();
        final Input[] input = new Input[1];
        final boolean[] created = {false};
        UUID jobId;
        try {
            jobId = transactions.execute(tx -> {
                repository.findByIdForUpdate(SubmissionId.of(id))
                        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "문서 세트를 찾을 수 없습니다."));
                input[0] = verifiedInput(id, memberId);
                var existing = jdbc.queryForList("SELECT job_id FROM tc_generation_jobs WHERE submission_id=? AND input_digest=? "
                        + "AND status IN ('GENERATING','COMPLETED') ORDER BY approved_at DESC LIMIT 1", id, input[0].digest());
                if (!existing.isEmpty()) return (UUID) existing.get(0).get("job_id");
                UUID next = UUID.randomUUID();
                jdbc.update("INSERT INTO tc_generation_jobs(job_id,submission_id,member_id,input_digest,status) VALUES (?,?,?,?,'GENERATING')",
                        next, id, memberId, input[0].digest());
                created[0] = true; return next;
            });
        } catch (DuplicateKeyException conflict) {
            throw error(HttpStatus.CONFLICT, "TC_JOB_ALREADY_ACTIVE", "이미 TC를 생성 중입니다.");
        }
        if (created[0]) submit(jobId, id, memberId, input[0]);
        return status(id, memberId);
    }

    private HttpResponse<String> post(String path, JsonNode body, Duration limit) throws Exception {
        if (internalToken.isBlank()) throw new IllegalStateException("INTERNAL_AUTH_NOT_CONFIGURED");
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/test-cases/" + path)).timeout(limit)
                .header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void submit(UUID jobId, UUID submissionId, long memberId, Input input) {
        var pulse = heartbeat.scheduleAtFixedRate(() -> {
            try { jdbc.update("UPDATE tc_generation_jobs SET updated_at=now() WHERE job_id=? AND status='GENERATING'", jobId); }
            catch (RuntimeException ignored) { /* Stale recovery remains available. */ }
        }, 0, 5, TimeUnit.SECONDS);
        var poll = progress.scheduleAtFixedRate(() -> {
            try {
                var response = post("progress", input.body(), Duration.ofSeconds(10));
                if (response.statusCode() == 200) {
                    var counts = mapper.readTree(response.body());
                    jdbc.update("UPDATE tc_generation_jobs SET completed_chunks=?, total_chunks=? WHERE job_id=? AND status='GENERATING'",
                            counts.path("completedChunks").asInt(), counts.path("totalChunks").asInt(), jobId);
                }
            } catch (Exception ignored) { /* Progress is advisory. */ }
        }, 5, 5, TimeUnit.SECONDS);
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                var response = post("generate", input.body(), timeout);
                if (response.statusCode() != 200) {
                    String code = "TC_GENERATION_FAILED";
                    try {
                        String supplied = mapper.readTree(response.body()).path("code").asText();
                        if (SAFE_FAILURE_CODES.contains(supplied)) code = supplied;
                    } catch (Exception ignored) { /* Keep a safe error if the provider response is unreadable. */ }
                    throw new IllegalStateException(code);
                }
                JsonNode result = mapper.readTree(response.body());
                validateResult(result, submissionId, input.body());
                transactions.execute(tx -> {
                    repository.findByIdForUpdate(SubmissionId.of(submissionId)).orElseThrow();
                    if (!verifiedInput(submissionId, memberId).digest().equals(input.digest()))
                        throw new IllegalStateException("DOCUMENT_VERSION_MISMATCH");
                    jdbc.update("UPDATE tc_generation_jobs SET status='COMPLETED', result=?::jsonb, completed_chunks=?, total_chunks=?, updated_at=now() "
                            + "WHERE job_id=? AND status='GENERATING'", result.toString(),
                            result.path("completedChunks").asInt(), result.path("totalChunks").asInt(), jobId);
                    return null;
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); fail(jobId, "INTERRUPTED");
            } catch (Exception failure) { fail(jobId, SAFE_FAILURE_CODES.contains(failure.getMessage() == null ? "" : failure.getMessage())
                    ? failure.getMessage() : "TC_GENERATION_FAILED"); }
            return null;
        }) {
            @Override protected void done() { pulse.cancel(false); poll.cancel(true); running.remove(jobId, this); }
        };
        running.put(jobId, task);
        try { executor.execute(task); }
        catch (RuntimeException rejected) { task.cancel(false); fail(jobId, "QUEUE_FULL"); }
    }

    static void validateResult(JsonNode result, UUID submissionId, JsonNode input) {
        if (!submissionId.toString().equals(result.path("submissionId").asText())
                || !("REAL".equals(result.path("generationMode").asText()) || "MOCK".equals(result.path("generationMode").asText()))
                || !result.path("testCases").isArray() || result.path("testCases").isEmpty())
            throw new IllegalStateException("TC_RESPONSE_INVALID");
        var ids = new HashSet<String>();
        for (var tc : result.path("testCases")) {
            if (!tc.has("result") || !tc.path("result").isNull() || !tc.path("reviewRequired").asBoolean()
                    || !ids.add(tc.path("tcId").asText()) || tc.path("tcId").asText().isBlank()
                    || tc.path("expectedResult").asText().isBlank()
                    || !tc.path("featurePath").isArray() || tc.path("featurePath").isEmpty()
                    || !tc.path("steps").isArray() || tc.path("steps").isEmpty()
                    || !tc.path("sources").isArray() || tc.path("sources").isEmpty())
                throw new IllegalStateException("TC_RESPONSE_INVALID");
            for (var source : tc.path("sources")) {
                boolean matches = false;
                for (var doc : input.path("documents")) {
                    if (doc.path("fileId").asText().equals(source.path("fileId").asText())
                            && doc.path("role").asText().equals(source.path("role").asText())) {
                        String text = doc.path("extractedText").asText();
                        int start = source.path("characterStart").asInt(-1), end = source.path("characterEnd").asInt(-1);
                        matches = source.path("quote").asText().length() >= 8
                                && start >= 0 && end > start && end <= text.codePointCount(0, text.length())
                                && text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end))
                                    .equals(source.path("quote").asText());
                    }
                }
                if (!matches) throw new IllegalStateException("TC_EVIDENCE_INVALID");
            }
        }
    }

    private void fail(UUID id, String code) {
        jdbc.update("UPDATE tc_generation_jobs SET status='FAILED', error_code=?, updated_at=now() WHERE job_id=? AND status='GENERATING'", code, id);
    }

    public JsonNode status(UUID id, long memberId) {
        authorize(id, memberId); recoverStale();
        var rows = jdbc.query("SELECT * FROM tc_generation_jobs WHERE submission_id=? ORDER BY approved_at DESC LIMIT 1", (rs, n) -> {
            var job = mapper.createObjectNode();
            job.put("jobId", rs.getString("job_id")); job.put("status", rs.getString("status"));
            job.put("inputDigest", rs.getString("input_digest")); job.put("errorCode", rs.getString("error_code"));
            job.put("completedChunks", rs.getInt("completed_chunks")); job.put("totalChunks", rs.getInt("total_chunks"));
            job.put("approvedAt", rs.getTimestamp("approved_at").toInstant().toString());
            job.put("updatedAt", rs.getTimestamp("updated_at").toInstant().toString());
            try { job.set("output", rs.getString("result") == null ? mapper.nullNode() : mapper.readTree(rs.getString("result"))); }
            catch (Exception invalid) { throw new IllegalStateException("TC_STORED_RESULT_INVALID"); }
            return job;
        }, id);
        ObjectNode view = mapper.createObjectNode(); view.set("job", rows.isEmpty() ? mapper.nullNode() : rows.get(0)); return view;
    }

    public JsonNode cancel(UUID id, UUID jobId, long memberId) {
        authorize(id, memberId);
        if (jdbc.update("UPDATE tc_generation_jobs SET status='CANCELED', updated_at=now() WHERE job_id=? AND submission_id=? AND status='GENERATING'",
                jobId, id) > 0) {
            Future<?> task = running.get(jobId); if (task != null) task.cancel(true);
        }
        return status(id, memberId);
    }

    public JsonNode storedJob(UUID id, UUID jobId, long memberId) {
        authorize(id, memberId);
        var rows = jdbc.query("SELECT job_id,status,result,approved_at FROM tc_generation_jobs WHERE submission_id=? AND job_id=?",
                (rs, n) -> {
                    var job = mapper.createObjectNode(); job.put("jobId", rs.getString("job_id"));
                    job.put("status", rs.getString("status")); job.put("approvedAt", rs.getTimestamp("approved_at").toInstant().toString());
                    try { job.set("output", rs.getString("result") == null ? mapper.nullNode() : mapper.readTree(rs.getString("result"))); }
                    catch (Exception invalid) { throw new IllegalStateException("TC_STORED_RESULT_INVALID"); }
                    return job;
                }, id, jobId);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "TC_JOB_NOT_FOUND", "TC 작업을 찾을 수 없습니다.");
        return rows.get(0);
    }

    @PreDestroy void shutdown() { executor.shutdownNow(); heartbeat.shutdownNow(); progress.shutdownNow(); }
}
