package com.autotest.test_management_service.application.crawler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class LocalCrawlDispatcher {
    private final String baseUrl,token;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    public LocalCrawlDispatcher(@Value("${crawler-service.base-url:http://crawler-service:8006}") String baseUrl,
            @Value("${product-description.internal-token:}") String token) {this.baseUrl=baseUrl;this.token=token;}
    public void dispatch(JsonNode job,Consumer<String> failure) {
        if(token.isBlank()) {failure.accept("LOCAL_RUNNER_UNAVAILABLE");return;}
        try {
            var request=HttpRequest.newBuilder(URI.create(baseUrl+"/api/v1/browser-jobs")).timeout(Duration.ofSeconds(10))
                    .header("Content-Type","application/json").header("X-Internal-Token",token)
                    .POST(HttpRequest.BodyPublishers.ofString(job.toString())).build();
            http.sendAsync(request,HttpResponse.BodyHandlers.discarding()).whenComplete((response,error)->{
                if(error!=null || response.statusCode()!=202) failure.accept("LOCAL_RUNNER_UNAVAILABLE");
            });
        } catch(Exception unavailable) {failure.accept("LOCAL_RUNNER_UNAVAILABLE");}
    }
}
