package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.Pattern;

/** User bindings for the existing, deliberately narrow NO_FILTER / TEXT_PRESENT executor. */
final class ReadSpecificationBindings {
    private static final Pattern SEMANTIC = Pattern.compile("동작|기능|제공|표시|명확|목적|제약|여부|정보|취소되|업로드 되|정상적으로|한다|된다|됨$|함$|그리고| 및 ");
    static ObjectNode apply(ObjectMapper mapper, JsonNode original, JsonNode request, JsonNode hints) {
        var tc=(ObjectNode)original.deepCopy();var plan=(ObjectNode)tc.path("executionPlan");
        require(TcExecutionSpecification.VERSION.equals(plan.path("specificationVersion").asText()),"지원하는 조회 실행 명세가 아닙니다.");
        var spec=(ObjectNode)plan.path("specification");
        String digest=MenuTcGenerator.hash(tc.path("scenario").asText()+tc.path("inputs").asText()+tc.path("expectedResult").asText());
        require(digest.equals(spec.path("sourceTcDigest").asText())&&digest.equals(plan.path("tcContentDigest").asText()),"TC 내용이 변경되었습니다. 명세를 다시 생성하세요.");
        require(request!=null&&request.isObject(),"연결 정보를 입력하세요.");
        var missing=spec.putArray("missingBindings");var bindings=spec.putObject("bindings");
        var target=request.path("target");
        if(target.isObject()) {
            boolean found=false;
            for(var hint:hints)if(hint.path("menuKey").equals(target.path("menuKey"))&&hint.path("href").equals(target.path("href"))&&hint.path("label").equals(target.path("heading")))found=true;
            require(found&&!target.path("href").asText().isBlank()&&!target.path("heading").asText().isBlank(),"현재 수집 근거에서 대상 화면을 선택하세요.");
            var saved=bindings.putObject("target");for(String key:List.of("menuKey","href","heading"))saved.set(key,target.path(key).deepCopy());
        }else {bindings.putNull("target");missing(missing,"TARGET_BINDING_REQUIRED","bindings.target","수집 근거에서 조회 대상 화면을 선택하세요.");}
        var context=request.path("readContext");
        if(context.isObject()) {
            require("NO_FILTER".equals(context.path("mode").asText()),"조건 입력 없는 조회만 지원합니다.");
            require(Set.of("TC_INPUT","DOCUMENT_EVIDENCE").contains(context.path("oracle").path("kind").asText()),"조회 전제의 입력·문서 근거를 선택하세요.");
            var oracle=oracle(mapper,tc,context.path("oracle"));
            bindings.putObject("readContext").put("mode","NO_FILTER").put("confirmed",context.path("confirmed").asBoolean()).set("oracle",oracle);
            if(!context.path("confirmed").asBoolean())missing(missing,"READ_CONTEXT_REQUIRED","bindings.readContext","조건 입력 없는 조회 전제를 확인하세요.");
        }else {bindings.putNull("readContext");missing(missing,"READ_CONTEXT_REQUIRED","bindings.readContext","조회 전제의 입력·문서 근거를 연결하세요.");}
        var supplied=new HashMap<String,JsonNode>();
        require(!request.has("checks")||request.path("checks").isArray(),"기대 조건 연결 형식이 올바르지 않습니다.");
        for(var check:request.path("checks"))require(supplied.put(check.path("id").asText(),check)==null,"기대 조건이 중복되었습니다.");
        String expected=tc.path("expectedResult").asText();boolean[] covered=new boolean[expected.length()];
        for(int i=0;i<expected.length();i++)covered[i]=!Character.isLetterOrDigit(expected.charAt(i));
        var connectors=Pattern.compile("\\s+(?:그리고|및|또한)\\s+").matcher(expected);
        while(connectors.find())Arrays.fill(covered,connectors.start(),connectors.end(),true);
        boolean allChecks=true;
        for(var node:spec.path("checks")) {
            var check=(ObjectNode)node;String id=check.path("id").asText(),quote=check.path("requirementQuote").asText();
            var input=supplied.remove(id);check.put("status","UNRESOLVED").put("method","REQUIRES_BINDING").putNull("oracle");check.remove("expected");
            boolean literal=!quote.isBlank()&&quote.length()<=1000&&expected.contains(quote)&&!SEMANTIC.matcher(quote).find();
            if(input!=null) {
                require(quote.equals(input.path("expected").asText()),"기대 문구는 원래 기대 조건과 같아야 합니다. 일부 문구로 축소할 수 없습니다.");
                check.set("oracle",oracle(mapper,tc,input.path("oracle")));check.put("expected",quote);
            }
            if(input!=null&&literal){check.put("status","BOUND").put("method","TEXT_PRESENT");int start=expected.indexOf(quote);Arrays.fill(covered,start,start+quote.length(),true);}
            else {allChecks=false;missing(missing,"CHECK_BINDING_REQUIRED","checks."+id,literal?id+"의 기대 문구 근거를 연결하세요.":id+"은 설명형·복합 조건입니다. 현재 문구 존재 비교로 실행할 수 없습니다.");}
        }
        require(supplied.isEmpty(),"이 TC에 없는 기대 조건입니다.");
        if(allChecks){boolean complete=!expected.isBlank();for(boolean value:covered)complete&=value;
            if(!complete||spec.path("checks").size()<1||spec.path("checks").size()>16)missing(missing,"CHECK_COVERAGE_REQUIRED","checks","기대 조건 전체를 연결해야 합니다. 지원 범위는 1~16개입니다.");}
        boolean ready=missing.isEmpty();spec.put("status",ready?"READY":"NEEDS_CONTEXT");
        for(var step:spec.path("steps"))((ObjectNode)step).put("status",ready?"BOUND":"UNRESOLVED");
        var required=plan.putArray("requiredContext");missing.forEach(issue->required.add(issue.path("message").asText()));
        plan.put("readinessReason",ready?"조회 근거 연결 완료. 실행 준비를 새로 생성하세요.":"조회 근거 추가 연결 필요 "+missing.size()+"건");
        return tc;
    }
    private static ObjectNode oracle(ObjectMapper mapper,JsonNode tc,JsonNode value) {
        String kind=value.path("kind").asText(),quote=value.path("quote").asText();String source="";
        if("TC_INPUT".equals(kind))source=tc.path("inputs").asText();
        if("TC_EXPECTED".equals(kind))source=tc.path("expectedResult").asText();
        int index=value.path("index").asInt(-1);
        if("DOCUMENT_EVIDENCE".equals(kind)&&value.path("index").isIntegralNumber()&&index>=0&&index<tc.path("evidence").size())source=tc.path("evidence").get(index).path("quote").asText();
        require(!quote.isBlank()&&quote.length()<=4000&&source.contains(quote),"선택한 원문에 존재하는 인용문만 연결할 수 있습니다.");
        var result=mapper.createObjectNode().put("kind",kind).put("quote",quote);if("DOCUMENT_EVIDENCE".equals(kind))result.put("index",index);return result;
    }
    private static void missing(ArrayNode issues,String code,String field,String message){issues.addObject().put("code",code).put("field",field).put("message",message);}
    private static void require(boolean valid,String message){if(!valid)throw MenuTcGenerator.invalid("READ_BINDING_INVALID",message);}
}
