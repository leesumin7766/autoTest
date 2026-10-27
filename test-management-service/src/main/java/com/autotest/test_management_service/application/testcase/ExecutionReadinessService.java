package com.autotest.test_management_service.application.testcase;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;

/** API presence, compatible protocol, installed local model and judge configuration; never a product verdict. */
@Service
public class ExecutionReadinessService {
    private final ObjectMapper mapper;private final String ai,crawler,token;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
    private ObjectNode cached;private long checked;
    public ExecutionReadinessService(ObjectMapper mapper,@Value("${ai-service.base-url:http://ai-service:8005}")String ai,
            @Value("${crawler-service.base-url:http://crawler-service:8006}")String crawler,@Value("${product-description.internal-token:}")String token){this.mapper=mapper;this.ai=ai;this.crawler=crawler;this.token=token;}
    private CompletableFuture<JsonNode> probe(String url){
        try{return http.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).header("X-Internal-Token",token).GET().build(),HttpResponse.BodyHandlers.ofString())
                .thenApply(response->{try{return response.statusCode()==200?mapper.readTree(response.body()):mapper.createObjectNode();}catch(Exception invalid){return mapper.createObjectNode();}}).exceptionally(error->mapper.createObjectNode());}
        catch(Exception invalid){return CompletableFuture.completedFuture(mapper.createObjectNode());}
    }
    public synchronized ObjectNode status(){
        if(cached!=null&&System.currentTimeMillis()-checked<5000)return cached.deepCopy();
        var planner=probe(ai+"/api/v1/test-executions/capabilities");var browser=probe(crawler+"/api/v1/tc-browser/capabilities");
        CompletableFuture.allOf(planner,browser).join();var p=planner.join();var b=browser.join();
        boolean aiReady="tc-execution-v2".equals(p.path("protocolVersion").asText());
        boolean browserApi="tc-browser-v2".equals(b.path("protocolVersion").asText());
        boolean browserReady=browserApi&&b.path("browserAvailable").asBoolean();
        boolean model=aiReady&&p.path("plannerAvailable").asBoolean();boolean judge=aiReady&&p.path("judgeConfigured").asBoolean();
        var result=mapper.createObjectNode().put("executorConnected",aiReady&&browserReady&&model).put("judgeConnected",judge).put("planner",p.path("model").asText("qwen3:4b")).put("judge","Jev").put("readinessCheckedAt",Instant.now().toString());
        result.set("executionSpecificationVersions",p.path("specificationVersions").isArray()?p.path("specificationVersions").deepCopy():mapper.createArrayNode());
        var issues=result.putArray("readinessIssues");
        result.put("executorBusy",b.path("busy").asBoolean()).put("browserConnected",browserReady);
        if(!aiReady)issues.addObject().put("code","EXECUTION_AI_API_UNAVAILABLE").put("message","AI 서버의 시험 실행 API가 없거나 버전이 다릅니다. 현재 작업 버전으로 AI 서비스를 반영해야 합니다.");
        else if(!model)issues.addObject().put("code","EXECUTION_MODEL_UNAVAILABLE").put("message","로컬 Qwen 모델을 사용할 수 없습니다. Ollama 연결과 설치 모델을 확인하세요.");
        if(!browserApi)issues.addObject().put("code","EXECUTION_BROWSER_API_UNAVAILABLE").put("message","Playwright 실행 API가 없거나 버전이 다릅니다. 크롤러 서비스를 현재 버전으로 반영해야 합니다.");
        else if(!browserReady)issues.addObject().put("code","EXECUTION_BROWSER_UNAVAILABLE").put("message","Playwright와 Chromium 설치 버전이 맞지 않습니다. 잠금 파일을 기준으로 크롤러 이미지를 다시 빌드하세요.");
        if(aiReady&&!judge)issues.addObject().put("code","EXECUTION_JUDGE_UNCONFIGURED").put("message","Jev 판정 설정이 없습니다. 판정 서비스 설정을 확인하세요.");
        cached=result;checked=System.currentTimeMillis();return result.deepCopy();
    }
    public void requireReady(){var value=status();if(value.path("executorBusy").asBoolean())throw new ProductDescriptionException(HttpStatus.CONFLICT,"EXECUTION_BROWSER_BUSY","화면 수집 또는 다른 시험이 브라우저를 사용 중입니다. 해당 작업 종료 후 시작하세요.");if(!value.path("executorConnected").asBoolean()||!value.path("judgeConnected").asBoolean())throw new ProductDescriptionException(HttpStatus.SERVICE_UNAVAILABLE,"EXECUTION_NOT_READY","실행기·모델·판정 서비스가 준비되지 않았습니다. 시험 실행 화면의 연결 상태를 확인하세요.");}
    public void requireSpecifications(JsonNode snapshot){
        var supported=status().path("executionSpecificationVersions");
        for(var item:snapshot.path("items")){
            String version=item.path("tc").path("executionPlan").path("specificationVersion").asText();
            if(!"PENDING".equals(item.path("executionStatus").asText())||version.isBlank()||"execution-spec-v1".equals(version))continue;
            boolean found=false;for(var value:supported)if(version.equals(value.asText()))found=true;
            if(!found)throw new ProductDescriptionException(HttpStatus.SERVICE_UNAVAILABLE,"EXECUTION_SPEC_VERSION_UNSUPPORTED","실행기가 이 TC의 실행 명세를 지원하지 않습니다. 실행 서비스를 최신 버전으로 반영해 주세요.");
        }
    }
}
