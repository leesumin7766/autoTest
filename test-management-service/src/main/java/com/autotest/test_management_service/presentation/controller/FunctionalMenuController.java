package com.autotest.test_management_service.presentation.controller;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.autotest.test_management_service.application.testcase.FunctionalMenuService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/submissions/{submission}/functional-menu")
public class FunctionalMenuController {
    private final FunctionalMenuService service;
    private final com.autotest.test_management_service.application.testcase.UploadedMenuService uploads;
    public record Start(UUID crawlJobId,String productName,String mode){}
    public record Edit(int revision,JsonNode rows,String requestId){}
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    @GetMapping public ResponseEntity<?> status(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestParam(required=false) UUID job){return ok(service.status(submission,member,job));}
    @PostMapping public ResponseEntity<?> start(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestBody Start request){return ok(service.start(submission,member,request.crawlJobId(),request.productName(),request.mode()==null?"AUTO":request.mode()));}
    @PostMapping(value="/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<?> upload(@PathVariable UUID submission,@RequestHeader("X-Member-Id") long member,@RequestParam("file") org.springframework.web.multipart.MultipartFile file,@RequestParam(required=false) String productName) throws java.io.IOException{return ok(uploads.upload(submission,member,file.getOriginalFilename(),file.getBytes(),productName));}
    @PostMapping("/{job}/prompt") public ResponseEntity<?> prompt(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member){return ok(service.prompt(submission,member,job));}
    @GetMapping("/{job}/evidence") public ResponseEntity<?> evidence(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member,@RequestParam String sourceId){return ok(service.evidence(submission,member,job,sourceId));}
    @PostMapping("/{job}/import") public ResponseEntity<?> imported(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member,@RequestBody Edit request){return ok(service.imported(submission,member,job,request.revision(),request.rows(),request.requestId()));}
    @PutMapping("/{job}/draft") public ResponseEntity<?> save(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member,@RequestBody Edit request){return ok(service.edit(submission,member,job,request.revision(),request.rows(),false));}
    @PutMapping("/{job}/approval") public ResponseEntity<?> approve(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member,@RequestBody Edit request){return ok(service.edit(submission,member,job,request.revision(),request.rows(),true));}
    @PostMapping("/{job}/cancel") public ResponseEntity<?> cancel(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member){service.cancel(submission,member,job);return ok(service.status(submission,member,job));}
    @GetMapping("/{job}/download") public ResponseEntity<byte[]> download(@PathVariable UUID submission,@PathVariable UUID job,@RequestHeader("X-Member-Id") long member,@RequestParam(defaultValue="false") boolean draft){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=menu-tree.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(service.download(submission,member,job,draft));}

}
