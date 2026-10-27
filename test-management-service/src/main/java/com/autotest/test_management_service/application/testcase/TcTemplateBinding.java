package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Bind reviewed recipes to product context without inventing selectors or oracle values. */
final class TcTemplateBinding {
    static void bind(ObjectMapper mapper, ObjectNode tc, JsonNode feature, JsonNode template, String group) {
        String name=feature.path("functionName").asText(feature.path("description").asText());
        String path=join(feature.path("featurePath"));
        tc.set("templateOriginal",template.deepCopy());
        tc.put("templateRuleId",template.path("ruleId").asText());
        var plan=(ObjectNode)tc.path("executionPlan");
        var selection=plan.putObject("selection").put("status","PENDING_LLM").put("policy","KEEP_ALL_TCS")
            .put("requiredOperation",template.path("requiredOperation").asText()).put("scopeOperation",operation(name)).put("scopeName",name);
        String required=selection.path("requiredOperation").asText(),actual=selection.path("scopeOperation").asText();
        if(Set.of("FEATURE-01","CRUD-01").contains(tc.path("ruleId").asText())&&!actual.isBlank()) {
            String procedure=switch(actual){
                case "CREATE"->"시험용 유효값을 입력해 등록하고, 다시 조회해 생성된 값과 입력값을 비교한다.";
                case "UPDATE"->"식별한 시험 데이터의 값을 수정하고, 새로 조회해 변경값과 비교한다.";
                case "DELETE"->"삭제 전용 시험 데이터를 식별해 삭제하고, 재조회하여 해당 데이터와 다른 데이터의 상태를 확인한다.";
                case "READ"->"정해진 조회 조건으로 기능을 수행하고, 표시된 결과를 조건별 기대 데이터와 비교한다.";
                default->"허용된 시험 파일을 업로드하고, 처리 결과와 저장된 데이터를 기대 결과와 비교한다.";
            };
            String expected=switch(actual){
                case "CREATE"->"시험 데이터가 입력값대로 생성되고 재조회한 값이 일치한다. 기존의 다른 데이터는 유지된다.";
                case "UPDATE"->"대상 데이터만 변경값대로 저장되고 재조회한 값이 일치한다. 다른 데이터는 유지된다.";
                case "DELETE"->"선택한 시험 데이터가 삭제되어 재조회해도 표시되지 않는다. 다른 데이터는 유지된다.";
                case "READ"->"조회 조건에 맞는 결과가 표시되고 시험 데이터의 기대 결과와 일치한다. 제품 데이터는 변경되지 않는다.";
                default->"시험 파일이 허용 조건에 따라 처리되고 결과 데이터가 지정한 기대 데이터와 일치한다.";
            };
            tc.put("scenario",procedure).put("inputs","저장된 시험 계정·권한, 대상 기능의 시험 데이터·입력값·사전 상태와 비교할 기대 데이터 (미제공 값은 실행 전 준비)")
                .put("expectedResult",expected);
        }
        boolean conflict=!required.isBlank()&&!actual.isBlank()&&!required.equals(actual)&&!(required.equals("WRITE")&&Set.of("CREATE","UPDATE").contains(actual));
        selection.put("exclusionCandidate",conflict).put("reason",conflict?"현재 기능의 동작과 템플릿의 시험 동작이 다릅니다. LLM 확인 전에는 제외 확정하지 않습니다.":"생성 TC는 유지하고 실행 전 적용 여부를 확인합니다.");
        tc.put("automationMode",Set.of("COMMON","PRODUCT_DOCUMENT","USER_MANUAL").contains(group)?"MANUAL_OR_TOOL":
            Set.of("LOGIN-02","LOGIN-10","LOGIN-11","LOGIN-12","LOGIN-13","LOGIN-14","LOGIN-26","LOGIN-27").contains(tc.path("ruleId").asText())?"ADDITIONAL_EVIDENCE":"BROWSER_CANDIDATE");
        for(String field:List.of("scenario","inputs","expectedResult")) {
            String value=tc.path(field).asText();
            if(!name.isBlank()&&!Set.of("COMMON","PRODUCT_DOCUMENT","USER_MANUAL").contains(group)) {
                if(field.equals("scenario"))value="["+path+"]\n"+value;
                if(field.equals("inputs"))value="대상: "+name+"\n"+value;
            }
            tc.put(field,substitute(value,name,path));
        }
        var defect=(ObjectNode)tc.path("defectExample");
        for(String key:List.of("summary","content"))defect.put(key,substitute(defect.path(key).asText(),name,path));
        TcProcedureWriter.write(tc,name,path,group);
        var context=mapper.createArrayNode().add("저장된 접속 설정과 시험 계정의 역할·권한");
        String rule=tc.path("ruleId").asText();
        if(required.equals("READ"))context.add("조회 조건별 시험 데이터와 기대 목록");
        else if(required.equals("DELETE"))context.add("삭제 전용 데이터·식별값과 정리·복구 방법");
        else if(required.equals("CREATE")||required.equals("UPDATE")||required.equals("WRITE"))context.add("필드별 유효값·필수값·제약과 저장 전후 기대 데이터");
        else if(required.equals("UPLOAD"))context.add("시험 파일·허용 확장자·크기·개수와 기대 처리 결과");
        else if(group.equals("LOGIN"))context.add("계정 상태·역할·정책에 맞는 예상 인증 결과");
        else context.add("대상 기능의 구체적인 입력값·기대값과 독립 비교 근거");
        if(rule.equals("LOGIN-17")||rule.equals("LOGIN-18"))context.add("문서·실제 설정의 잠금 정책과 시험 전용 계정 복구 절차");
        plan.set("requiredContext",context);
        plan.put("readinessReason","구체적인 시험 데이터·검증 기준과 화면 요소 연결은 실행 전에 검토합니다.");
        plan.put("specificationVersion","execution-spec-v1");
        plan.put("tcContentDigest",MenuTcGenerator.hash(tc.path("scenario").asText()+tc.path("inputs").asText()+tc.path("expectedResult").asText()));
        var spec=plan.putObject("specification").put("status","NEEDS_CONTEXT").put("timeoutSeconds",60)
            .put("retryPolicy","조건 대기만 재시도. 실패한 변경 동작을 성공할 때까지 반복하지 않음.")
            .put("cleanup",Set.of("CREATE","UPDATE","DELETE","WRITE").contains(required)?"시험 전용 데이터의 정리·복구 방법 확인 필요":"변경한 시험 조건·세션을 초기화");
        var specSteps=spec.putArray("steps");
        for(var step:plan.path("steps"))specSteps.addObject().put("action","REQUIRES_BINDING").put("instruction",step.asText()).put("target",path).put("status","UNRESOLVED");
        var checks=spec.putArray("checks");
        for(String check:tc.path("expectedResult").asText().split("\n"))checks.addObject().put("requirementQuote",check).put("method","REQUIRES_BINDING")
            .put("status","UNRESOLVED").put("oracleSource","문서·제품 설정·시험 데이터 근거와 연결 필요");
        spec.putArray("evidence").add("대상 화면 본문").add("실제 조작 기록").add("조건별 실제값과 기대값");
        TcExecutionSpecification.upgradeRead(mapper, tc, feature);
    }
    static String operation(String name) {
        int kinds=0;
        for(String pattern:List.of("삭제|제거|delete|remove","등록|생성|추가|create|add","수정|변경|편집|update|edit","조회|목록|검색|read|list|search","업로드|upload"))
            if(java.util.regex.Pattern.compile(pattern,java.util.regex.Pattern.CASE_INSENSITIVE).matcher(name).find())kinds++;
        if(kinds!=1)return ""; // A combined CRUD menu is not a delete-only feature.
        if(name.matches("(?is).*?(삭제|제거|delete|remove)(?:\\s*(기능|버튼))?$"))return "DELETE";
        if(name.matches("(?is).*?(등록|생성|추가|create|add)(?:\\s*(기능|버튼))?$"))return "CREATE";
        if(name.matches("(?is).*?(수정|변경|편집|update|edit)(?:\\s*(기능|버튼))?$"))return "UPDATE";
        if(name.matches("(?is).*?(조회|목록|검색|read|list|search)(?:\\s*(기능|버튼))?$"))return "READ";
        if(name.matches("(?is).*?(업로드|upload)(?:\\s*(기능|버튼))?$"))return "UPLOAD";
        return "";
    }
    private static String substitute(String text,String name,String path) {
        return text.replace("{기능명}",name).replace("AAA",name).replace("BBB",path).replace("CCC","문서·시험 데이터에 근거한 입력값");
    }
    private static String join(JsonNode path) {var parts=new ArrayList<String>();path.forEach(p->parts.add(p.asText()));return String.join(" → ",parts);}
}
