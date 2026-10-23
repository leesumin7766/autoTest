package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.Pattern;

/** Authentication is a product capability, independent of sidebar placement. */
public final class AuthenticationTcContext {
    private AuthenticationTcContext() {}
    public static ObjectNode detect(ObjectMapper mapper, JsonNode snapshot, JsonNode rows) {
        var result=mapper.createObjectNode().put("required",false);
        var evidence=result.putArray("evidence");
        Set<Integer> limits=new TreeSet<>();
        var limit=Pattern.compile("(?:비밀번호|패스워드|로그인|인증)[^。.!?\\n]{0,70}?(\\d{1,3})\\s*회[^。.!?\\n]{0,50}?(?:잠금|잠기|제한|차단)");
        for(var row:rows) {
            String name=row.path("functionName").asText().toLowerCase(Locale.ROOT);
            if(name.matches(".*(로그인|login).*")&&!name.matches(".*(기록|이력|조회|history|audit).*")) {
                result.put("required",true);
                if(row.path("evidence").isArray()) row.path("evidence").forEach(evidence::add);
            }
        }
        for(var source:snapshot.path("evidence")) {
            if("PRE_AUTH".equals(source.path("authenticationStage").asText())) {
                result.put("required",true);
                if(evidence.size()<8)evidence.addObject().put("sourceId",source.path("id").asText()).put("quote",source.path("text").asText().substring(0,Math.min(500,source.path("text").asText().length())));
            }
            if(!"DOCUMENT".equals(source.path("kind").asText()))continue;
            String text=source.path("text").asText();
            // An affirmative credential statement avoids treating a bare word or a negation as support.
            for(String sentence:text.split("[\\n。]")) {
                if(!sentence.matches("(?is).*(로그인|login).*")||sentence.matches("(?is).*(로그인.{0,12}(없|불필요|지원하지)|로그인 이력|로그인 기록).*"))continue;
                if(!sentence.matches("(?is).*(아이디|비밀번호|패스워드|계정|로그인 화면|로그인 버튼|로그인한다|로그인합니다).*"))continue;
                result.put("required",true);
                if(evidence.size()<8)evidence.addObject().put("sourceId",source.path("id").asText()).put("quote",sentence.substring(0,Math.min(500,sentence.length())));
            }
            var matches=limit.matcher(text);
            while(matches.find()) { int count=Integer.parseInt(matches.group(1));if(count>0) {
                limits.add(count);
                if(evidence.size()<8)evidence.addObject().put("sourceId",source.path("id").asText()).put("quote",matches.group());
            } }
        }
        if(limits.size()==1)result.put("failedAttemptLimit",limits.iterator().next());
        result.put("policyReviewRequired",limits.size()!=1);
        result.put("policyNote",limits.size()>1?"문서의 실패 횟수 정책이 서로 다릅니다. 적용 제품·정책을 확인하세요.":limits.isEmpty()?"실패 횟수는 임의로 가정하지 않습니다. 잠금 정책을 확인하세요.":"문서에서 추출한 실패 횟수입니다. 시험 전 적용 정책을 확인하세요.");
        return result;
    }
    public static ArrayNode prepare(ObjectMapper mapper, String product, JsonNode input, JsonNode context) {
        ArrayNode rows=input.deepCopy();
        boolean login=false;
        var generator=new MenuTcGenerator(mapper);
        for(var row:rows)if("로그인".equals(generator.classify(row)))login=true;
        if(context.path("required").asBoolean()&&!login) {
            var row=rows.addObject().put("featureId","authentication:login").put("functionName","로그인").put("description","계정 정보로 로그인하는 기능").put("featureType","로그인");
            row.putArray("featurePath").add(product).add("로그인");
            row.set("evidence",context.path("evidence").deepCopy());row.putArray("matchedMenuKeys");
        }
        return rows;
    }
}
