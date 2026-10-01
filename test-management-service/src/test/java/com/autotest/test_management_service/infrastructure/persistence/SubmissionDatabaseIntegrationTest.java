package com.autotest.test_management_service.infrastructure.persistence;

import com.autotest.test_management_service.Application;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.StoredPath;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.url=jdbc:postgresql://localhost:5432/autotest",
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileStoragePort fileStoragePort;

    private final Map<String, byte[]> storedFiles = new HashMap<>();
    private final List<UUID> submissionIds = new ArrayList<>();

    @AfterEach
    void removeTestSubmissions() {
        submissionIds.forEach(id -> jdbcTemplate.update("DELETE FROM submissions WHERE submission_id = ?", id));
        submissionIds.clear();
        storedFiles.clear();
    }

    @Test
    void commitsUploadedBeforeParsingAndPersistsParsedResult() throws Exception {
        stubStorage();
        byte[] workbook = createWorkbook(true);

        JsonNode postBody = upload("document.xlsx", workbook);
        UUID id = UUID.fromString(postBody.get("submissionId").asText());
        submissionIds.add(id);

        assertEquals("PARSED", postBody.get("status").asText());
        assertEquals("PARSED", jdbcTemplate.queryForObject(
                "SELECT status FROM submissions WHERE submission_id = ?", String.class, id));
        String extractedText = jdbcTemplate.queryForObject(
                "SELECT extracted_text FROM submissions WHERE submission_id = ?", String.class, id);
        assertNotNull(extractedText);
        assertEquals(true, extractedText.contains("Known spreadsheet body"));

        JsonNode getBody = objectMapper.readTree(mockMvc.perform(get("/api/submissions/{id}", id))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertEquals(id.toString(), getBody.get("submissionId").asText());
        assertEquals("PARSED", getBody.get("status").asText());
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
                "SELECT failure_reason FROM submissions WHERE submission_id = ?", String.class, id));
        assertEquals("", jdbcTemplate.queryForObject(
                "SELECT extracted_text FROM submissions WHERE submission_id = ?", String.class, id));

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
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT submission_id, status, extracted_text FROM submissions WHERE stored_path = ?", key);
                if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                UUID id = (UUID) row.get("submission_id");
                submissionIds.add(id);
                assertEquals("UPLOADED", row.get("status"));
                assertEquals("", row.get("extracted_text"));
                }
            return new ByteArrayInputStream(storedFiles.get(key));
        });
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