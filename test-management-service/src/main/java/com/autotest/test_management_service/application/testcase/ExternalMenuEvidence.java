package com.autotest.test_management_service.application.testcase;

import java.util.*;
import org.springframework.http.HttpStatus;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

/** A deterministic short-reference catalogue reconstructed only from the frozen job input. */
public final class ExternalMenuEvidence {
    private ExternalMenuEvidence() {}
    private record Entry(String sourceId,String quote,String menuKey,ObjectNode exposed) {}
    private static List<Entry> entries(ObjectMapper mapper,JsonNode snapshot) {
        List<Entry> result=new ArrayList<>();
        for(JsonNode source:snapshot.path("evidence")) {
            String text=source.path("text").asText();
            for(int start=0;start<text.length();) {
                int end=Math.min(text.length(),start+240);
                if(end<text.length()&&Character.isLowSurrogate(text.charAt(end)))end--;
                String quote=text.substring(start,end),ref=String.format(Locale.ROOT,"E%04d",result.size()+1);
                ObjectNode item=mapper.createObjectNode().put("refId",ref).put("text",quote);
                for(String field:List.of("kind","role","filename","pageIndex","location","readiness","webPath"))if(source.has(field))item.set(field,source.get(field).deepCopy());
                result.add(new Entry(source.path("id").asText(),quote,source.path("menuKey").asText(),item));
                if(end==text.length())break;
                start=end-40;if(Character.isLowSurrogate(text.charAt(start)))start--;
            }
        }
        return result;
    }
    public static ArrayNode catalogue(ObjectMapper mapper,JsonNode snapshot) {
        ArrayNode result=mapper.createArrayNode();for(Entry entry:entries(mapper,snapshot))result.add(entry.exposed());return result;
    }
    public static JsonNode resolve(ObjectMapper mapper,JsonNode snapshot,UUID job,String requestId,JsonNode rows) {
        if(!rows.isArray()||rows.size()>2000)throw error("MENU_ROWS_INVALID","기능 행은 최대 2,000개 배열이어야 합니다.");
        boolean shortRefs=false;for(JsonNode row:rows)shortRefs|=row.has("evidenceRefIds");
        if(!shortRefs)return rows; // Existing full-ID + quote responses remain supported.
        if(!job.toString().equals(requestId))throw error("MENU_REQUEST_MISMATCH","다른 실행의 응답이거나 requestId가 없습니다. 요청 프롬프트의 requestId와 같은 응답을 가져오세요.");
        Map<String,Entry> byRef=new HashMap<>();for(Entry entry:entries(mapper,snapshot))byRef.put(entry.exposed().path("refId").asText(),entry);
        ArrayNode result=mapper.createArrayNode();
        for(JsonNode raw:rows) {
            if(!raw.isObject())throw error("MENU_ROWS_INVALID","각 기능 행은 JSON 객체여야 합니다.");
            ObjectNode row=raw.deepCopy();
            if(row.has("evidenceRefIds")) {
                JsonNode refs=row.remove("evidenceRefIds");
                if(!refs.isArray()||refs.isEmpty()||refs.size()>8)throw error("MENU_REFS_INVALID","evidenceRefIds는 1~8개 근거 번호 배열이어야 합니다.");
                ArrayNode evidence=row.putArray("evidence"),keys=row.putArray("matchedMenuKeys");Set<String> seen=new HashSet<>(),menuKeys=new HashSet<>();
                for(JsonNode value:refs) {
                    if(!value.isTextual()||value.asText().length()>40)throw error("MENU_REFS_INVALID","근거 번호 형식이 올바르지 않습니다.");
                    String ref=value.asText();if(!seen.add(ref))continue;Entry entry=byRef.get(ref);
                    if(entry==null){evidence.addObject().put("sourceId","unknown-ref:"+ref).put("quote","");continue;}
                    evidence.addObject().put("sourceId",entry.sourceId()).put("quote",entry.quote());
                    if(!entry.menuKey().isBlank()&&menuKeys.add(entry.menuKey()))keys.add(entry.menuKey());
                }
            }
            result.add(row);
        }
        return result;
    }
    private static ProductDescriptionException error(String code,String message){return new ProductDescriptionException(HttpStatus.BAD_REQUEST,code,message);}
}
