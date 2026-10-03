package com.autotest.test_management_service.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.autotest.test_management_service.domain.submission.Submission;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmittedDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiDocumentDeliveryService {
    private static final Logger logger = LoggerFactory.getLogger(AiDocumentDeliveryService.class);
    private static final Duration PENDING_TIMEOUT = SubmissionPersistenceService.AI_DELIVERY_LEASE;
    // Must stay above the ai-service Jev deadline (13s) and below the lease.
    static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration HTTP_READ_TIMEOUT = Duration.ofSeconds(20);
        private static final int REQUIRED_DOCUMENT_COUNT = 3;

        private final SubmissionPersistenceService submissionPersistenceService;
    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiDocumentDeliveryService(
            SubmissionPersistenceService submissionPersistenceService,
            JdbcTemplate jdbcTemplate,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${ai-service.base-url:http://localhost:8005}") String aiServiceBaseUrl
    ) {
        this.submissionPersistenceService = submissionPersistenceService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(HTTP_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(HTTP_READ_TIMEOUT);
        this.restClient = restClientBuilder.baseUrl(aiServiceBaseUrl).requestFactory(requestFactory).build();
    }

    public DeliveryResult deliverIfReady(SubmissionId submissionId) {
        SubmissionPersistenceService.DeliveryClaim claim = submissionPersistenceService
                .claimAiDelivery(submissionId).orElse(null);
        if (claim == null) {
            return getStatus(submissionId.value());
        }
        Submission submission = claim.submission();
        List<SubmittedDocument> documents = claim.documents();
        int attempt = claim.attempt();

        AiDocumentRequest request = new AiDocumentRequest(
                submissionId.value(), submission.productId().value(),
                documents.stream().map(AiDocument::from).toList());
        try {
            byte[] requestBody = objectMapper.writeValueAsBytes(request);
            AiDocumentReceipt receipt = restClient.post()
                    .uri("/api/v1/document-intakes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                    .retrieve()
                    .body(AiDocumentReceipt.class);
            if (receipt != null && receipt.blocked()) {
                recordBlocked(submissionId, attempt, receipt);
                return getStatus(submissionId.value());
            }
            if (receipt == null || !receipt.accepted()
                    || !Objects.equals(receipt.submissionId(), submissionId.value())
                    || receipt.documentCount() != REQUIRED_DOCUMENT_COUNT
                    || receipt.preflight() == null
                    || "BLOCKED".equals(receipt.preflight().path("decision").asText())) {
                throw new IllegalStateException("AI service did not confirm document intake");
            }
            jdbcTemplate.update("""
                    UPDATE submission_ai_deliveries
                    SET preflight_result = ?::jsonb, verified_document_digest = ?
                    WHERE submission_id = ? AND status = 'PENDING' AND attempt_count = ?
                    """, objectMapper.writeValueAsString(receipt.preflight()), DocumentDigest.of(documents),
                    submissionId.value(), attempt);
            jdbcTemplate.update("""
                    UPDATE submission_ai_deliveries
                    SET status = 'DELIVERED', last_error = NULL, delivered_at = now(), updated_at = now()
                    WHERE submission_id = ? AND status = 'PENDING' AND attempt_count = ?
                    """, submissionId.value(), attempt);
        } catch (RestClientException | IllegalStateException deliveryFailure) {
            logger.warn("AI document delivery failed ({})", deliveryFailure.getClass().getSimpleName());
            jdbcTemplate.update("""
                    UPDATE submission_ai_deliveries
                    SET status = 'FAILED', last_error = 'AI service delivery failed', updated_at = now()
                    WHERE submission_id = ? AND status = 'PENDING' AND attempt_count = ?
                    """, submissionId.value(), attempt);
        } catch (JsonProcessingException serializationFailure) {
            logger.warn("AI document delivery request could not be serialized");
            jdbcTemplate.update("""
                    UPDATE submission_ai_deliveries
                    SET status = 'FAILED', last_error = 'AI service delivery failed', updated_at = now()
                    WHERE submission_id = ? AND status = 'PENDING' AND attempt_count = ?
                    """, submissionId.value(), attempt);
        }
        return getStatus(submissionId.value());
    }

    public DeliveryResult getStatus(UUID submissionId) {
        List<DeliveryResult> results = jdbcTemplate.query("""
                SELECT status, attempt_count, last_error, delivered_at, updated_at, preflight_result::text
                FROM submission_ai_deliveries WHERE submission_id = ?
                """, (resultSet, rowNumber) -> new DeliveryResult(
                resultSet.getString("status"),
                resultSet.getInt("attempt_count"),
                resultSet.getString("last_error"),
                resultSet.getTimestamp("delivered_at") == null
                    ? null : resultSet.getTimestamp("delivered_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(), false,
                parsePreflight(resultSet.getString("preflight_result"))), submissionId);
            if (results.isEmpty()) {
                return new DeliveryResult("NOT_READY", 0, null, null, null, false);
            }
            DeliveryResult result = results.getFirst();
            boolean retryable = "FAILED".equals(result.status())
                || ("PENDING".equals(result.status()) && result.updatedAt() != null
                && result.updatedAt().isBefore(Instant.now().minus(PENDING_TIMEOUT)));
            return new DeliveryResult(result.status(), result.attempts(), result.lastError(), result.deliveredAt(),
                result.updatedAt(), retryable, result.preflight());
    }

    public record DeliveryResult(
            String status,
            int attempts,
            String lastError,
            Instant deliveredAt,
            Instant updatedAt,
            boolean retryable,
            JsonNode preflight
    ) {
        public DeliveryResult(String status, int attempts, String lastError, Instant deliveredAt,
                              Instant updatedAt, boolean retryable) {
            this(status, attempts, lastError, deliveredAt, updatedAt, retryable, null);
        }

        public DeliveryResult(String status, int attempts, String lastError, Instant deliveredAt) {
            this(status, attempts, lastError, deliveredAt, null, "FAILED".equals(status), null);
        }
    }

    public record AiDocumentRequest(UUID submissionId, Long productId, List<AiDocument> documents) {
    }

    public record AiDocument(UUID fileId, String role, String originalFilename, String format,
                             String storedPath, String extractedText) {
        private static AiDocument from(SubmittedDocument document) {
            return new AiDocument(document.fileId(), document.role().name(), document.originalFilename(),
                    document.format().name(), document.storedPath().value(), document.extractedText());
        }
    }

    public record AiDocumentReceipt(boolean accepted, boolean blocked, boolean duplicate, UUID submissionId,
                                    int documentCount, Instant receivedAt, JsonNode preflight) {
    }

    private void recordBlocked(SubmissionId submissionId, int attempt, AiDocumentReceipt receipt)
            throws JsonProcessingException {
        JsonNode preflight = receipt.preflight();
        if (receipt.accepted() || !Objects.equals(receipt.submissionId(), submissionId.value())
                || receipt.documentCount() != REQUIRED_DOCUMENT_COUNT || preflight == null
                || !"BLOCKED".equals(preflight.path("decision").asText())) {
            throw new IllegalStateException("AI service returned an inconsistent blocked receipt");
        }
        var reasons = objectMapper.createArrayNode();
        preflight.path("warnings").forEach(warning -> {
            if ("BLOCKER".equals(warning.path("severity").asText())) {
                reasons.add(warning);
            }
        });
        jdbcTemplate.update("""
                UPDATE submission_ai_deliveries
                SET status = 'BLOCKED', preflight_result = ?::jsonb, block_reasons = ?::jsonb,
                    blocked_at = now(), last_error = NULL, delivered_at = NULL, updated_at = now()
                WHERE submission_id = ? AND status = 'PENDING' AND attempt_count = ?
                """, objectMapper.writeValueAsString(preflight), objectMapper.writeValueAsString(reasons),
                submissionId.value(), attempt);
    }

    private JsonNode parsePreflight(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            logger.warn("Stored AI preflight result could not be parsed");
            return null;
        }
    }
}
