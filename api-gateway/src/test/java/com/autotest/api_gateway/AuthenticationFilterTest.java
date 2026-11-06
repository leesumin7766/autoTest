package com.autotest.api_gateway;

import com.autotest.common_lib.auth.AccessTokens;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationFilterTest {
    final AccessTokens tokens=new AccessTokens("test-only-secret-at-least-thirty-two-bytes-long");
    final AuthenticationFilter filter=new AuthenticationFilter(tokens);
    @Test void missingOrExpiredTokenCannotSpoofMember() {
        for(String token:List.of("",tokens.issue(1,1,List.of("ADMIN"),Instant.now().minusSeconds(1800)))) {
            var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/submissions").header("X-Member-Id","1").header("Authorization","Bearer "+token));
            filter.filter(exchange,e->{fail("must reject");return Mono.empty();}).block();
            assertEquals(401,exchange.getResponse().getStatusCode().value());
            assertTrue(exchange.getResponse().getBodyAsString().block().contains("AUTH_REQUIRED"));
        }
    }
    @Test void bearerOverwritesAllForgedIdentityHeaders() {
        String token=tokens.issue(2,42,List.of("USER"),Instant.now());
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/submissions").header("Authorization","Bearer "+token)
            .header("X-Member-Id","1","2").header("X-Internal-Token","forged"));
        var called=new AtomicBoolean();
        filter.filter(exchange,e->{called.set(true);assertEquals(List.of("42"),e.getRequest().getHeaders().get("X-Member-Id"));
            assertNull(e.getRequest().getHeaders().getFirst("X-Internal-Token"));return Mono.empty();}).block();
        assertTrue(called.get());
    }
    @Test void exactPublicPathsStripIdentityButPreserveAgentSignature() {
        for(String path:List.of("/api/auth/login","/api/auth/refresh","/api/crawl-agents/agent-1/channel/poll")) {
            var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(path).header("X-Member-Id","1")
                .header("X-Internal-Token","secret").header("X-Agent-Nonce","nonce"));
            var called=new AtomicBoolean();
            filter.filter(exchange,e->{called.set(true);assertNull(e.getRequest().getHeaders().getFirst("X-Member-Id"));
                assertNull(e.getRequest().getHeaders().getFirst("X-Internal-Token"));assertEquals("nonce",e.getRequest().getHeaders().getFirst("X-Agent-Nonce"));return Mono.empty();}).block();
            assertTrue(called.get());
        }
        for(String path:List.of("/api/crawl-agents","/api/crawl-agents/a/revoke","/api/auth/users","/api/auth/login/extra")) {
            var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(path));
            filter.filter(exchange,e->{fail();return Mono.empty();}).block();
            assertEquals(401,exchange.getResponse().getStatusCode().value());
        }
    }
    @Test void cookieMutationsRequireWebHeaderAndRejectNullOrigin() {
        String token=tokens.issue(1,1,List.of("ADMIN"),Instant.now());
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/submissions").cookie(new org.springframework.http.HttpCookie("autotest_access",token)));
        filter.filter(exchange,e->{fail();return Mono.empty();}).block();
        assertEquals(403,exchange.getResponse().getStatusCode().value());
        var nullOrigin=MockServerWebExchange.from(MockServerHttpRequest.post("/api/submissions").cookie(new org.springframework.http.HttpCookie("autotest_access",token))
            .header("X-AutoTest-Web","1").header("Origin","null"));
        filter.filter(nullOrigin,e->{fail();return Mono.empty();}).block();
        assertEquals(403,nullOrigin.getResponse().getStatusCode().value());
        var valid=MockServerWebExchange.from(MockServerHttpRequest.post("/api/submissions").cookie(new org.springframework.http.HttpCookie("autotest_access",token)).header("X-AutoTest-Web","1"));
        var passed=new AtomicBoolean();filter.filter(valid,e->{passed.set(true);return Mono.empty();}).block();assertTrue(passed.get());
    }
    @Test void rateLimitCannotBeBypassedWithClientForwardingHeaders() {
        for(int i=0;i<61;i++) {
            var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login").header("X-Auth-Client-IP","10.0.0."+i));
            filter.filter(exchange,e->Mono.empty()).block();
            if(i==60)assertEquals(429,exchange.getResponse().getStatusCode().value());
            else assertNull(exchange.getResponse().getStatusCode());
        }
    }
}
