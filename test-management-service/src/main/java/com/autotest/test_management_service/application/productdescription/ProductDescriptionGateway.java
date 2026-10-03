package com.autotest.test_management_service.application.productdescription;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/** Boundary to the content/PDF generation service. Calls block; interrupting the thread aborts the outbound request. */
public interface ProductDescriptionGateway {

    ContentResult generateContent(ContentCall call) throws GatewayException, InterruptedException;

    byte[] render(String format, JsonNode document, JsonNode presentation) throws GatewayException, InterruptedException;

    record SourceDocument(UUID fileId, String role, String originalFilename, String format, String extractedText) {
    }

    record ContentCall(UUID submissionId, long productId, String decision, JsonNode warnings,
                       List<SourceDocument> documents) {
    }

    record ContentResult(JsonNode template, JsonNode document, String mode, String label, String templateId,
                         String templateVersion) {
    }

    class GatewayException extends Exception {
        private final String code;

        public GatewayException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
