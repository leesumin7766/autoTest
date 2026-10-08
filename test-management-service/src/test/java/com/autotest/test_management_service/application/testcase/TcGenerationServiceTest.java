package com.autotest.test_management_service.application.testcase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionJobRepository;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class TcGenerationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void noApprovalDoesNotCreateOrRunAnything() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new TcGenerationService(jdbc, mapper, mock(SubmissionRepository.class),
                mock(SubmissionPersistenceService.class), mock(ProductDescriptionJobRepository.class),
                mock(PlatformTransactionManager.class), "http://invalid", "test", 10);
        try {
            var error = assertThrows(ProductDescriptionException.class, () -> service.start(UUID.randomUUID(), 1L, false));
            assertEquals("APPROVAL_REQUIRED", error.code());
            verifyNoInteractions(jdbc);
        } finally { service.shutdown(); }
    }

    @Test void rejectsInventedSourceAndPrematureVerdict() throws Exception {
        UUID id = UUID.randomUUID();
        var input = mapper.readTree("""
                {"documents":[{"fileId":"doc","role":"MANUAL","extractedText":"🙂export CSV now"}]}
                """);
        var output = mapper.readTree("""
                {"submissionId":"%s","generationMode":"REAL","testCases":[
                {"tcId":"TC-001-001","result":null,"reviewRequired":true,"expectedResult":"CSV","featurePath":["Exports"],"steps":["Click export"],
                "sources":[{"fileId":"doc","role":"MANUAL","quote":"export CSV","characterStart":1,"characterEnd":11}]}]}
                """.formatted(id));
        assertDoesNotThrow(() -> TcGenerationService.validateResult(output, id, input));
        var tc = (com.fasterxml.jackson.databind.node.ObjectNode) output.path("testCases").get(0);
        tc.put("result", "P");
        assertThrows(IllegalStateException.class, () -> TcGenerationService.validateResult(output, id, input));
        tc.putNull("result");
        ((com.fasterxml.jackson.databind.node.ObjectNode) tc.path("sources").get(0)).put("quote", "invented");
        assertThrows(IllegalStateException.class, () -> TcGenerationService.validateResult(output, id, input));
    }
}
