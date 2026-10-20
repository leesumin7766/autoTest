package com.autotest.test_management_service.application.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionJobRepository;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlaywrightSetupServiceTest {
    private PlaywrightSetupService service(JdbcTemplate jdbc) {
        return new PlaywrightSetupService(jdbc, new ObjectMapper(), mock(SubmissionPersistenceService.class),
                mock(SubmissionRepository.class), mock(ProductDescriptionJobRepository.class),
                mock(PlatformTransactionManager.class));
    }

    private PlaywrightSetupService.SetupRequest request(String url, boolean confirmed) {
        return new PlaywrightSetupService.SetupRequest(url, "MANUAL", "sign in using test account",
                "QA user", "dashboard only", "seeded data", confirmed);
    }

    @Test void rejectsCredentialsTokensAndNonWebSchemesWithoutAccessingStorage() {
        var jdbc = mock(JdbcTemplate.class);
        var service = service(jdbc);
        for (String url : new String[] { "javascript:alert(1)", "https://user:pass@example.test/",
                "https://example.test/?token=secret", "https://example.test/#fragment" }) {
            var error = assertThrows(ProductDescriptionException.class,
                    () -> service.save(java.util.UUID.randomUUID(), 1L, request(url, true)));
            assertEquals("PLAYWRIGHT_URL_INVALID", error.code());
        }
        verifyNoInteractions(jdbc);
    }

    @Test void requiresExplicitTestEnvironmentConfirmationBeforeLookingUpSubmission() {
        var jdbc = mock(JdbcTemplate.class);
        var error = assertThrows(ProductDescriptionException.class,
                () -> service(jdbc).save(java.util.UUID.randomUUID(), 1L, request("http://localhost:5173/", false)));
        assertEquals("TEST_ENVIRONMENT_CONFIRMATION_REQUIRED", error.code());
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsCredentialLookingValuesInFreeTextBeforeLookingUpSubmission() {
        var jdbc = mock(JdbcTemplate.class);
        var req = new PlaywrightSetupService.SetupRequest("https://test.example/", "SSO",
                "password = dont-store-this", "QA account", "dashboard", "seeded data", true);
        var error = assertThrows(ProductDescriptionException.class,
                () -> service(jdbc).save(java.util.UUID.randomUUID(), 1L, req));
        assertEquals("PLAYWRIGHT_SECRET_INPUT_NOT_ALLOWED", error.code());
        verifyNoInteractions(jdbc);
    }
}
