package com.autotest.test_management_service.presentation.controller;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.autotest.test_management_service.application.testcase.TestExecutionService;
import com.autotest.test_management_service.application.testcase.ExecutionReviewService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/submissions/{submission}/executions")
public class TestExecutionController {
    private final TestExecutionService service;
    private final ExecutionReviewService reviews;
    public record Prepare(UUID tcSetId,String targetUrl,String notes,List<String> caseIds,boolean allowMutation,String productVersion){}
    public record Review(String caseId,int revision,String result,String reason,String disposition){}
    public record Retry(List<String> caseIds,String productVersion){}
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping public ResponseEntity<?> status(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestParam(required=false) UUID run){return ok(service.status(submission,member,run));}
    @PostMapping public ResponseEntity<?> prepare(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestBody Prepare input){return ok(service.prepare(submission,member,input.tcSetId(),input.targetUrl(),input.notes(),input.caseIds(),input.allowMutation(),input.productVersion()));}
    @PostMapping("/{run}/review") public ResponseEntity<?> review(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run,@RequestBody Review input){return ok(reviews.review(submission,member,run,input.caseId(),input.revision(),input.result(),input.reason(),input.disposition()));}
    @PostMapping("/{run}/retry") public ResponseEntity<?> retry(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run,@RequestBody Retry input){return ok(reviews.retry(submission,member,run,input.caseIds(),input.productVersion()));}
    @GetMapping("/{run}/report") public ResponseEntity<?> report(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=test-report.md").contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8")).body(reviews.report(submission,member,run));}
    @PostMapping("/{run}/start") public ResponseEntity<?> start(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run){return ok(service.transition(submission,member,run,false));}
    @PostMapping("/{run}/cancel") public ResponseEntity<?> cancel(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run){return ok(service.transition(submission,member,run,true));}
    @ExceptionHandler(ProductDescriptionException.class) public ResponseEntity<?> failure(ProductDescriptionException error){return ResponseEntity.status(error.status()).body(Map.of("code",error.code(),"message",error.getMessage()));}
}
