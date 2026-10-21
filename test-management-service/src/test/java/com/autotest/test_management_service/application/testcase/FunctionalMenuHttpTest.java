package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.autotest.test_management_service.application.crawler.PlaywrightSetupService;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FunctionalMenuHttpTest {
    @Test void largeGenerationRequestDoesNotAttemptUnsupportedHttpUpgrade() throws Exception {
        var upgrade=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/functional-menus/generate",exchange->{
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            exchange.getRequestBody().readAllBytes();
            byte[] result="{\"rows\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,result.length);exchange.getResponseBody().write(result);exchange.close();
        });server.start();
        var mapper=new ObjectMapper();
        var service=new FunctionalMenuService(mock(JdbcTemplate.class),mapper,mock(SubmissionPersistenceService.class),mock(SubmissionRepository.class),
            mock(PlaywrightSetupService.class),mock(FunctionalMenuValidator.class),mock(FunctionalMenuExcel.class),mock(PlatformTransactionManager.class),
            "http://127.0.0.1:"+server.getAddress().getPort(),"test-only-token");
        try {
            var method=FunctionalMenuService.class.getDeclaredMethod("post",String.class,com.fasterxml.jackson.databind.JsonNode.class,Duration.class);
            method.setAccessible(true);
            var result=(com.fasterxml.jackson.databind.JsonNode)method.invoke(service,"generate",mapper.createObjectNode().put("text","가".repeat(50000)),Duration.ofSeconds(10));
            assertTrue(result.path("rows").isArray());assertNull(upgrade.get());
        } finally {service.close();server.stop(0);}
    }
}
