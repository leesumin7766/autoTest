package com.autotest.test_management_service.presentation.controller;

import com.autotest.test_management_service.application.service.SubmissionWorkspaceService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionWorkspaceController {
    private final SubmissionWorkspaceService service;
    public SubmissionWorkspaceController(SubmissionWorkspaceService service){this.service=service;}
    public record Update(String name,String lastPage){}
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    @GetMapping("/workspaces") public ResponseEntity<?> list(@RequestHeader("X-Member-Id")long member,
        @RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="20")int limit){
        if(limit<1||limit>50)throw new com.autotest.common_lib.exception.CommonException(com.autotest.common_lib.exception.CommonErrorCode.INVALID_INPUT_VALUE);
        var items=service.list(member,offset,limit+1);
        // A fixed UI page of 20 uses the extra item to detect further results.
        return ok(Map.of("workspaces",items.stream().limit(limit).toList(),"hasMore",items.size()>limit));
    }
    @GetMapping("/{id}/workspace") public ResponseEntity<?> get(@PathVariable UUID id,@RequestHeader("X-Member-Id")long member){return ok(service.get(id,member));}
    @PatchMapping("/{id}/workspace") public ResponseEntity<?> update(@PathVariable UUID id,@RequestHeader("X-Member-Id")long member,@RequestBody Update input){return ok(service.update(id,member,input.name(),input.lastPage()));}
}
