package com.autotest.test_management_service.presentation.controller;

import com.autotest.test_management_service.application.service.ManualReviewService;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/submissions/{id}/manual-reviews")
public class ManualReviewController {
    private final ManualReviewService service;
    public ManualReviewController(ManualReviewService service){this.service=service;}
    @GetMapping public ResponseEntity<?> get(@PathVariable UUID id,@RequestHeader("X-Member-Id")long member,
            @RequestParam(required=false)UUID job){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id,member,job));}
    @PostMapping public ResponseEntity<?> start(@PathVariable UUID id,@RequestHeader("X-Member-Id")long member){
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(service.start(id,member));
    }
    @PostMapping("/{job}/pause") public ResponseEntity<?> pause(@PathVariable UUID id,@PathVariable UUID job,@RequestHeader("X-Member-Id")long member){
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(service.pause(id,member,job));
    }
    @PostMapping("/{job}/resume") public ResponseEntity<?> resume(@PathVariable UUID id,@PathVariable UUID job,@RequestHeader("X-Member-Id")long member){
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(service.resume(id,member,job));
    }
}
