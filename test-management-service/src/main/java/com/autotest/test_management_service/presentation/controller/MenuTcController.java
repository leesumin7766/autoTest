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
    public record Generate(UUID menuJobId,Map<String,String> featureTypes) {}
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    @GetMapping public ResponseEntity<?> status(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestParam(required=false) UUID set){return ok(service.status(submission,member,set));}
    @PostMapping public ResponseEntity<?> generate(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestBody Generate body){return ok(service.create(submission,member,body.menuJobId(),body.featureTypes()==null?Map.of():body.featureTypes()));}
    @GetMapping("/{set}/download") public ResponseEntity<byte[]> download(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@PathVariable UUID set){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=test-cases.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(service.download(submission,member,set));}
    @ExceptionHandler(ProductDescriptionException.class) public ResponseEntity<?> failure(ProductDescriptionException error){return ResponseEntity.status(error.status()).body(Map.of("code",error.code(),"message",error.getMessage()));}
}
