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
    public TestExecutionInternalController(JdbcTemplate jdbc,@Value("${product-description.internal-token:}") String token){this.jdbc=jdbc;this.token=token;}
    @GetMapping("/api/internal/test-executions/{run}/active")
    public ResponseEntity<?> active(@PathVariable UUID run,@RequestParam UUID attempt,@RequestHeader("X-Internal-Token") String supplied){
        if(token.isBlank()||!java.security.MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))return ResponseEntity.status(403).build();
        boolean active=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM test_execution_runs WHERE run_id=? AND attempt=? AND status='RUNNING')",Boolean.class,run,attempt));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("active",active));
    }
}
