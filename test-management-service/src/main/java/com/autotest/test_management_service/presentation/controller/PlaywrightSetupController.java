package com.autotest.test_management_service.presentation.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autotest.test_management_service.application.crawler.PlaywrightSetupService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions/{submissionId}/playwright-setup")
@RequiredArgsConstructor
public class PlaywrightSetupController {
    private final PlaywrightSetupService service;
    public record SetupResponse(PlaywrightSetupService.SetupView setup,String configVersion) { }

    @GetMapping("/tls-policy")
    public ResponseEntity<?> tlsPolicy(@PathVariable UUID submissionId,@RequestHeader("X-Member-Id")long memberId,
            @org.springframework.web.bind.annotation.RequestParam String targetUrl){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("ignoreHTTPSErrors",service.trustedTargetCertificate(submissionId,memberId,targetUrl)));
    }

    @GetMapping
    public ResponseEntity<SetupResponse> get(@PathVariable UUID submissionId, @RequestHeader("X-Member-Id") long memberId) {
        return ResponseEntity.ok().header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new SetupResponse(service.get(submissionId, memberId),service.configurationVersion(submissionId,memberId)));
    }

    @PutMapping
    public ResponseEntity<SetupResponse> save(@PathVariable UUID submissionId, @RequestHeader("X-Member-Id") long memberId,
            @RequestBody PlaywrightSetupService.SetupRequest request) {
        return ResponseEntity.ok().header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new SetupResponse(service.save(submissionId, memberId, request),service.configurationVersion(submissionId,memberId)));
    }

    @ExceptionHandler(ProductDescriptionException.class)
    public ResponseEntity<Map<String, String>> handle(ProductDescriptionException error) {
        return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "message", error.getMessage()));
    }
}
