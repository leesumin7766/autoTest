package com.autotest.test_management_service.application.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.client.RestClient;

import com.autotest.test_management_service.domain.submission.FileFormat;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.StoredPath;
import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class AiDocumentDeliveryServiceTest {
    private final SubmissionId submissionId = SubmissionId.generate();
    private final ObjectMapper objectMapper = new ObjectMapper();
        private final SubmissionPersistenceService submissionPersistenceService = mock(SubmissionPersistenceService.class);
    private final DeliveryJdbcTemplate jdbcTemplate = new DeliveryJdbcTemplate(submissionId.value());
        private final Submission submission = Submission.reconstitute(submissionId, new MemberId(1L), new ProductId(8L),
            SubmissionStatus.UPLOADED, Instant.now());
    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String responseBody;
    private volatile int responseStatus;
    private CountDownLatch firstRequestReceived;
    private CountDownLatch releaseFirstRequest;
    private List<SubmittedDocument> documents;

    @BeforeEach
    void setUp() throws IOException {
        documents = documents(true);
        when(submissionPersistenceService.claimAiDelivery(submissionId))
            .thenAnswer(invocation -> jdbcTemplate.claim(submission, documents));
        responseBody = receipt(false, submissionId.value(), 3);
        responseStatus = 200;
        httpExecutor = Executors.newCachedThreadPool();
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.setExecutor(httpExecutor);
        httpServer.createContext("/api/v1/document-intakes", this::handleRequest);
        httpServer.start();
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
        }
    }

    @Test
    void doesNotSendWhenRequiredDocumentsAreNotReady() {
        documents = documents(false);

        AiDocumentDeliveryService.DeliveryResult result = service().deliverIfReady(submissionId);

        assertEquals("NOT_READY", result.status());
        assertEquals(0, result.attempts());
        assertEquals(0, requests.get());
        assertEquals(1, jdbcTemplate.claims());
    }

    @Test
    void acceptsMatchingReceiptAndSendsRoleSeparatedDocumentData() throws Exception {
        AiDocumentDeliveryService.DeliveryResult result = service().deliverIfReady(submissionId);

        assertEquals("DELIVERED", result.status());
        assertEquals(1, result.attempts());
        assertEquals(1, requests.get());
        JsonNode sent = objectMapper.readTree(httpServerRequestBody);
        assertEquals(submissionId.value().toString(), sent.path("submissionId").asText());
        assertEquals(3, sent.path("documents").size());
        assertEquals(Set.of("AGREEMENT", "FUNCTION_LIST", "MANUAL"), roles(sent));
        assertTrue(sent.path("documents").get(0).hasNonNull("fileId"));
        assertTrue(sent.path("documents").get(0).hasNonNull("extractedText"));
        assertEquals("READY_WITH_WARNINGS", result.preflight().path("decision").asText());
    }

    @Test
    void rejectsReceiptWithWrongSubmissionOrDocumentCount() {
        responseBody = receipt(false, UUID.randomUUID(), 3);

        AiDocumentDeliveryService.DeliveryResult wrongId = service().deliverIfReady(submissionId);

        assertEquals("FAILED", wrongId.status());
        assertEquals(1, wrongId.attempts());

        jdbcTemplate.resetForRetry();
        responseBody = receipt(false, submissionId.value(), 2);
        AiDocumentDeliveryService.DeliveryResult wrongCount = service().deliverIfReady(submissionId);

        assertEquals("FAILED", wrongCount.status());
        assertEquals(2, wrongCount.attempts());

        jdbcTemplate.resetForRetry();
        responseBody = "{\"accepted\":false,\"duplicate\":false,\"submissionId\":\""
            + submissionId.value() + "\",\"documentCount\":3}";
        AiDocumentDeliveryService.DeliveryResult rejected = service().deliverIfReady(submissionId);
        assertEquals("FAILED", rejected.status());
        assertEquals(3, rejected.attempts());
    }

    @Test
    void retriesAfterHttpFailureAndAcceptsDuplicateReceipt() {
        responseStatus = 503;
        responseBody = "{}";

        AiDocumentDeliveryService.DeliveryResult failed = service().deliverIfReady(submissionId);

        assertEquals("FAILED", failed.status());
        assertTrue(failed.retryable());

        responseStatus = 200;
        responseBody = receipt(true, submissionId.value(), 3);
        AiDocumentDeliveryService.DeliveryResult retried = service().deliverIfReady(submissionId);

        assertEquals("DELIVERED", retried.status());
        assertEquals(2, retried.attempts());
        assertEquals(2, requests.get());
    }

    @Test
    void allowsStalePendingRecoveryButDoesNotDuplicateFreshPending() {
        jdbcTemplate.setPendingAt(Instant.now());

        AiDocumentDeliveryService.DeliveryResult freshPending = service().deliverIfReady(submissionId);

        assertEquals("PENDING", freshPending.status());
        assertFalse(freshPending.retryable());
        assertEquals(0, requests.get());

        jdbcTemplate.setPendingAt(Instant.now().minusSeconds(46));
        assertTrue(service().getStatus(submissionId.value()).retryable());
        AiDocumentDeliveryService.DeliveryResult recovered = service().deliverIfReady(submissionId);

        assertEquals("DELIVERED", recovered.status());
        assertEquals(2, recovered.attempts());
        assertEquals(1, requests.get());
    }

    @Test
    void lateOldAttemptCannotOverwriteNewerDeliveredAttempt() throws Exception {
        firstRequestReceived = new CountDownLatch(1);
        releaseFirstRequest = new CountDownLatch(1);
        responseBody = receipt(false, submissionId.value(), 3);
        responseStatus = 200;
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<AiDocumentDeliveryService.DeliveryResult> first = callers.submit(
                    () -> service().deliverIfReady(submissionId));
            assertTrue(firstRequestReceived.await(5, TimeUnit.SECONDS));

            jdbcTemplate.setPendingAt(Instant.now().minusSeconds(46));
            AiDocumentDeliveryService.DeliveryResult second = service().deliverIfReady(submissionId);
            assertEquals("DELIVERED", second.status());
            assertEquals(2, second.attempts());

            releaseFirstRequest.countDown();
            AiDocumentDeliveryService.DeliveryResult firstResult = first.get(5, TimeUnit.SECONDS);
            assertEquals("DELIVERED", firstResult.status());
            assertEquals(2, firstResult.attempts());
            assertEquals(2, jdbcTemplate.attempts());
        } finally {
            releaseFirstRequest.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void storesBlockedDecisionWithReasonsAndDoesNotReclaimBlockedSubmission() {
        responseBody = "{\"accepted\":false,\"blocked\":true,\"duplicate\":false,\"submissionId\":\""
            + submissionId.value() + "\",\"documentCount\":3,\"receivedAt\":null,"
            + "\"preflight\":{\"decision\":\"BLOCKED\",\"warnings\":["
            + "{\"code\":\"PRODUCT_MISMATCH\",\"role\":null,\"severity\":\"BLOCKER\",\"message\":\"m\"},"
            + "{\"code\":\"SPARSE_EXTRACTED_TEXT\",\"role\":\"MANUAL\",\"severity\":\"WARNING\",\"message\":\"w\"}],"
            + "\"diagnostics\":{}}}";

        AiDocumentDeliveryService.DeliveryResult blocked = service().deliverIfReady(submissionId);

        assertEquals("BLOCKED", blocked.status());
        assertFalse(blocked.retryable());
        assertEquals("BLOCKED", blocked.preflight().path("decision").asText());
        assertEquals(1, jdbcTemplate.blockReasons().size());
        assertEquals("PRODUCT_MISMATCH", jdbcTemplate.blockReasons().get(0).path("code").asText());

        AiDocumentDeliveryService.DeliveryResult again = service().deliverIfReady(submissionId);

        assertEquals("BLOCKED", again.status());
        assertEquals(1, requests.get());
    }

    @Test
    void blockedDecisionOnAcceptedReceiptIsTreatedAsFailure() {
        responseBody = receipt(false, submissionId.value(), 3)
            .replace("READY_WITH_WARNINGS", "BLOCKED");

        assertEquals("FAILED", service().deliverIfReady(submissionId).status());
    }

    @Test
    void timeoutsAndLeaseDoNotOverlap() {
        assertTrue(AiDocumentDeliveryService.HTTP_CONNECT_TIMEOUT.plus(AiDocumentDeliveryService.HTTP_READ_TIMEOUT)
            .compareTo(SubmissionPersistenceService.AI_DELIVERY_LEASE) < 0);
        // ai-service: Jev deadline 13s < preflight wait 15s < TMS read 20s < ai lease 30s < TMS lease 45s.
        assertTrue(AiDocumentDeliveryService.HTTP_READ_TIMEOUT.toSeconds() > 15);
        assertTrue(AiDocumentDeliveryService.HTTP_READ_TIMEOUT.toSeconds() < 30);
        assertEquals(45, SubmissionPersistenceService.AI_DELIVERY_LEASE.toSeconds());
    }

    private AiDocumentDeliveryService service() {
        return new AiDocumentDeliveryService(submissionPersistenceService, jdbcTemplate, RestClient.builder(), objectMapper,
                "http://127.0.0.1:" + httpServer.getAddress().getPort());
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        byte[] request = exchange.getRequestBody().readAllBytes();
        httpServerRequestBody = new String(request, StandardCharsets.UTF_8);
        int requestNumber = requests.incrementAndGet();
        if (requestNumber == 1 && firstRequestReceived != null) {
            firstRequestReceived.countDown();
            try {
                releaseFirstRequest.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private String httpServerRequestBody;

    private String receipt(boolean duplicate, UUID id, int count) {
        return "{\"accepted\":true,\"duplicate\":" + duplicate + ",\"submissionId\":\"" + id
                + "\",\"documentCount\":" + count + ",\"receivedAt\":\"2026-10-02T00:00:00Z\","
                + "\"preflight\":{\"decision\":\"READY_WITH_WARNINGS\",\"warnings\":[],\"diagnostics\":{}}}";
    }

    private List<SubmittedDocument> documents(boolean ready) {
        return List.of(
                document(com.autotest.test_management_service.domain.submission.SubmissionType.AGREEMENT, ready),
                document(com.autotest.test_management_service.domain.submission.SubmissionType.FUNCTION_LIST, ready),
                document(com.autotest.test_management_service.domain.submission.SubmissionType.MANUAL, ready));
    }

    private SubmittedDocument document(
            com.autotest.test_management_service.domain.submission.SubmissionType role,
            boolean ready
    ) {
        return new SubmittedDocument(UUID.randomUUID(), submissionId, role,
                com.autotest.test_management_service.domain.vo.SubmissionType.WORD,
                FileFormat.DOCX, role.name().toLowerCase() + ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new StoredPath("s3://autotest-docs/" + role.name().toLowerCase() + ".docx"),
                ready ? "sample extracted content" : "", ready ? SubmissionStatus.PARSED : SubmissionStatus.FAILED,
                ready ? null : "parser failed", Instant.now());
    }

    private Set<String> roles(JsonNode request) {
        Set<String> roles = new HashSet<>();
        request.path("documents").forEach(document -> roles.add(document.path("role").asText()));
        return roles;
    }

    private static final class DeliveryJdbcTemplate extends JdbcTemplate {
        private final UUID submissionId;
        private String status = "NOT_READY";
        private String lastError;
        private Instant updatedAt = Instant.now();
        private Instant deliveredAt;
        private JsonNode preflight;
        private JsonNode blockReasons;
        private int attempts;
        private int claims;

        private DeliveryJdbcTemplate(UUID submissionId) {
            this.submissionId = submissionId;
        }

        @Override
        public int update(String sql, Object... arguments) {
            if (sql.contains("INSERT INTO submission_ai_deliveries")) {
                return 1;
            }
            if (sql.contains("SET preflight_result")) {
                UUID id = (UUID) arguments[1];
                int expectedAttempt = (Integer) arguments[2];
                if (submissionId.equals(id) && "PENDING".equals(status) && attempts == expectedAttempt) {
                    try {
                        preflight = new ObjectMapper().readTree((String) arguments[0]);
                    } catch (IOException exception) {
                        throw new IllegalArgumentException(exception);
                    }
                    return 1;
                }
                return 0;
            }
            String expectedStatus = sql.contains("SET status = 'DELIVERED'") ? "DELIVERED"
                    : sql.contains("SET status = 'BLOCKED'") ? "BLOCKED" : "FAILED";
            if ("BLOCKED".equals(expectedStatus)) {
                if (submissionId.equals(arguments[2]) && "PENDING".equals(status) && attempts == (Integer) arguments[3]) {
                    try {
                        preflight = new ObjectMapper().readTree((String) arguments[0]);
                        blockReasons = new ObjectMapper().readTree((String) arguments[1]);
                    } catch (IOException exception) {
                        throw new IllegalArgumentException(exception);
                    }
                    status = "BLOCKED";
                    lastError = null;
                    updatedAt = Instant.now();
                    return 1;
                }
                return 0;
            }
            int expectedAttempt = (Integer) arguments[1];
            if (submissionId.equals(arguments[0]) && "PENDING".equals(status) && attempts == expectedAttempt) {
                status = expectedStatus;
                lastError = "DELIVERED".equals(status) ? null : "AI service delivery failed";
                deliveredAt = "DELIVERED".equals(status) ? Instant.now() : deliveredAt;
                updatedAt = Instant.now();
                return 1;
            }
            return 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... arguments) {
            return (List<T>) List.of(new AiDocumentDeliveryService.DeliveryResult(
                    status, attempts, lastError, deliveredAt, updatedAt, false, preflight));
        }

        private java.util.Optional<SubmissionPersistenceService.DeliveryClaim> claim(
                Submission submission,
                List<SubmittedDocument> documents
        ) {
            claims++;
            if (documents.size() != 3 || documents.stream().anyMatch(document ->
                    document.status() != SubmissionStatus.PARSED || document.extractedText().isBlank())) {
                return java.util.Optional.empty();
            }
            boolean freshPending = "PENDING".equals(status)
                    && updatedAt.isAfter(Instant.now().minusSeconds(45));
            if ("DELIVERED".equals(status) || "BLOCKED".equals(status) || freshPending) {
                return java.util.Optional.empty();
            }
            status = "PENDING";
            attempts++;
            updatedAt = Instant.now();
            lastError = null;
            return java.util.Optional.of(new SubmissionPersistenceService.DeliveryClaim(
                    submission, documents, attempts));
        }

        private int claims() {
            return claims;
        }

        private JsonNode blockReasons() {
            return blockReasons;
        }

        private int attempts() {
            return attempts;
        }

        private void resetForRetry() {
            status = "FAILED";
            updatedAt = Instant.now();
        }

        private void setPendingAt(Instant instant) {
            status = "PENDING";
            attempts = Math.max(1, attempts);
            updatedAt = instant;
        }
    }
}
