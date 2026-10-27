package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;

class ExecutionReadinessServiceTest {
    @Test void checksBothProtocolsModelAndJudgeInsteadOfRootHealth() throws Exception {
        var ai=new java.util.concurrent.atomic.AtomicReference<>("{}");
        var browser=new java.util.concurrent.atomic.AtomicReference<>("{}");
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            assertEquals("test-token",exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            byte[] bytes=(exchange.getRequestURI().getPath().contains("tc-browser")?browser:ai).get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try{
            String url="http://127.0.0.1:"+server.getAddress().getPort();
            var missing=new ExecutionReadinessService(new ObjectMapper(),url,url,"test-token");
            assertFalse(missing.status().path("executorConnected").asBoolean());
            assertEquals(2,missing.status().path("readinessIssues").size());
            assertThrows(ProductDescriptionException.class,missing::requireReady);
            ai.set("{\"protocolVersion\":\"tc-execution-v2\",\"plannerAvailable\":false,\"judgeConfigured\":true}");
            browser.set("{\"protocolVersion\":\"tc-browser-v2\",\"browserAvailable\":false}");
            var missingBinary=new ExecutionReadinessService(new ObjectMapper(),url,url,"test-token");
            assertFalse(missingBinary.status().path("executorConnected").asBoolean());
            assertEquals("EXECUTION_BROWSER_UNAVAILABLE",missingBinary.status().path("readinessIssues").get(1).path("code").asText());
            browser.set("{\"protocolVersion\":\"tc-browser-v2\",\"browserAvailable\":true}");
            var noModel=new ExecutionReadinessService(new ObjectMapper(),url,url,"test-token");
            assertFalse(noModel.status().path("executorConnected").asBoolean());
            assertEquals("EXECUTION_MODEL_UNAVAILABLE",noModel.status().path("readinessIssues").get(0).path("code").asText());
            ai.set("{\"protocolVersion\":\"tc-execution-v2\",\"plannerAvailable\":true,\"judgeConfigured\":false}");
            var noJudge=new ExecutionReadinessService(new ObjectMapper(),url,url,"test-token");
            assertTrue(noJudge.status().path("executorConnected").asBoolean());
            assertFalse(noJudge.status().path("judgeConnected").asBoolean());
            assertThrows(ProductDescriptionException.class,noJudge::requireReady);
            ai.set("{\"protocolVersion\":\"tc-execution-v2\",\"plannerAvailable\":true,\"judgeConfigured\":true}");
            var ready=new ExecutionReadinessService(new ObjectMapper(),url,url,"test-token");
            ready.requireReady();assertTrue(ready.status().path("readinessIssues").isEmpty());
            var snapshot=new ObjectMapper().createObjectNode();
            snapshot.putArray("items").addObject().put("executionStatus","PENDING").putObject("tc").putObject("executionPlan").put("specificationVersion",TcExecutionSpecification.VERSION);
            assertThrows(ProductDescriptionException.class,()->ready.requireSpecifications(snapshot));
            ai.set("{\"protocolVersion\":\"tc-execution-v2\",\"plannerAvailable\":true,\"judgeConfigured\":true,\"specificationVersions\":[\"execution-spec-v2-read\"]}");
            var withSpec=new ExecutionReadinessService(new ObjectMapper(),url,url,"test-token");
            assertDoesNotThrow(()->withSpec.requireSpecifications(snapshot));
        }finally{server.stop(0);}
    }
}
