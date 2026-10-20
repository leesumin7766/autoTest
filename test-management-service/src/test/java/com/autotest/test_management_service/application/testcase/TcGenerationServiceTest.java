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
                mock(MenuTreeService.class),
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
                {"menuTree":{"nodes":[{"featurePath":["Exports"],"children":[]}]},
                "documents":[{"fileId":"doc","role":"MANUAL","extractedText":"🙂export CSV now"}]}
                """);
        var output = mapper.readTree("""
                {"submissionId":"%s","generationMode":"REAL","testCases":[
                {"tcId":"TC-001-001","testType":"PRODUCT_BEHAVIOR","result":null,"reviewRequired":true,"expectedResult":"CSV","featurePath":["Exports"],"steps":["Click export"],
                "sources":[{"fileId":"doc","role":"MANUAL","quote":"export CSV","characterStart":1,"characterEnd":11}]}]}
                """.formatted(id));
        addPolicyCase(output, "TC-002-001", "product-description");
        addPolicyCase(output, "TC-003-001", "user-manual");
        assertDoesNotThrow(() -> TcGenerationService.validateResult(output, id, input));
        ((com.fasterxml.jackson.databind.node.ArrayNode) output.path("testCases").get(0).path("featurePath")).removeAll();
        ((com.fasterxml.jackson.databind.node.ArrayNode) output.path("testCases").get(0).path("featurePath")).add("Unapproved");
        assertThrows(IllegalStateException.class, () -> TcGenerationService.validateResult(output, id, input));
        ((com.fasterxml.jackson.databind.node.ArrayNode) output.path("testCases").get(0).path("featurePath")).removeAll();
        ((com.fasterxml.jackson.databind.node.ArrayNode) output.path("testCases").get(0).path("featurePath")).add("Exports");
        var tc = (com.fasterxml.jackson.databind.node.ObjectNode) output.path("testCases").get(0);
        tc.put("result", "P");
        assertThrows(IllegalStateException.class, () -> TcGenerationService.validateResult(output, id, input));
        tc.putNull("result");
        ((com.fasterxml.jackson.databind.node.ObjectNode) tc.path("sources").get(0)).put("quote", "invented");
        assertThrows(IllegalStateException.class, () -> TcGenerationService.validateResult(output, id, input));
    }

    @Test void acceptsOnlyKnownProjectDocumentCriteria() throws Exception {
        UUID id = UUID.randomUUID();
        var input = mapper.readTree("{\"documents\":[]}");
        var output = mapper.readTree("""
                {"submissionId":"%s","generationMode":"REAL","testCases":[
                {"tcId":"TC-001-001","testType":"DOCUMENT_CONFORMANCE","result":null,"reviewRequired":true,
                 "expectedResult":"제품설명서 제공","featurePath":["제품 문서"],"steps":["자료 확인"],
                 "sources":[{"kind":"PROJECT_CRITERION","criterionId":"product-description","fileId":"TC_GENERATION_GUIDELINES_V1",
                  "role":"PROJECT_POLICY","quote":"제품설명서와 사용자매뉴얼의 제공 여부를 각각 확인한다."}]}]}
                """.formatted(id));
        addPolicyCase(output, "TC-002-001", "user-manual");
        assertDoesNotThrow(() -> TcGenerationService.validateResult(output, id, input));
        ((com.fasterxml.jackson.databind.node.ObjectNode) output.path("testCases").get(0).path("sources").get(0))
                .put("criterionId", "arbitrary");
        assertThrows(IllegalStateException.class, () -> TcGenerationService.validateResult(output, id, input));
    }

    private void addPolicyCase(com.fasterxml.jackson.databind.JsonNode output, String tcId, String criterionId) {
        var tc = ((com.fasterxml.jackson.databind.node.ObjectNode) output).withArray("testCases").addObject();
        tc.put("tcId", tcId); tc.put("testType", "DOCUMENT_CONFORMANCE"); tc.putNull("result");
        tc.put("reviewRequired", true); tc.put("expectedResult", "문서가 제공됨");
        tc.putArray("featurePath").add("제품 문서"); tc.putArray("steps").add("제품 자료 확인");
        var source = tc.putArray("sources").addObject(); source.put("kind", "PROJECT_CRITERION");
        source.put("criterionId", criterionId); source.put("fileId", "TC_GENERATION_GUIDELINES_V1");
        source.put("role", "PROJECT_POLICY"); source.put("quote", "제품설명서와 사용자매뉴얼의 제공 여부를 각각 확인한다.");
    }
}
