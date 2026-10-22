package com.autotest.test_management_service.presentation.controller;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.autotest.test_management_service.application.testcase.TestExecutionService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/submissions/{submission}/executions")
public class TestExecutionController {
    private final TestExecutionService service;
    public record Prepare(UUID tcSetId,String targetUrl,String notes,List<String> caseIds){}
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping public ResponseEntity<?> status(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestParam(required=false) UUID run){return ok(service.status(submission,member,run));}
    @PostMapping public ResponseEntity<?> prepare(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestBody Prepare input){return ok(service.prepare(submission,member,input.tcSetId(),input.targetUrl(),input.notes(),input.caseIds()));}
    @PostMapping("/{run}/start") public ResponseEntity<?> start(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run){return ok(service.transition(submission,member,run,false));}
    @PostMapping("/{run}/cancel") public ResponseEntity<?> cancel(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run){return ok(service.transition(submission,member,run,true));}
    @ExceptionHandler(ProductDescriptionException.class) public ResponseEntity<?> failure(ProductDescriptionException error){return ResponseEntity.status(error.status()).body(Map.of("code",error.code(),"message",error.getMessage()));}
}
