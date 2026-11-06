package com.autotest.api_gateway;

import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeyFilterTest {
    static final String KEY = "atk_" + "A".repeat(43);
    static final String OTHER = "atk_" + "B".repeat(43);
    final ApiKeyFilter filter = new ApiKeyFilter("hub-kim:42:" + HexFormat.of().formatHex(ApiKeyFilter.sha256(KEY))
            + ", hub-lee:7:" + HexFormat.of().formatHex(ApiKeyFilter.sha256(OTHER)));

    private ServerHttpRequest pass(MockServerHttpRequest.BaseBuilder<?> builder) {
        var exchange = MockServerWebExchange.from(builder);
        var seen = new AtomicReference<ServerHttpRequest>();
        filter.filter(exchange, e -> { seen.set(e.getRequest()); return Mono.empty(); }).block();
        assertNotNull(seen.get(), "request should pass, got " + exchange.getResponse().getStatusCode());
        return seen.get();
    }

    private String reject(MockServerHttpRequest.BaseBuilder<?> builder) {
        var exchange = MockServerWebExchange.from(builder);
        filter.filter(exchange, e -> { fail("must reject"); return Mono.empty(); }).block();
        assertEquals(401, exchange.getResponse().getStatusCode().value());
        assertEquals("Bearer", exchange.getResponse().getHeaders().getFirst("WWW-Authenticate"));
        return exchange.getResponse().getBodyAsString().block();
    }

    @Test void validKeyReplacesForgedIdentityAndDropsKey() {
        var request = pass(MockServerHttpRequest.post("/api/submissions").header("Authorization", "Bearer " + OTHER)
                .header("X-Member-Id", "1", "2").header("X-Internal-Token", "forged").header("X-Api-Client", "admin"));
        assertEquals(List.of("7"), request.getHeaders().get("X-Member-Id"));
        assertEquals(List.of("hub-lee"), request.getHeaders().get("X-Api-Client"));
        assertNull(request.getHeaders().getFirst("X-Internal-Token"));
        assertNull(request.getHeaders().getFirst("Authorization"));
    }

    @Test void anyAuthorizationHeaderMustBeValid() {
        for (String value : List.of("Bearer ", "Bearer atk_" + "C".repeat(43), "Bearer " + KEY + "x", "Basic " + KEY, KEY))
            assertTrue(reject(MockServerHttpRequest.get("/api/submissions").header("Authorization", value)).contains("API_KEY_INVALID"), value);
    }

    @Test void hubPathRequiresKey() {
        assertTrue(reject(MockServerHttpRequest.get("/api/v1/hub/whoami").header("X-Member-Id", "42")).contains("API_KEY_REQUIRED"));
        assertEquals("42", pass(MockServerHttpRequest.get("/api/v1/hub/whoami").header("Authorization", "Bearer " + KEY))
                .getHeaders().getFirst("X-Member-Id"));
    }

    @Test void webRequestsWithoutKeyStillPassButLoseInternalHeaders() {
        var request = pass(MockServerHttpRequest.get("/api/submissions").header("X-Member-Id", "3")
                .header("X-Internal-Token", "forged").header("X-Api-Client", "hub-kim"));
        assertEquals("3", request.getHeaders().getFirst("X-Member-Id"));
        assertNull(request.getHeaders().getFirst("X-Internal-Token"));
        assertNull(request.getHeaders().getFirst("X-Api-Client"));
        var agent = pass(MockServerHttpRequest.post("/api/crawl-agents/a1/channel/poll").header("X-Agent-Nonce", "n"));
        assertEquals("n", agent.getHeaders().getFirst("X-Agent-Nonce"));
    }

    @Test void noConfiguredKeysRejectsEveryBearer() {
        var empty = new ApiKeyFilter("");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/submissions").header("Authorization", "Bearer " + KEY));
        empty.filter(exchange, e -> { fail(); return Mono.empty(); }).block();
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test void malformedConfigFailsStartup() {
        String hash = HexFormat.of().formatHex(ApiKeyFilter.sha256(KEY));
        for (String config : List.of("hub", "hub:42", "Hub:42:" + hash, "hub:0:" + hash, "hub:42:" + hash.toUpperCase(),
                "hub:42:" + hash + ",hub:43:" + hash, "hub:42:" + KEY))
            assertThrows(IllegalStateException.class, () -> ApiKeyFilter.parse(config), config);
        assertEquals(2, ApiKeyFilter.parse(" a:1:" + hash + " ,, b-2:2:" + hash + " ").size());
    }
}
