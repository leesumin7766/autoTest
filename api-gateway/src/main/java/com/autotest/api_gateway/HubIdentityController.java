package com.autotest.api_gateway;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 허브가 키 설정을 확인하는 용도. ApiKeyFilter를 통과한 요청만 도달한다. */
@RestController
public class HubIdentityController {
    @GetMapping("/api/v1/hub/whoami")
    public ResponseEntity<Map<String, String>> whoami(@RequestHeader(ApiKeyFilter.CLIENT_HEADER) String client,
                                                      @RequestHeader("X-Member-Id") String memberId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("client", client, "memberId", memberId));
    }
}
