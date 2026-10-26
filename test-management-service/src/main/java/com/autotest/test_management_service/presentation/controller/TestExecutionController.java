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
    private final com.autotest.test_management_service.application.crawler.PlaywrightSetupService setups;
    private final ExecutionReviewService reviews;
    private final com.autotest.test_management_service.application.testcase.ExecutionReadinessService readiness;
    public record Prepare(UUID tcSetId,String targetUrl,String notes,List<String> caseIds,boolean allowMutation,String productVersion){}
    public record Review(String caseId,int revision,String result,String reason,String disposition){}
    public record Retry(List<String> caseIds,String productVersion){}
    private ResponseEntity<?> ok(Object value){if(value instanceof com.fasterxml.jackson.databind.node.ObjectNode root&&root.has("executorConnected"))root.setAll(readiness.status());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping public ResponseEntity<?> status(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestParam(required=false) UUID run){return ok(service.status(submission,member,run));}
    @GetMapping("/{run}/evidence") public ResponseEntity<?> evidence(@PathVariable UUID submission,@RequestHeader("X-Member-Id")long member,@PathVariable UUID run,@RequestParam String caseId){return ok(service.evidence(submission,member,run,caseId));}
    @PostMapping public ResponseEntity<?> prepare(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestBody Prepare input){return ok(service.prepare(submission,member,input.tcSetId(),input.targetUrl(),input.notes(),input.caseIds(),input.allowMutation(),input.productVersion()));}
    @PostMapping("/{run}/review") public ResponseEntity<?> review(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run,@RequestBody Review input){return ok(reviews.review(submission,member,run,input.caseId(),input.revision(),input.result(),input.reason(),input.disposition()));}
    @PostMapping("/{run}/retry") public ResponseEntity<?> retry(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run,@RequestBody Retry input){return ok(reviews.retry(submission,member,run,input.caseIds(),input.productVersion()));}
    @PostMapping("/{run}/resume") public ResponseEntity<?> resume(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run){return ok(reviews.resume(submission,member,run));}
    @GetMapping("/{run}/report") public ResponseEntity<?> report(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=test-report.md").contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8")).body(reviews.report(submission,member,run));}
    @PostMapping("/{run}/start") public ResponseEntity<?> start(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run){var current=service.status(submission,member,run);if(!"PREPARED".equals(current.path("run").path("status").asText()))return ok(service.transition(submission,member,run,false));var setup=setups.get(submission,member);
        if(setup!=null){
            if(setup.stale()||!setup.targetUrl().equals(current.path("run").path("snapshot").path("targetUrl").asText()))throw new ProductDescriptionException(HttpStatus.CONFLICT,"EXECUTION_LOGIN_SETUP_MISMATCH","시험 대상 URL과 화면 수집에 저장한 접속 설정이 다릅니다. 화면 수집 탭에서 설정을 확인하세요.");
            if(!Set.of("NONE","FORM_AUTO","BASIC_AUTH").contains(setup.loginMethod()))throw new ProductDescriptionException(HttpStatus.CONFLICT,"EXECUTION_LOGIN_UNSUPPORTED","이 로그인 방식은 자동 시험을 지원하지 않습니다. 자동 로그인 설정을 확인하세요.");
        }
        readiness.requireReady();return ok(service.transition(submission,member,run,false));}
    @PostMapping("/{run}/cancel") public ResponseEntity<?> cancel(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID run){return ok(service.transition(submission,member,run,true));}

}
