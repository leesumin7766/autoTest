package com.autotest.test_management_service.application.productdescription;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.autotest.test_management_service.Application;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionGateway.ContentCall;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionGateway.ContentResult;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionGateway.GatewayException;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "spring.jpa.hibernate.ddl-auto=validate",
                "s3.endpoint=http://localhost:8333",
                "s3.access-key=integration-test",
                "s3.secret-key=integration-test",
                "s3.enabled=false"
        }
)
@AutoConfigureMockMvc
class ProductDescriptionIntegrationTest {
    private static final long OWNER = 7001L;
    private static final long OTHER = 7002L;
    private static final byte[] PDF = "%PDF-1.4 integration".getBytes();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = System.getenv().getOrDefault(
                    "AUTOTEST_TEST_DATABASE_URL", "jdbc:postgresql://localhost:5432/autotest_test");
            if ("/autotest".equalsIgnoreCase(URI.create(url.substring("jdbc:".length())).getPath())) {
                throw new IllegalStateException("Integration tests must not use the autotest database");
            }
            return url;
        });
    }

    @Autowired private ProductDescriptionService service;
    @Autowired private ProductDescriptionJobRepository jobs;
    @Autowired private SubmissionPersistenceService persistence;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MockMvc mockMvc;

    @MockitoBean private FileStoragePort storage;
    @MockitoBean private ProductDescriptionGateway gateway;

    private final Map<String, byte[]> stored = new ConcurrentHashMap<>();
    private final List<UUID> submissionIds = new ArrayList<>();
    private volatile Callable<ContentResult> contentBehavior;
    private volatile Callable<byte[]> renderBehavior;
    private volatile String templateDigest;

    @BeforeEach
    void setUp() throws Exception {
        templateDigest = "digest-1";
        contentBehavior = this::cannedContent;
        renderBehavior = () -> PDF;
        when(gateway.generateContent(any())).thenAnswer(invocation -> {
            try {
                return contentBehavior.call();
            } catch (GatewayException | InterruptedException expected) {
                throw expected;
            }
        });
        when(gateway.render(anyString(), any(), any())).thenAnswer(invocation -> renderBehavior.call());
        when(storage.storeUnderPrefix(anyString(), any(), anyString(), anyString())).thenAnswer(invocation -> {
            String key = "integration:" + invocation.getArgument(0) + "/" + UUID.randomUUID() + "-"
                    + invocation.getArgument(2);
            stored.put(key, ((java.io.InputStream) invocation.getArgument(1)).readAllBytes());
            return new StoredPath(key);
        });
        when(storage.load(any())).thenAnswer(invocation -> {
            byte[] bytes = stored.get(((StoredPath) invocation.getArgument(0)).value());
            if (bytes == null) {
                throw new IllegalStateException("missing");
            }
            return new ByteArrayInputStream(bytes);
        });
    }

    @AfterEach
    void cleanUp() {
        submissionIds.forEach(id -> jdbc.update("DELETE FROM submissions WHERE submission_id = ?", id));
        jdbc.update("DELETE FROM s3_cleanup_outbox WHERE stored_path LIKE 'integration:%'");
    }

    private ContentResult cannedContent() throws Exception {
        JsonNode template = mapper.readTree("{\"manifest\":{\"templateId\":\"product-description\","
                + "\"version\":\"1.0.0\"},\"content\":{},\"presentation\":{\"marker\":\"" + templateDigest
                + "\"},\"digest\":\"" + templateDigest + "\"}");
        JsonNode document = mapper.readTree("{\"template\":{\"digest\":\"" + templateDigest + "\"},\"sections\":[]}");
        return new ContentResult(template, document, "MOCK", "개발용 모의 생성", "product-description", "1.0.0");
    }

    private UUID seed(String deliveryStatus, String decision, boolean withDigest) {
        UUID submissionId = UUID.randomUUID();
        submissionIds.add(submissionId);
        jdbc.update("INSERT INTO submissions (submission_id, member_id, product_id, status, uploaded_at) "
                + "VALUES (?, ?, 1, 'PARSED', now())", submissionId, OWNER);
        for (String role : List.of("AGREEMENT", "FUNCTION_LIST", "MANUAL")) {
            jdbc.update("""
                    INSERT INTO submission_files (file_id, submission_id, role, file_type, file_format,
                        original_filename, mime_type, stored_path, extracted_text, status, uploaded_at)
                    VALUES (?, ?, ?, 'PDF', 'PDF', ?, 'application/pdf', 's3://autotest-docs/x', ?, 'PARSED', now())
                    """, UUID.randomUUID(), submissionId, role, role + ".pdf", "text of " + role);
        }
        if (deliveryStatus != null) {
            String digest = withDigest
                    ? DocumentDigest.of(persistence.findDocumentsBySubmissionId(SubmissionId.of(submissionId))) : null;
            String preflight = "{\"decision\":\"" + decision + "\",\"warnings\":"
                    + "[{\"code\":\"DOCUMENT_PARTIALLY_READABLE\",\"role\":\"MANUAL\",\"severity\":\"WARNING\","
                    + "\"message\":\"m\"}]}";
            jdbc.update("""
                    INSERT INTO submission_ai_deliveries (submission_id, status, attempt_count, preflight_result,
                        verified_document_digest, updated_at)
                    VALUES (?, ?, 1, ?::jsonb, ?, now())
                    """, submissionId, deliveryStatus, preflight, digest);
        }
        return submissionId;
    }

    private UUID ready(String decision) {
        return seed("DELIVERED", decision, true);
    }

    private ProductDescriptionJob await(UUID jobId, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        ProductDescriptionJob job = jobs.find(jobId).orElseThrow();
        while (!expected.equals(job.status()) && System.nanoTime() < deadline) {
            Thread.sleep(25);
            job = jobs.find(jobId).orElseThrow();
        }
        assertEquals(expected, job.status());
        return job;
    }

    private int jobCount(UUID submissionId) {
        return jdbc.queryForObject("SELECT count(*) FROM product_description_jobs WHERE submission_id = ?",
                Integer.class, submissionId);
    }

    private static void rejects(String code, Runnable action) {
        ProductDescriptionException failure = assertThrows(ProductDescriptionException.class, action::run);
        assertEquals(code, failure.code());
    }

    @Test
    void generatesOnlyForReadyAndReadyWithWarnings() throws Exception {
        for (String decision : List.of("READY", "READY_WITH_WARNINGS")) {
            UUID submissionId = ready(decision);
            UUID jobId = service.start(submissionId, OWNER).job().jobId();
            ProductDescriptionJob job = await(jobId, "COMPLETED");
            assertEquals(decision, job.preflightDecision());
            assertTrue(job.outputPath().startsWith("integration:generated/product-descriptions/" + submissionId));
        }
        ArgumentCaptor<ContentCall> call = ArgumentCaptor.forClass(ContentCall.class);
        verify(gateway, times(2)).generateContent(call.capture());
        ContentCall last = call.getValue();
        assertEquals(3, last.documents().size());
        assertEquals("DOCUMENT_PARTIALLY_READABLE", last.warnings().get(0).path("code").asText());
    }

    @Test
    void rejectsBlockedUnverifiedAndInProgressWithoutCallingTheModel() throws Exception {
        rejects("PREFLIGHT_NOT_VERIFIED", () -> service.start(seed(null, null, false), OWNER));
        rejects("PREFLIGHT_NOT_VERIFIED", () -> service.start(seed("NOT_READY", "READY", true), OWNER));
        rejects("PREFLIGHT_NOT_VERIFIED", () -> service.start(seed("FAILED", "READY", true), OWNER));
        rejects("PREFLIGHT_IN_PROGRESS", () -> service.start(seed("PENDING", "READY", true), OWNER));
        rejects("PREFLIGHT_BLOCKED", () -> service.start(seed("BLOCKED", "BLOCKED", true), OWNER));
        rejects("PREFLIGHT_BLOCKED", () -> service.start(seed("DELIVERED", "BLOCKED", true), OWNER));
        rejects("DOCUMENT_VERSION_MISMATCH", () -> service.start(seed("DELIVERED", "READY", false), OWNER));
        verify(gateway, times(0)).generateContent(any());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM product_description_jobs WHERE member_id = ?",
                Integer.class, OWNER));
    }

    @Test
    void rejectsWhenDocumentsChangedAfterVerificationOrAreNotParsed() {
        UUID changed = ready("READY");
        jdbc.update("UPDATE submission_files SET extracted_text = 'edited' WHERE submission_id = ? AND role = 'MANUAL'",
                changed);
        rejects("DOCUMENT_VERSION_MISMATCH", () -> service.start(changed, OWNER));

        UUID failed = ready("READY");
        jdbc.update("UPDATE submission_files SET status = 'FAILED' WHERE submission_id = ? AND role = 'MANUAL'", failed);
        rejects("DOCUMENTS_NOT_READY", () -> service.start(failed, OWNER));
    }

    @Test
    void worksOnlyForTheSubmissionOwner() throws Exception {
        UUID submissionId = ready("READY");
        rejects("FORBIDDEN", () -> service.start(submissionId, OTHER));
        rejects("FORBIDDEN", () -> service.status(submissionId, OTHER));
        rejects("SUBMISSION_NOT_FOUND", () -> service.start(UUID.randomUUID(), OWNER));
        UUID jobId = service.start(submissionId, OWNER).job().jobId();
        await(jobId, "COMPLETED");
        rejects("FORBIDDEN", () -> service.cancel(submissionId, jobId, OTHER));
        rejects("FORBIDDEN", () -> service.download(submissionId, jobId, OTHER));
        rejects("JOB_NOT_FOUND", () -> service.download(ready("READY"), jobId, OWNER));
    }

    @Test
    void concurrentRequestsShareOneJobAndRunTheModelOnce() throws Exception {
        UUID submissionId = ready("READY");
        CountDownLatch release = new CountDownLatch(1);
        contentBehavior = () -> {
            release.await();
            return cannedContent();
        };
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<UUID>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> service.start(submissionId, OWNER).job().jobId()));
            }
            UUID first = results.get(0).get(10, TimeUnit.SECONDS);
            for (Future<UUID> result : results) {
                assertEquals(first, result.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, jobCount(submissionId));
            release.countDown();
            await(first, "COMPLETED");
        } finally {
            pool.shutdownNow();
        }
        verify(gateway, times(1)).generateContent(any());
    }

    @Test
    void cancelWinsOverAnUncancellableLateContentResult() throws Exception {
        UUID submissionId = ready("READY");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        contentBehavior = () -> {
            entered.countDown();
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException ignoredInterrupt) {
                    // An external request that cannot be aborted keeps running.
                }
            }
            return cannedContent();
        };
        UUID jobId = service.start(submissionId, OWNER).job().jobId();
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertEquals("CANCELED", service.cancel(submissionId, jobId, OWNER).job().status());
        release.countDown();
        Thread.sleep(500);
        ProductDescriptionJob job = jobs.find(jobId).orElseThrow();
        assertEquals("CANCELED", job.status());
        assertNull(job.outputPath());
        assertFalse(job.hasContent());
        verify(gateway, times(0)).render(anyString(), any(), any());
    }

    @Test
    void cancelAbortsAnInterruptibleRequest() throws Exception {
        UUID submissionId = ready("READY");
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        contentBehavior = () -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException abort) {
                interrupted.set(true);
                throw abort;
            }
            return cannedContent();
        };
        UUID jobId = service.start(submissionId, OWNER).job().jobId();
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        service.cancel(submissionId, jobId, OWNER);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!interrupted.get() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(interrupted.get());
        assertEquals("CANCELED", jobs.find(jobId).orElseThrow().status());
    }

    @Test
    void canceledRenderNeverBecomesACompletedResultAndItsFileIsScheduledForCleanup() throws Exception {
        UUID submissionId = ready("READY");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        renderBehavior = () -> {
            entered.countDown();
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException ignoredInterrupt) {
                    // Simulates a render call that finishes anyway.
                }
            }
            return PDF;
        };
        UUID jobId = service.start(submissionId, OWNER).job().jobId();
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        service.cancel(submissionId, jobId, OWNER);
        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int scheduled = 0;
        while (scheduled == 0 && System.nanoTime() < deadline) {
            Thread.sleep(25);
            scheduled = jdbc.queryForObject(
                    "SELECT count(*) FROM s3_cleanup_outbox WHERE stored_path LIKE ?", Integer.class,
                    "integration:generated/product-descriptions/" + submissionId + "%");
        }
        assertEquals(1, scheduled);
        ProductDescriptionJob job = jobs.find(jobId).orElseThrow();
        assertEquals("CANCELED", job.status());
        assertNull(job.outputPath());
        rejects("NOT_COMPLETED", () -> service.download(submissionId, jobId, OWNER));
    }

    @Test
    void renderFailureCanBeRetriedWithoutCallingTheModelAgain() throws Exception {
        UUID submissionId = ready("READY");
        AtomicBoolean fail = new AtomicBoolean(true);
        renderBehavior = () -> {
            if (fail.get()) {
                throw new GatewayException("RENDER_FAILED", "PDF 출력에 실패했습니다.");
            }
            return PDF;
        };
        UUID jobId = service.start(submissionId, OWNER).job().jobId();
        ProductDescriptionJob failed = await(jobId, "RENDER_FAILED");
        assertEquals("RENDER_FAILED", failed.errorCode());
        assertTrue(failed.hasContent());
        assertTrue(service.status(submissionId, OWNER).job().canRerender());
        rejects("NOT_COMPLETED", () -> service.download(submissionId, jobId, OWNER));

        fail.set(false);
        service.rerender(submissionId, jobId, OWNER);
        ProductDescriptionJob done = await(jobId, "COMPLETED");
        assertEquals(2, done.attempt());
        verify(gateway, times(1)).generateContent(any());
        verify(gateway, times(2)).render(anyString(), any(), any());
        assertArrayEquals(PDF, service.download(submissionId, jobId, OWNER).content());
        rejects("NOT_RERENDERABLE", () -> service.rerender(submissionId, jobId, OWNER));
    }

    @Test
    void contentFailureIsDistinctAndAllowsANewRequest() throws Exception {
        UUID submissionId = ready("READY");
        AtomicBoolean fail = new AtomicBoolean(true);
        contentBehavior = () -> {
            if (fail.get()) {
                throw new GatewayException("LLM_NOT_CONFIGURED", "설정 오류");
            }
            return cannedContent();
        };
        UUID failedId = service.start(submissionId, OWNER).job().jobId();
        ProductDescriptionJob failed = await(failedId, "CONTENT_FAILED");
        assertEquals("LLM_NOT_CONFIGURED", failed.errorCode());
        rejects("NOT_RERENDERABLE", () -> service.rerender(submissionId, failedId, OWNER));

        fail.set(false);
        UUID retryId = service.start(submissionId, OWNER).job().jobId();
        assertNotEquals(failedId, retryId);
        await(retryId, "COMPLETED");
    }

    @Test
    void completedPdfIsKeptAndNewGenerationUsesTheLatestTemplateSeparately() throws Exception {
        UUID submissionId = ready("READY");
        UUID first = service.start(submissionId, OWNER).job().jobId();
        ProductDescriptionJob firstDone = await(first, "COMPLETED");
        String firstSnapshot = jobs.loadContent(first).orElseThrow().snapshotJson();

        templateDigest = "digest-2";
        UUID second = service.start(submissionId, OWNER).job().jobId();
        ProductDescriptionJob secondDone = await(second, "COMPLETED");

        assertNotEquals(firstDone.outputPath(), secondDone.outputPath());
        assertEquals(firstSnapshot, jobs.loadContent(first).orElseThrow().snapshotJson());
        assertTrue(jobs.loadContent(second).orElseThrow().snapshotJson().contains("digest-2"));
        assertEquals(firstDone.outputPath(), jobs.find(first).orElseThrow().outputPath());
        assertArrayEquals(PDF, service.download(submissionId, first, OWNER).content());
        verify(gateway, times(2)).render(anyString(), any(), any());
        assertEquals(second, service.status(submissionId, OWNER).job().jobId());
    }

    @Test
    void restartMarksActiveJobsInterruptedWithoutLosingStoredContent() throws Exception {
        UUID contentStage = ready("READY");
        UUID outputStage = ready("READY");
        UUID contentJob = UUID.randomUUID();
        UUID outputJob = UUID.randomUUID();
        jobs.insert(contentJob, contentStage, OWNER, "d".repeat(64), "READY");
        jobs.insert(outputJob, outputStage, OWNER, "d".repeat(64), "READY");
        assertTrue(jobs.markContentReady(outputJob, 1, "{\"presentation\":{}}", "{}", "MOCK", "l", "t", "1"));

        service.recoverAfterRestart();

        assertEquals("CONTENT_FAILED", jobs.find(contentJob).orElseThrow().status());
        ProductDescriptionJob output = jobs.find(outputJob).orElseThrow();
        assertEquals("RENDER_FAILED", output.status());
        assertEquals("INTERRUPTED", output.errorCode());
        assertTrue(service.status(outputStage, OWNER).job().canRerender());
        assertFalse(service.status(contentStage, OWNER).job().active());
    }

    @Test
    void staleHeartbeatIsRecoveredOnStatusReadAndFreshOneIsNot() throws Exception {
        UUID stale = ready("READY");
        UUID fresh = ready("READY");
        UUID staleJob = UUID.randomUUID();
        UUID freshJob = UUID.randomUUID();
        jobs.insert(staleJob, stale, OWNER, "d".repeat(64), "READY");
        jobs.insert(freshJob, fresh, OWNER, "d".repeat(64), "READY");
        jdbc.update("UPDATE product_description_jobs SET updated_at = ? WHERE job_id = ?",
                Timestamp.from(Instant.now().minusSeconds(120)), staleJob);

        assertEquals("CONTENT_FAILED", service.status(stale, OWNER).job().status());
        assertEquals("GENERATING_CONTENT", service.status(fresh, OWNER).job().status());
    }

    @Test
    void downloadIsAvailableOnlyToTheOwnerForCompletedJobs() throws Exception {
        UUID submissionId = ready("READY");
        UUID jobId = service.start(submissionId, OWNER).job().jobId();
        await(jobId, "COMPLETED");
        String url = "/api/submissions/" + submissionId + "/product-description/" + jobId + "/download";

        mockMvc.perform(get(url).header("X-Member-Id", OTHER)).andExpect(status().isForbidden());
        mockMvc.perform(get(url)).andExpect(status().isBadRequest());
        mockMvc.perform(get(url).header("X-Member-Id", OWNER))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(PDF));

        UUID blocked = seed("BLOCKED", "BLOCKED", true);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/submissions/" + blocked + "/product-description").header("X-Member-Id", OWNER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREFLIGHT_BLOCKED"));
    }
}
