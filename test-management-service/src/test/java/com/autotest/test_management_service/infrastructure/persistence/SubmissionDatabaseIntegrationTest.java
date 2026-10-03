package com.autotest.test_management_service.infrastructure.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.autotest.test_management_service.Application;
import com.autotest.test_management_service.application.service.AiDocumentDeliveryService;
import com.autotest.test_management_service.application.service.S3CleanupOutboxWorker;
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
class SubmissionDatabaseIntegrationTest {
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SubmissionDatabaseIntegrationTest::isolatedDatabaseUrl);
    }

    private static String isolatedDatabaseUrl() {
        String databaseUrl = System.getenv().getOrDefault(
                "AUTOTEST_TEST_DATABASE_URL", "jdbc:postgresql://localhost:5432/autotest_test");
        URI databaseUri = URI.create(databaseUrl.substring("jdbc:".length()));
        if ("/autotest".equalsIgnoreCase(databaseUri.getPath())) {
            throw new IllegalStateException("Integration tests must not use the autotest database");
        }
        return databaseUrl;
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private S3CleanupOutboxRepository cleanupOutboxRepository;

    @Autowired
    private SubmissionPersistenceService submissionPersistenceService;

    @MockitoBean
    private FileStoragePort fileStoragePort;

    @MockitoBean
    private AiDocumentDeliveryService aiDocumentDeliveryService;

    private final Map<String, byte[]> storedFiles = new ConcurrentHashMap<>();
    private final List<UUID> submissionIds = new ArrayList<>();
    private volatile CountDownLatch loadArrivals;
    private volatile CountDownLatch releaseLoads;

    @AfterEach
    void removeTestSubmissions() {
        submissionIds.forEach(id -> jdbcTemplate.update("DELETE FROM submissions WHERE submission_id = ?", id));
        jdbcTemplate.update("DELETE FROM s3_cleanup_outbox WHERE stored_path LIKE 'integration:%'");
        submissionIds.clear();
        storedFiles.clear();
        loadArrivals = null;
        releaseLoads = null;
    }

    @Test
    void commitsUploadedBeforeParsingAndPersistsParsedResult() throws Exception {
        stubStorage();
        byte[] workbook = createWorkbook(true);

        JsonNode postBody = upload("document.xlsx", workbook);
        UUID id = UUID.fromString(postBody.get("submissionId").asText());
        submissionIds.add(id);

        assertEquals("UPLOADED", postBody.get("status").asText());
        assertEquals("UPLOADED", jdbcTemplate.queryForObject(
                "SELECT status FROM submissions WHERE submission_id = ?", String.class, id));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM submission_ai_deliveries WHERE submission_id = ?", Integer.class, id));
        assertEquals("PARSED", jdbcTemplate.queryForObject(
            "SELECT status FROM submission_files WHERE submission_id = ?", String.class, id));
        String extractedText = jdbcTemplate.queryForObject(
            "SELECT extracted_text FROM submission_files WHERE submission_id = ?", String.class, id);
        assertNotNull(extractedText);
        assertEquals(true, extractedText.contains("Known spreadsheet body"));

        JsonNode getBody = objectMapper.readTree(mockMvc.perform(get("/api/submissions/{id}", id))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertEquals(id.toString(), getBody.get("submissionId").asText());
        assertEquals("UPLOADED", getBody.get("status").asText());
        assertFalse(getBody.hasNonNull("failureReason"));
    }

    @Test
    void persistsEmptyExcelFailureAndReturnsItFromPostAndGet() throws Exception {
        stubStorage();

        JsonNode postBody = upload("empty.xlsx", createWorkbook(false));
        UUID id = UUID.fromString(postBody.get("submissionId").asText());
        submissionIds.add(id);

        assertEquals("FAILED", postBody.get("status").asText());
        assertEquals("추출할 셀 내용이 없습니다", postBody.get("failureReason").asText());
        assertEquals("FAILED", jdbcTemplate.queryForObject(
                "SELECT status FROM submissions WHERE submission_id = ?", String.class, id));
        assertEquals("추출할 셀 내용이 없습니다", jdbcTemplate.queryForObject(
            "SELECT failure_reason FROM submission_files WHERE submission_id = ?", String.class, id));
        assertEquals("", jdbcTemplate.queryForObject(
            "SELECT extracted_text FROM submission_files WHERE submission_id = ?", String.class, id));

        JsonNode getBody = objectMapper.readTree(mockMvc.perform(get("/api/submissions/{id}", id))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertEquals("FAILED", getBody.get("status").asText());
        assertEquals("추출할 셀 내용이 없습니다", getBody.get("failureReason").asText());
    }

        @Test
        void storesThreeRoleDocumentsUnderOneSubmissionAndReturnsTheirDetails() throws Exception {
        stubStorage();
        byte[] workbook = createWorkbook(true);

        JsonNode firstResponse = upload("agreement.xlsx", workbook);
        UUID id = UUID.fromString(firstResponse.get("submissionId").asText());
        submissionIds.add(id);

        uploadAdditional(id, "FUNCTION_LIST", "functions.xlsx", workbook);
        uploadAdditional(id, "MANUAL", "manual.xlsx", workbook);

        assertEquals(3, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM submission_files WHERE submission_id = ?", Integer.class, id));
        assertEquals(3, jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT role) FROM submission_files WHERE submission_id = ?", Integer.class, id));
        assertEquals(3, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM submission_files WHERE submission_id = ? AND file_format = 'XLSX' AND extracted_text LIKE '%Known spreadsheet body%'",
            Integer.class, id));
        assertEquals("PARSED", jdbcTemplate.queryForObject(
            "SELECT status FROM submissions WHERE submission_id = ?", String.class, id));

        JsonNode getBody = objectMapper.readTree(mockMvc.perform(get("/api/submissions/{id}", id))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertEquals(3, getBody.get("documents").size());
        assertEquals("AGREEMENT", getBody.get("documents").get(0).get("role").asText());
        assertEquals("FUNCTION_LIST", getBody.get("documents").get(1).get("role").asText());
        assertEquals("MANUAL", getBody.get("documents").get(2).get("role").asText());
        assertEquals("EXCEL", getBody.get("documents").get(2).get("fileType").asText());
        assertTrue(getBody.get("documents").get(2).get("storedPath").asText().startsWith("integration:"));
        assertTrue(getBody.get("documents").get(2).get("extractedText").asText().contains("Known spreadsheet body"));
        }

        @Test
        void replacesFailedXlsxWithPdfAndRetriesDurableCleanupWithoutDeletingCurrentPath() throws Exception {
        stubStorage();
        JsonNode failed = upload("empty.xlsx", createWorkbook(false));
        UUID id = UUID.fromString(failed.get("submissionId").asText());
        submissionIds.add(id);
        StoredPath oldPath = new StoredPath(jdbcTemplate.queryForObject(
            "SELECT stored_path FROM submission_files WHERE submission_id = ?", String.class, id));
        when(aiDocumentDeliveryService.getStatus(id)).thenReturn(
            new AiDocumentDeliveryService.DeliveryResult("NOT_READY", 0, null, null));
        when(aiDocumentDeliveryService.deliverIfReady(SubmissionId.of(id))).thenReturn(
            new AiDocumentDeliveryService.DeliveryResult("NOT_READY", 0, null, null));

        byte[] pdf = createPdf("Recovered contract scope");
        int responseStatus = replace(id, "replacement.pdf", "application/pdf", pdf);
        assertEquals(200, responseStatus);

        Map<String, Object> document = jdbcTemplate.queryForMap(
            "SELECT file_type, file_format, original_filename, stored_path, extracted_text, status "
                + "FROM submission_files WHERE submission_id = ?", id);
        assertEquals("PDF", document.get("file_type"));
        assertEquals("PDF", document.get("file_format"));
        assertEquals("replacement.pdf", document.get("original_filename"));
        assertEquals("PARSED", document.get("status"));
        assertTrue(((String) document.get("extracted_text")).contains("Recovered contract scope"));
        assertEquals("UPLOADED", jdbcTemplate.queryForObject(
            "SELECT status FROM submissions WHERE submission_id = ?", String.class, id));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM s3_cleanup_outbox WHERE stored_path = ? AND completed_at IS NULL",
            Integer.class, oldPath.value()));
        StoredPath currentPath = new StoredPath((String) document.get("stored_path"));

        doThrow(new IllegalStateException("simulated object-store outage"))
            .doNothing().when(fileStoragePort).delete(oldPath);
        S3CleanupOutboxWorker firstWorker = new S3CleanupOutboxWorker(cleanupOutboxRepository, fileStoragePort);
        firstWorker.processPending();
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT attempts FROM s3_cleanup_outbox WHERE stored_path = ?", Integer.class, oldPath.value()));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM s3_cleanup_outbox WHERE stored_path = ? AND completed_at IS NOT NULL",
            Integer.class, oldPath.value()));

        jdbcTemplate.update("UPDATE s3_cleanup_outbox SET next_attempt_at = now() WHERE stored_path = ?", oldPath.value());
        new S3CleanupOutboxWorker(cleanupOutboxRepository, fileStoragePort).processPending();
        assertEquals("DELETED", jdbcTemplate.queryForObject(
            "SELECT completion_reason FROM s3_cleanup_outbox WHERE stored_path = ?", String.class, oldPath.value()));

        cleanupOutboxRepository.schedule(currentPath);
        new S3CleanupOutboxWorker(cleanupOutboxRepository, fileStoragePort).processPending();
        assertEquals("SKIPPED_STILL_REFERENCED", jdbcTemplate.queryForObject(
            "SELECT completion_reason FROM s3_cleanup_outbox WHERE stored_path = ?", String.class, currentPath.value()));
        org.mockito.Mockito.verify(fileStoragePort, org.mockito.Mockito.never()).delete(currentPath);
        org.mockito.Mockito.verify(fileStoragePort, org.mockito.Mockito.times(2)).delete(oldPath);
        }

    @Test
    void blockedSubmissionAllowsReplacementAfterFailedReplacementAndIsRevalidated() throws Exception {
        stubStorage();
        byte[] workbook = createWorkbook(true);
        UUID id = UUID.fromString(upload("agreement.xlsx", workbook).get("submissionId").asText());
        submissionIds.add(id);
        uploadAdditional(id, "FUNCTION_LIST", "functions.xlsx", workbook);
        uploadAdditional(id, "MANUAL", "manual.xlsx", workbook);
        SubmissionId submissionId = SubmissionId.of(id);

        assertTrue(submissionPersistenceService.claimAiDelivery(submissionId).isPresent());
        jdbcTemplate.update("""
                UPDATE submission_ai_deliveries
                SET status = 'BLOCKED', preflight_result = '{"decision":"BLOCKED"}'::jsonb,
                    block_reasons = '[{"code":"PRODUCT_MISMATCH"}]'::jsonb, blocked_at = now()
                WHERE submission_id = ?
                """, id);
        assertTrue(submissionPersistenceService.claimAiDelivery(submissionId).isEmpty());
        assertTrue(submissionPersistenceService.isAiDeliveryBlocked(submissionId));

        when(aiDocumentDeliveryService.getStatus(id)).thenReturn(
            new AiDocumentDeliveryService.DeliveryResult("BLOCKED", 1, null, null));
        int failedReplacement = replace(id, "empty.xlsx", XLSX_CONTENT_TYPE, createWorkbook(false));
        assertTrue(failedReplacement >= 400);
        assertEquals("agreement.xlsx", jdbcTemplate.queryForObject(
            "SELECT original_filename FROM submission_files WHERE submission_id = ? AND role = 'AGREEMENT'",
            String.class, id));
        assertEquals("BLOCKED", jdbcTemplate.queryForObject(
            "SELECT status FROM submission_ai_deliveries WHERE submission_id = ?", String.class, id));

        assertEquals(200, replace(id, "corrected.pdf", "application/pdf", createPdf("Corrected agreement")));
        assertEquals("corrected.pdf", jdbcTemplate.queryForObject(
            "SELECT original_filename FROM submission_files WHERE submission_id = ? AND role = 'AGREEMENT'",
            String.class, id));
        Map<String, Object> delivery = jdbcTemplate.queryForMap(
            "SELECT status, attempt_count, preflight_result, block_reasons, blocked_at "
                + "FROM submission_ai_deliveries WHERE submission_id = ?", id);
        assertEquals("NOT_READY", delivery.get("status"));
        assertEquals(1, delivery.get("attempt_count"));
        assertEquals(null, delivery.get("preflight_result"));
        assertEquals(null, delivery.get("block_reasons"));
        assertEquals(null, delivery.get("blocked_at"));

        assertEquals(2, submissionPersistenceService.claimAiDelivery(submissionId).orElseThrow().attempt());
    }

    @Test
    void replacementIsRejectedWhileDeliveryIsPendingOrDelivered() throws Exception {
        stubStorage();
        UUID id = UUID.fromString(upload("empty.xlsx", createWorkbook(false)).get("submissionId").asText());
        submissionIds.add(id);

        for (String closedStatus : List.of("PENDING", "DELIVERED")) {
            when(aiDocumentDeliveryService.getStatus(id)).thenReturn(
                new AiDocumentDeliveryService.DeliveryResult(closedStatus, 1, null, null));
            assertEquals(409, replace(id, "replacement.pdf", "application/pdf", createPdf("text")));
        }
    }

    @Test
    void concurrentReplacementOfSameFailedFileCommitsExactlyOneNewDocument() throws Exception {
        stubStorage();
        JsonNode failed = upload("empty.xlsx", createWorkbook(false));
        UUID id = UUID.fromString(failed.get("submissionId").asText());
        submissionIds.add(id);
        byte[] workbook = createWorkbook(true);
        uploadAdditional(id, "FUNCTION_LIST", "functions.xlsx", workbook);
        uploadAdditional(id, "MANUAL", "manual.xlsx", workbook);
        when(aiDocumentDeliveryService.getStatus(id)).thenReturn(
            new AiDocumentDeliveryService.DeliveryResult("NOT_READY", 0, null, null));
        when(aiDocumentDeliveryService.deliverIfReady(SubmissionId.of(id))).thenAnswer(invocation -> {
            java.util.Optional<SubmissionPersistenceService.DeliveryClaim> claim =
                submissionPersistenceService.claimAiDelivery(SubmissionId.of(id));
            return claim.map(deliveryClaim -> new AiDocumentDeliveryService.DeliveryResult(
                "PENDING", deliveryClaim.attempt(), null, null))
                .orElseGet(() -> new AiDocumentDeliveryService.DeliveryResult("NOT_READY", 0, null, null));
        });

        loadArrivals = new CountDownLatch(2);
        releaseLoads = new CountDownLatch(1);
        byte[] pdf = createPdf("Concurrent replacement");
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = callers.submit(() -> replaceSafely(id, "first.pdf", pdf));
            Future<Integer> second = callers.submit(() -> replaceSafely(id, "second.pdf", pdf));
            assertTrue(loadArrivals.await(5, TimeUnit.SECONDS));
            releaseLoads.countDown();
            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);

            assertEquals(1, List.of(firstStatus, secondStatus).stream().filter(status -> status == 200).count());
                assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM submission_files WHERE submission_id = ? AND status = 'PARSED'",
                Integer.class, id));
                assertEquals("PENDING", jdbcTemplate.queryForObject(
                    "SELECT status FROM submission_ai_deliveries WHERE submission_id = ?", String.class, id));
                assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT attempt_count FROM submission_ai_deliveries WHERE submission_id = ?", Integer.class, id));
            assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM s3_cleanup_outbox WHERE stored_path LIKE 'integration:%'",
                Integer.class));
        } finally {
            releaseLoads.countDown();
            callers.shutdownNow();
        }
        }

    private JsonNode upload(String filename, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, XLSX_CONTENT_TYPE, content);
        String response = mockMvc.perform(multipart("/api/submissions")
                        .file(file)
                        .param("productId", "100")
                        .param("role", "AGREEMENT")
                        .header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void uploadAdditional(UUID submissionId, String role, String filename, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, XLSX_CONTENT_TYPE, content);
        mockMvc.perform(multipart("/api/submissions/{id}/files", submissionId)
                        .file(file)
                        .param("role", role)
                        .header("X-Member-Id", "1"))
                .andExpect(status().isOk());
    }

    private void stubStorage() throws Exception {
        when(fileStoragePort.store(any(InputStream.class), anyString(), anyString())).thenAnswer(invocation -> {
            String key = "integration:" + UUID.randomUUID();
            storedFiles.put(key, invocation.getArgument(0, InputStream.class).readAllBytes());
            return new StoredPath(key);
        });
        when(fileStoragePort.load(any(StoredPath.class))).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            String key = invocation.getArgument(0, StoredPath.class).value();
            CountDownLatch arrivals = loadArrivals;
            CountDownLatch release = releaseLoads;
            if (arrivals != null && release != null) {
                arrivals.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent parser loads");
                }
            }
            return new ByteArrayInputStream(storedFiles.get(key));
        });
    }

    private int replace(UUID id, String filename, String contentType, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        return mockMvc.perform(multipart("/api/submissions/{id}/files/{role}/replace", id, "AGREEMENT")
                        .file(file)
                        .header("X-Member-Id", "1"))
                .andReturn().getResponse().getStatus();
    }

    private int replaceSafely(UUID id, String filename, byte[] content) {
        try {
            return replace(id, filename, "application/pdf", content);
        } catch (Exception exception) {
            return 500;
        }
    }

    private byte[] createPdf(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream stream =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                stream.beginText();
                stream.newLineAtOffset(70, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
        }
        return output.toByteArray();
    }

    private byte[] createWorkbook(boolean withContent) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Upload");
            if (withContent) {
                sheet.createRow(0).createCell(0).setCellValue("Known spreadsheet body");
            }
            workbook.write(output);
        }
        return output.toByteArray();
    }
}