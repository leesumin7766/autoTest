package com.autotest.test_management_service.presentation.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.autotest.test_management_service.application.crawler.CrawlAgentWire;
import com.autotest.test_management_service.application.crawler.RemoteCrawlService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RemoteCrawlController {
    private final RemoteCrawlService service;
    private final CrawlAgentWire wire;
    @org.springframework.beans.factory.annotation.Value("${product-description.internal-token:}")
    private String internalToken;
    public record AgentRequest(String name) { }
    public record StartRequest(UUID agentId,String executionMode,String purpose,
            com.autotest.test_management_service.application.crawler.PlaywrightSetupService.SetupRequest draft) { }
    private <T> ResponseEntity<T> response(T value) { return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(value); }

    @GetMapping("/api/crawl-agents")
    public ResponseEntity<?> agents(@RequestHeader("X-Member-Id") long member) {
        return response(Map.of("agents",service.agents(member)));
    }
    @PostMapping("/api/crawl-agents")
    public ResponseEntity<?> create(@RequestHeader("X-Member-Id") long member,@RequestBody AgentRequest request) {
        return response(service.provision(member,request.name()));
    }
    @PostMapping("/api/crawl-agents/{agent}/revoke")
    public ResponseEntity<?> revoke(@RequestHeader("X-Member-Id") long member,@PathVariable UUID agent) {
        service.revoke(member,agent); return response(Map.of("revoked",true));
    }
    @PostMapping("/api/crawl-agents/{agent}/channel/{action}")
    public ResponseEntity<?> channel(@PathVariable UUID agent,@PathVariable String action,
            @RequestHeader("X-Agent-Timestamp") long timestamp,@RequestHeader("X-Agent-Nonce") UUID nonce,@RequestBody String raw) {
        var session=wire.open(agent,action,timestamp,nonce,raw);
        Object result=switch(action) {
            case "heartbeat" -> { service.seen(agent); yield Map.of("accepted",true); }
            case "claim" -> service.claim(agent,session.body().path("capabilities").isArray()&&java.util.stream.StreamSupport.stream(session.body().path("capabilities").spliterator(),false).anyMatch(value->"crawl-resume-v1".equals(value.asText())));
            case "event" -> service.worker(agent,session.body());
            default -> throw new ProductDescriptionException(HttpStatus.BAD_REQUEST,"AGENT_ACTION_INVALID","지원하지 않는 실행기 요청입니다.");
        };
        return response(wire.reply(session,result));
    }
    @GetMapping("/api/submissions/{submission}/playwright-crawls")
    public ResponseEntity<?> status(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member) {
        return response(service.status(submission,member));
    }
    @PostMapping("/api/submissions/{submission}/playwright-crawls")
    public ResponseEntity<?> start(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestBody StartRequest request) {
        return response(service.start(submission,member,request.agentId(),request.executionMode()==null?"REMOTE":request.executionMode(),
                request.purpose()==null?"CRAWL":request.purpose(),request.draft()));
    }
    @PostMapping("/api/submissions/{submission}/playwright-crawls/{job}/resume")
    public ResponseEntity<?> resume(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member,@RequestBody StartRequest request){return response(service.resume(submission,member,job,request.agentId(),request.executionMode()==null?"LOCAL":request.executionMode()));}
    @PostMapping("/api/internal/playwright-crawls/events")
    public ResponseEntity<?> localEvent(@RequestHeader("X-Internal-Token") String token,@RequestBody JsonNode body) {
        if(internalToken.isBlank() || !java.security.MessageDigest.isEqual(internalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),token.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new ProductDescriptionException(HttpStatus.UNAUTHORIZED,"INTERNAL_AUTH_INVALID","내부 실행기 인증이 필요합니다.");
        return response(service.worker(null,body));
    }
    @PostMapping("/api/submissions/{submission}/playwright-crawls/{job}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member) {
        service.cancel(submission,member,job); return response(service.status(submission,member));
    }
    @GetMapping("/api/submissions/{submission}/playwright-crawls/{job}/pages/{index}")
    public ResponseEntity<JsonNode> evidence(@PathVariable UUID submission,@PathVariable UUID job,@PathVariable int index,@RequestHeader("X-Member-Id") long member) {
        return response(service.evidence(submission,member,job,index));
    }
    @GetMapping("/api/submissions/{submission}/playwright-crawls/{job}/menu-tree")
    public ResponseEntity<?> menuTree(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member) {
        return response(service.menuTree(submission,member,job));
    }
    @GetMapping("/api/submissions/{submission}/playwright-crawls/{job}/pages/{index}/screenshot")
    public ResponseEntity<byte[]> screenshot(@PathVariable UUID submission,@PathVariable UUID job,@PathVariable int index,@RequestHeader("X-Member-Id") long member) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.IMAGE_JPEG).body(service.screenshot(submission,member,job,index));
    }
    @ExceptionHandler(ProductDescriptionException.class)
    ResponseEntity<?> handle(ProductDescriptionException error) {
        return ResponseEntity.status(error.status()).body(Map.of("code",error.code(),"message",error.getMessage()));
    }
}
