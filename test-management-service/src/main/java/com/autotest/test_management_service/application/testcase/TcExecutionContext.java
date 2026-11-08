package com.autotest.test_management_service.application.testcase;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * 자동 TC 의 '실행 시 필요한 정보'(requiredContext)에 시험원이 적는 시험 자료(2026-11-08).
 * TC 본문은 바꾸지 않고 executionContext 로 저장한다. 실행 때 userContext 로 모델 계획·판정에 함께 보낸다.
 * 조건 입력 없는 조회 명세(execution-spec-v2-read) TC 는 별도 근거 연결을 쓰므로 여기서 받지 않는다.
 */
public final class TcExecutionContext {
    static final int MAX_VALUE=1000, MAX_NOTE=2000;
    public static final String NOT_NEEDED="NOT_NEEDED", MISSING="MISSING", PARTIAL="PARTIAL", COMPLETE="COMPLETE", READ_BINDING="READ_BINDING";
    /** 비밀번호·토큰은 접속 설정에만 둔다. 시험 자료에 적으면 거절한다 */
    static final Pattern SECRET=Pattern.compile("(?i)(비밀번호|패스워드|password|passwd|pwd|token|토큰|secret|api[_ -]?key)\\s*[:=]\\s*\\S+|bearer\\s+[a-z0-9._-]{12,}");

    private TcExecutionContext() {}

    static boolean readBinding(JsonNode tc) {
        return TcExecutionSpecification.VERSION.equals(tc.path("executionPlan").path("specificationVersion").asText());
    }

    static List<String> needs(JsonNode tc) {
        var needs=new ArrayList<String>();
        for(var need:tc.path("executionPlan").path("requiredContext"))if(need.isTextual()&&!need.asText().isBlank())needs.add(need.asText());
        return needs;
    }

    public static String status(JsonNode tc) {
        if(readBinding(tc))return READ_BINDING;
        var needs=needs(tc);if(needs.isEmpty())return NOT_NEEDED;
        Map<String,String> values=new HashMap<>();
        for(var item:tc.path("executionContext").path("items"))values.put(item.path("need").asText(),item.path("value").asText("").strip());
        long filled=needs.stream().filter(need->!values.getOrDefault(need,"").isEmpty()).count();
        return filled==0?MISSING:filled==needs.size()?COMPLETE:PARTIAL;
    }

    /** 실행기로 보내는 형태: 채운 항목과 메모만. 없으면 null */
    public static ObjectNode userContext(JsonNode tc,com.fasterxml.jackson.databind.ObjectMapper mapper) {
        var saved=tc.path("executionContext");if(!saved.isObject())return null;
        var out=mapper.createObjectNode().put("source","TESTER_SUPPLIED");var items=out.putArray("items");
        for(var item:saved.path("items"))if(!item.path("value").asText("").isBlank())items.addObject().put("need",item.path("need").asText()).put("value",item.path("value").asText().strip());
        String note=saved.path("note").asText("").strip();if(!note.isEmpty())out.put("note",note);
        return items.isEmpty()&&note.isEmpty()?null:out;
    }

    /** 요청 {revision, items:[{need,value}], note} 를 검증해 tc 에 반영한다. 모두 비우면 기록을 지운다 */
    public static void apply(ObjectNode tc,JsonNode request,long member) {
        if(readBinding(tc))throw conflict("TC_CONTEXT_READ_BINDING","조건 입력 없는 조회 TC는 '조회 실행 명세 근거 연결'에서 연결하세요.");
        int current=tc.path("executionContext").path("revision").asInt(0);
        if(request==null||!request.path("revision").isIntegralNumber()||request.path("revision").asInt(-1)!=current)
            throw conflict("TC_CONTEXT_CONFLICT","다른 화면에서 시험 자료를 변경했습니다. 다시 불러온 뒤 저장하세요.");
        var needs=new LinkedHashSet<>(needs(tc));
        Map<String,String> values=new LinkedHashMap<>();
        var items=request.path("items");if(!items.isMissingNode()&&!items.isArray())throw invalid("시험 자료 형식이 올바르지 않습니다.");
        for(var item:items) {
            String need=item.path("need").asText(""),value=item.path("value").isTextual()?item.path("value").asText().strip():"";
            if(!needs.contains(need))throw invalid("이 TC가 요구하지 않는 항목입니다: "+need);
            if(value.length()>MAX_VALUE)throw coded("TC_CONTEXT_TOO_LONG","항목마다 1,000자 이내로 적어 주세요.");
            values.put(need,value);
        }
        String note=request.path("note").isTextual()?request.path("note").asText().strip():"";
        if(note.length()>MAX_NOTE)throw coded("TC_CONTEXT_TOO_LONG","메모는 2,000자 이내로 적어 주세요.");
        for(String text:concat(values.values(),note))if(SECRET.matcher(text).find())
            throw coded("TC_CONTEXT_SECRET","비밀번호·토큰 같은 비밀값은 적지 마세요. 시험 계정은 화면 수집의 로그인 정보에 저장합니다.");
        boolean empty=note.isEmpty()&&values.values().stream().allMatch(String::isEmpty);
        if(empty&&current==0){tc.remove("executionContext");return;}
        var saved=tc.putObject("executionContext");var list=saved.putArray("items");
        for(String need:needs)if(!values.getOrDefault(need,"").isEmpty())list.addObject().put("need",need).put("value",values.get(need));
        saved.put("note",note).put("revision",current+1).put("updatedBy",member).put("updatedAt",Instant.now().toString());
    }

    private static List<String> concat(Collection<String> values,String note){var all=new ArrayList<>(values);all.add(note);return all;}
    private static ProductDescriptionException conflict(String code,String message){return new ProductDescriptionException(HttpStatus.CONFLICT,code,message);}
    private static ProductDescriptionException coded(String code,String message){return new ProductDescriptionException(HttpStatus.BAD_REQUEST,code,message);}
    private static ProductDescriptionException invalid(String message){return new ProductDescriptionException(HttpStatus.BAD_REQUEST,"TC_CONTEXT_INVALID",message);}
}
