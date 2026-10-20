package com.autotest.test_management_service.application.crawler;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest(properties = {"s3.enabled=false", "s3.access-key=test", "s3.secret-key=test",
        "playwright.credentials-key-file=./build/test-playwright/credentials.key"})
@AutoConfigureMockMvc
@Transactional
class PlaywrightSetupIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = System.getenv().getOrDefault("AUTOTEST_TEST_DATABASE_URL", "jdbc:postgresql://localhost:5432/autotest_test");
            if ("/autotest".equalsIgnoreCase(URI.create(url.substring(5)).getPath()))
                throw new IllegalStateException("Tests require an isolated database");
            return url;
        });
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired SubmissionPersistenceService persistence;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired PlaywrightCredentialCipher cipher;
    UUID id;
    String endpoint;

    @BeforeEach void seed() {
        id = UUID.randomUUID();
        endpoint = "/api/submissions/" + id + "/playwright-setup";
        jdbc.update("INSERT INTO submissions(submission_id,member_id,product_id,status,uploaded_at) VALUES (?,8101,1,'PARSED',now())", id);
        for (String role : List.of("FUNCTION_LIST", "MANUAL"))
            jdbc.update("""
                    INSERT INTO submission_files(file_id,submission_id,role,file_type,file_format,original_filename,
                        mime_type,stored_path,extracted_text,status,uploaded_at)
                    VALUES (?, ?, ?, 'PDF', 'PDF', ?, 'application/pdf', 's3://autotest-docs/test', ?, 'PARSED', now())
                    """, UUID.randomUUID(), id, role, role + ".pdf", "test content " + role);
        String digest = DocumentDigest.of(persistence.findDocumentsBySubmissionId(SubmissionId.of(id)));
        jdbc.update("""
                INSERT INTO submission_ai_deliveries(submission_id,status,attempt_count,preflight_result,verified_document_digest,updated_at)
                VALUES (?,'DELIVERED',1,'{"decision":"READY_WITH_WARNINGS","warnings":[]}'::jsonb,?,now())
                """, id, digest);
    }

    ObjectNode request() {
        ObjectNode body = mapper.createObjectNode();
        body.put("targetUrl", "https://test.example/dashboard"); body.put("loginMethod", "FORM_AUTO");
        body.put("testEnvironmentConfirmed", true); body.put("password", "test-secret-password");
        ObjectNode config = mapper.valueToTree(CrawlAutomation.defaults());
        config.put("username", "qa-admin"); config.put("productVersion", "2.0");
        config.put("loginUrl", "https://test.example/login");
        body.set("automation", config);
        return body;
    }

    String save(ObjectNode body) throws Exception {
        return mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.setup.passwordConfigured").value(true))
                .andReturn().getResponse().getContentAsString();
    }

    @Test void storesEncryptedCredentialReturnsOnlyFlagAndPreservesPasswordOnEdit() throws Exception {
        String response = save(request());
        assertFalse(response.contains("test-secret-password"));
        assertFalse(response.contains("ciphertext"));
        String ciphertext = jdbc.queryForObject("SELECT password_ciphertext FROM playwright_crawl_setups WHERE submission_id=?", String.class, id);
        assertNotEquals("test-secret-password", ciphertext);
        assertEquals("test-secret-password", cipher.decrypt(id, ciphertext));
        var edit = request(); edit.put("password", ""); edit.put("crawlScopeNotes", "dashboard");
        save(edit);
        assertEquals(ciphertext, jdbc.queryForObject("SELECT password_ciphertext FROM playwright_crawl_setups WHERE submission_id=?", String.class, id));
        mvc.perform(get(endpoint).header("X-Member-Id", "8101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.setup.automation.productVersion").value("2.0"))
                .andExpect(jsonPath("$.setup.password").doesNotExist())
                .andExpect(jsonPath("$.setup.passwordConfigured").value(true));
        mvc.perform(get(endpoint).header("X-Member-Id", "8102")).andExpect(status().isForbidden());
    }

    @Test void accountOrLoginOriginChangeRequiresNewPasswordAndSwitchToNoLoginDeletesIt() throws Exception {
        save(request());
        var edit = request(); edit.put("password", ""); ((ObjectNode) edit.get("automation")).put("username", "other");
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(edit))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAYWRIGHT_PASSWORD_REQUIRED"));
        edit = request(); edit.put("password", "");
        ((ObjectNode) edit.get("automation")).put("loginUrl", "https://login.example/login")
                .putArray("allowedOrigins").add("https://login.example");
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(edit))).andExpect(status().isBadRequest());
        edit = request(); edit.put("loginMethod", "NONE"); edit.remove("password");
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(edit))).andExpect(status().isOk())
                .andExpect(jsonPath("$.setup.passwordConfigured").value(false));
        assertNull(jdbc.queryForObject("SELECT password_ciphertext FROM playwright_crawl_setups WHERE submission_id=?", String.class, id));
    }

    @Test void rejectsUnlistedLoginHostExcessiveCrawlLimitsAndBlockedPreflight() throws Exception {
        var body = request(); ((ObjectNode) body.get("automation")).put("loginUrl", "https://other.example/login");
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        body = request(); ((ObjectNode) body.get("automation")).put("maxPages", 501);
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE submission_ai_deliveries SET status='BLOCKED' WHERE submission_id=?", id);
        mvc.perform(put(endpoint).header("X-Member-Id", "8101").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request()))).andExpect(status().isConflict());
    }
}
