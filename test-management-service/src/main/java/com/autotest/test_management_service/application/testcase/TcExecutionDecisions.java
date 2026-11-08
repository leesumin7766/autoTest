package com.autotest.test_management_service.application.testcase;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;

/**
 * TC 수행 방식(2026-11-08): 자동(AUTO) · 수동(MANUAL) · 제외(EXCLUDED).
 * TC 본문(16열)은 바꾸지 않고 TC 옆에 executionDecision 으로 저장한다. 저장하지 않은 TC 는 템플릿 표시로 기본값을 정한다.
 */
public final class TcExecutionDecisions {
    public static final String AUTO="AUTO", MANUAL="MANUAL", EXCLUDED="EXCLUDED";
    public static final Set<String> MODES=Set.of(AUTO,MANUAL,EXCLUDED);
    static final int MAX_REASON=500, MAX_DECISIONS=3000;
    /** 브라우저 화면만으로 판정할 수 없다고 템플릿이 표시한 TC(문서·도구·추가 증거) */
    static final Set<String> MANUAL_BY_TEMPLATE=Set.of("MANUAL_OR_TOOL","ADDITIONAL_EVIDENCE");

    private TcExecutionDecisions() {}

    public static String defaultMode(JsonNode tc) {
        return MANUAL_BY_TEMPLATE.contains(tc.path("automationMode").asText())?MANUAL:AUTO;
    }

    public static String mode(JsonNode tc) {
        String saved=tc.path("executionDecision").path("mode").asText("");
        return MODES.contains(saved)?saved:defaultMode(tc);
    }

    /** 화면용: 각 TC 에 decision{mode,source,reason,...} 과 집계를 붙인다. 저장본에는 쓰지 않는다 */
    public static void decorate(JsonNode output) {
        if(!(output instanceof ObjectNode root))return;
        Map<String,Integer> counts=new LinkedHashMap<>(Map.of(AUTO,0,MANUAL,0,EXCLUDED,0));
        for(var node:root.path("testCases")) {
            if(!(node instanceof ObjectNode tc))continue;
            String mode=mode(tc);counts.merge(mode,1,Integer::sum);
            var saved=tc.path("executionDecision");
            var view=tc.putObject("decision").put("mode",mode).put("defaultMode",defaultMode(tc)).put("source",saved.isObject()?"USER":"DEFAULT");
            if(saved.isObject()){view.put("reason",saved.path("reason").asText(""));view.put("decidedAt",saved.path("decidedAt").asText(""));}
        }
        var summary=root.putObject("decisionSummary");counts.forEach(summary::put);
        root.put("decisionRevision",root.path("decisionRevision").asInt(0));
    }

    /**
     * 요청 {revision, decisions:[{caseId, mode, reason}]} 를 검증해 output 에 반영한다(일부만 보내도 된다).
     * 제외는 사유가 필요하다. 기본값과 같은 방식으로 사유 없이 저장하면 기록을 지워 기본값으로 되돌린다.
     */
    public static void apply(ObjectNode output,JsonNode request,long member) {
        if(request==null||!request.path("revision").isIntegralNumber()||request.path("revision").asInt(-1)!=output.path("decisionRevision").asInt(0))
            throw conflict("TC_DECISION_CONFLICT","다른 화면에서 수행 방식을 변경했습니다. 다시 불러온 뒤 저장하세요.");
        var decisions=request.path("decisions");
        if(!decisions.isArray()||decisions.isEmpty()||decisions.size()>MAX_DECISIONS)throw invalid("수행 방식을 바꿀 TC를 선택하세요.");
        Map<String,ObjectNode> cases=new HashMap<>();
        for(var node:output.path("testCases"))if(node instanceof ObjectNode tc)cases.put(tc.path("id").asText(),tc);
        Map<String,JsonNode> wanted=new LinkedHashMap<>();
        for(var decision:decisions) {
            String id=decision.path("caseId").asText(""),mode=decision.path("mode").asText("");
            String reason=decision.path("reason").isTextual()?decision.path("reason").asText().strip():"";
            if(!cases.containsKey(id))throw new ProductDescriptionException(HttpStatus.NOT_FOUND,"TC_CASE_NOT_FOUND","TC를 찾을 수 없습니다.");
            if(!MODES.contains(mode))throw invalid("수행 방식은 자동·수동·제외 중 하나입니다.");
            if(reason.length()>MAX_REASON)throw invalid("사유는 500자 이내로 적어 주세요.");
            if(EXCLUDED.equals(mode)&&reason.isEmpty())throw invalid("제외하는 TC는 제외 사유가 필요합니다. 예: 다국어를 제공하지 않는 제품");
            wanted.put(id,decision);
        }
        String now=Instant.now().toString();
        wanted.forEach((id,decision)->{
            var tc=cases.get(id);String mode=decision.path("mode").asText();
            String reason=decision.path("reason").isTextual()?decision.path("reason").asText().strip():"";
            if(mode.equals(defaultMode(tc))&&reason.isEmpty()){tc.remove("executionDecision");return;}
            tc.putObject("executionDecision").put("mode",mode).put("reason",reason).put("decidedBy",member).put("decidedAt",now);
        });
        output.put("decisionRevision",output.path("decisionRevision").asInt(0)+1);
    }

    /** 저장 전 화면용 필드를 지운다(decorate 결과가 저장본에 섞이지 않게) */
    static void strip(ObjectNode output) {
        output.remove("decisionSummary");
        for(var node:output.path("testCases"))if(node instanceof ObjectNode tc)tc.remove("decision");
    }

    private static ProductDescriptionException conflict(String code,String message){return new ProductDescriptionException(HttpStatus.CONFLICT,code,message);}
    private static ProductDescriptionException invalid(String message){return new ProductDescriptionException(HttpStatus.BAD_REQUEST,"TC_DECISION_INVALID",message);}
}
