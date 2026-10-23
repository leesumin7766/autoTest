package com.autotest.test_management_service.presentation.controller;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class TestExecutionInternalController {
    private final JdbcTemplate jdbc;private final String token;
    public record Progress(UUID attempt,String stage,int round,String caseId,com.fasterxml.jackson.databind.JsonNode trace){}
    @PostMapping("/api/internal/test-executions/{run}/progress")
    public ResponseEntity<?> progress(@PathVariable UUID run,@RequestBody Progress body,@RequestHeader("X-Internal-Token") String supplied){
        if(token.isBlank()||!java.security.MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))return ResponseEntity.status(403).build();
        if(body.stage()==null||!Set.of("CONNECTING","PLANNING","ACTING","RECHECK","JUDGING").contains(body.stage())||body.round()<0||body.round()>12)return ResponseEntity.badRequest().build();
        if(body.trace()!=null&&(!body.trace().isArray()||body.trace().size()>12||body.trace().toString().length()>80000))return ResponseEntity.badRequest().build();
        var progress=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("stage",body.stage()).put("round",body.round());
        if(body.caseId()!=null)progress.put("caseId",body.caseId());
        if(body.trace()!=null)progress.set("trace",body.trace());
        int changed=jdbc.update("UPDATE test_execution_runs SET snapshot=jsonb_set(snapshot,'{progress}',?::jsonb),updated_at=now() WHERE run_id=? AND attempt=? AND status='RUNNING' AND (?::text IS NULL OR snapshot->>'currentCaseId'=?::text)",progress.toString(),run,body.attempt(),body.caseId(),body.caseId());
        return ResponseEntity.status(changed==1?200:409).cacheControl(CacheControl.noStore()).body(Map.of("active",changed==1));
    }
    public TestExecutionInternalController(JdbcTemplate jdbc,@Value("${product-description.internal-token:}") String token){this.jdbc=jdbc;this.token=token;}
    @GetMapping("/api/internal/test-executions/{run}/active")
    public ResponseEntity<?> active(@PathVariable UUID run,@RequestParam UUID attempt,@RequestHeader("X-Internal-Token") String supplied){
        if(token.isBlank()||!java.security.MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))return ResponseEntity.status(403).build();
        boolean active=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM test_execution_runs WHERE run_id=? AND attempt=? AND status='RUNNING')",Boolean.class,run,attempt));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("active",active));
    }
}
