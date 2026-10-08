package com.autotest.test_management_service.presentation.controller;

import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.autotest.test_management_service.application.testcase.TcGenerationService;
import com.autotest.test_management_service.application.testcase.TcExcelExporter;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions/{submissionId}/test-cases")
@RequiredArgsConstructor
public class TcGenerationController {
    private final TcGenerationService service;
    private final TcExcelExporter exporter;
    public record Approval(boolean approved) { }

    @PostMapping public ResponseEntity<JsonNode> start(@PathVariable UUID submissionId,
            @RequestHeader("X-Member-Id") long memberId, @RequestBody Approval approval) {
        return ResponseEntity.accepted().body(service.start(submissionId, memberId, approval.approved()));
    }
    @GetMapping public JsonNode status(@PathVariable UUID submissionId, @RequestHeader("X-Member-Id") long memberId) {
        return service.status(submissionId, memberId);
    }
    @PostMapping("/{jobId}/cancel") public JsonNode cancel(@PathVariable UUID submissionId,
            @PathVariable UUID jobId, @RequestHeader("X-Member-Id") long memberId) {
        return service.cancel(submissionId, jobId, memberId);
    }
    @GetMapping("/{jobId}/download") public ResponseEntity<byte[]> download(@PathVariable UUID submissionId,
            @PathVariable UUID jobId, @RequestHeader("X-Member-Id") long memberId) {
        JsonNode job = service.storedJob(submissionId, jobId, memberId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("TC-" + submissionId + ".xlsx", StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(exporter.export(job));
    }
    @ExceptionHandler(ProductDescriptionException.class)
    public ResponseEntity<Map<String,String>> handle(ProductDescriptionException error) {
        return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "message", error.getMessage()));
    }
}
