package com.autotest.test_management_service.presentation.controller;

import com.autotest.test_management_service.application.testcase.MenuTcService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/submissions/{submission}/menu-test-cases")
public class MenuTcController {
    private final MenuTcService service;
    public MenuTcController(MenuTcService service) { this.service=service; }
    public record Generate(UUID menuJobId,Map<String,String> featureTypes,String loginUrl) {}
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    @GetMapping public ResponseEntity<?> status(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestParam(required=false) UUID set){return ok(service.view(submission,member,set));}
    @GetMapping("/{set}/cases/{caseId}/read-bindings") public ResponseEntity<?> readBindings(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID set,@PathVariable String caseId){return ok(service.readBindings(submission,member,set,caseId));}
    @PutMapping("/{set}/cases/{caseId}/read-bindings") public ResponseEntity<?> saveReadBindings(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID set,@PathVariable String caseId,@RequestBody com.fasterxml.jackson.databind.JsonNode body){return ok(service.saveReadBindings(submission,member,set,caseId,body));}
    @PostMapping public ResponseEntity<?> generate(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestBody Generate body){var created=service.create(submission,member,body.menuJobId(),body.featureTypes()==null?Map.of():body.featureTypes(),body.loginUrl());com.autotest.test_management_service.application.testcase.TcExecutionDecisions.decorate(created.path("set").path("output"));return ok(created);}
    @GetMapping("/{set}/download") public ResponseEntity<byte[]> download(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID set){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=test-cases.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(service.download(submission,member,set));}

    @PutMapping("/{set}/decisions") public ResponseEntity<?> saveDecisions(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID set,@RequestBody com.fasterxml.jackson.databind.JsonNode body){return ok(service.saveDecisions(submission,member,set,body));}
}
