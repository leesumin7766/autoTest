package com.autotest.api_gateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import static org.junit.jupiter.api.Assertions.*;

class GatewayExceptionHandlerTest {
    @Test void unavailableUpstreamAndTimeoutHaveSafeResponses() {
        for(Throwable cause:new Throwable[]{new java.net.ConnectException("private-host:8082 secret"),new java.util.concurrent.TimeoutException("secret")}) {
            var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/submissions"));
            new GatewayExceptionHandler(new ObjectMapper()).handle(exchange,new RuntimeException(cause)).block();
            assertEquals(cause instanceof java.net.ConnectException?503:504,exchange.getResponse().getStatusCode().value());
            String body=exchange.getResponse().getBodyAsString().block();assertFalse(body.contains("secret"));assertTrue(body.contains("traceId"));
        }
    }
}
