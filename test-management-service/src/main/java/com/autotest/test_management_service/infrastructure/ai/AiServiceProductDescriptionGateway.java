package com.autotest.test_management_service.infrastructure.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class AiServiceProductDescriptionGateway implements ProductDescriptionGateway {
    private static final String BASE_PATH = "/api/v1/product-descriptions";

    // uvicorn does not accept the h2c upgrade the JDK client attempts by default.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration contentTimeout;
    private final Duration renderTimeout;
    @Value("${product-description.internal-token:}")
    private String internalToken;

    public AiServiceProductDescriptionGateway(
            ObjectMapper objectMapper,
            @Value("${ai-service.base-url:http://localhost:8005}") String baseUrl,
            @Value("${ai-service.product-description.content-timeout-seconds:1200}") long contentTimeoutSeconds,
            @Value("${product-description.render-timeout-seconds:60}") long renderTimeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.contentTimeout = Duration.ofSeconds(contentTimeoutSeconds);
        this.renderTimeout = Duration.ofSeconds(renderTimeoutSeconds);
    }

    @Override
    public ContentResult generateContent(ContentCall call) throws GatewayException, InterruptedException {
        ObjectNode body = contentBody(call);
        HttpResponse<byte[]> response = post(BASE_PATH + "/content", body, contentTimeout);
        JsonNode payload = parse(response.body());
        JsonNode template = payload.path("template");
        JsonNode document = payload.path("document");
        String mode = payload.path("generation").path("mode").asText("");
        if (!template.isObject() || !document.isObject() || !("MOCK".equals(mode) || "REAL".equals(mode))
                || !template.path("digest").asText().equals(document.path("template").path("digest").asText())) {
            throw new GatewayException("AI_RESPONSE_INVALID", "생성 서비스 응답 형식이 올바르지 않습니다.");
        }
        JsonNode label = payload.path("generation").path("label");
        return new ContentResult(template, document, mode, label.isTextual() ? label.asText() : null,
                template.path("manifest").path("templateId").asText(),
                template.path("manifest").path("version").asText());
    }

    @Override
    public ChunkProgress contentProgress(ContentCall call) throws GatewayException, InterruptedException {
        ObjectNode body = contentBody(call);
        HttpResponse<byte[]> response = post(BASE_PATH + "/content/progress", body, Duration.ofSeconds(30));
        JsonNode payload = parse(response.body());
        return new ChunkProgress(Math.max(0, payload.path("completedChunks").asInt()),
                Math.max(0, payload.path("totalChunks").asInt()));
    }

    private ObjectNode contentBody(ContentCall call) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("submissionId", call.submissionId().toString());
        body.put("productId", call.productId());
        body.put("decision", call.decision());
        body.set("warnings", call.warnings() == null ? objectMapper.createArrayNode() : call.warnings());
        var documents = body.putArray("documents");
        call.documents().forEach(document -> documents.addObject()
                .put("fileId", document.fileId().toString())
                .put("role", document.role())
                .put("originalFilename", document.originalFilename())
                .put("format", document.format())
                .put("extractedText", document.extractedText()));
        return body;
    }

    @Override
    public byte[] render(String format, JsonNode document, JsonNode presentation)
            throws GatewayException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("format", format);
        body.set("document", document);
        body.set("presentation", presentation);
        HttpResponse<byte[]> response = post(BASE_PATH + "/render", body, renderTimeout);
        byte[] bytes = response.body();
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw new GatewayException("RENDER_RESPONSE_INVALID", "PDF 출력 결과가 올바르지 않습니다.");
        }
        return bytes;
    }

    private HttpResponse<byte[]> post(String path, ObjectNode body, Duration timeout)
            throws GatewayException, InterruptedException {
        HttpRequest request;
        if (internalToken == null || internalToken.isBlank()) {
            throw new GatewayException("INTERNAL_AUTH_NOT_CONFIGURED", "생성 서비스 인증 설정이 없습니다.");
        }
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                    .build();
        } catch (JsonProcessingException exception) {
            throw new GatewayException("REQUEST_INVALID", "생성 요청을 만들지 못했습니다.");
        }
        CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> response = future.get(timeout.toSeconds() + 5, TimeUnit.SECONDS);
            if (response.statusCode() / 100 != 2) {
                throw errorFrom(response);
            }
            return response;
        } catch (InterruptedException | CancellationException interrupted) {
            future.cancel(true);
            throw interrupted instanceof InterruptedException exception ? exception : new InterruptedException();
        } catch (TimeoutException timedOut) {
            future.cancel(true);
            throw new GatewayException("AI_SERVICE_TIMEOUT", "생성 서비스 응답 시간이 초과되었습니다.");
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof java.net.http.HttpTimeoutException) {
                throw new GatewayException("AI_SERVICE_TIMEOUT", "생성 서비스 응답 시간이 초과되었습니다.");
            }
            throw new GatewayException("AI_SERVICE_UNAVAILABLE", "생성 서비스에 연결하지 못했습니다.");
        }
    }

    private GatewayException errorFrom(HttpResponse<byte[]> response) {
        try {
            JsonNode detail = objectMapper.readTree(response.body()).path("detail");
            String code = detail.path("code").asText("");
            if (!code.isBlank()) {
                return new GatewayException(code, detail.path("message").asText("생성 서비스 오류가 발생했습니다."));
            }
        } catch (IOException ignored) {
            // Fall through to the generic error; the response body is never echoed.
        }
        return new GatewayException("AI_SERVICE_ERROR", "생성 서비스 오류가 발생했습니다. (HTTP " + response.statusCode() + ")");
    }

    private JsonNode parse(byte[] body) throws GatewayException {
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new GatewayException("AI_RESPONSE_INVALID", "생성 서비스 응답 형식이 올바르지 않습니다.");
        }
    }
}
