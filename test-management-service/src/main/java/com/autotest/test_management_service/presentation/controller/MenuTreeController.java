package com.autotest.test_management_service.presentation.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.testcase.MenuTreeService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions/{submissionId}/menu-tree")
@RequiredArgsConstructor
public class MenuTreeController {
    private final MenuTreeService service;
    public record Approval(JsonNode tree) { }
    public record ExternalPrompt(String sourceType, String sourceText) { }
    public record ExternalDraft(String sourceType, String sourceText, JsonNode tree) { }

    @GetMapping
    public JsonNode status(@PathVariable UUID submissionId, @RequestHeader("X-Member-Id") long memberId) {
        return service.status(submissionId, memberId);
    }

    @PostMapping("/external-prompt")
    public ResponseEntity<JsonNode> externalPrompt(@PathVariable UUID submissionId,
            @RequestHeader("X-Member-Id") long memberId, @RequestBody ExternalPrompt request) {
        return ResponseEntity.ok().header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.externalPrompt(submissionId, memberId, request.sourceType(), request.sourceText()));
    }

    @PostMapping("/external-draft")
    public ResponseEntity<JsonNode> importExternalDraft(@PathVariable UUID submissionId,
            @RequestHeader("X-Member-Id") long memberId, @RequestBody ExternalDraft request) {
        return ResponseEntity.accepted().body(service.importExternalDraft(submissionId, memberId,
                request.sourceType(), request.sourceText(), request.tree()));
    }

    @PutMapping("/approval")
    public JsonNode approve(@PathVariable UUID submissionId, @RequestHeader("X-Member-Id") long memberId,
            @RequestBody Approval approval) {
        return service.approve(submissionId, memberId, approval.tree());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ProductDescriptionException.class)
    public ResponseEntity<Map<String,String>> handle(ProductDescriptionException error) {
        return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "message", error.getMessage()));
    }
}
