package com.autotest.test_management_service.application.testcase;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class FunctionalMenuValidator {
    private final ObjectMapper mapper;
    public FunctionalMenuValidator(ObjectMapper mapper) { this.mapper=mapper; }
    private void invalid(String code,String message) { throw new ProductDescriptionException(HttpStatus.BAD_REQUEST,code,message); }
    public ArrayNode validate(JsonNode rows,JsonNode snapshot,JsonNode originals) {
        if(!rows.isArray() || rows.size()>2000) invalid("MENU_ROWS_INVALID","기능 행은 최대 2,000개까지 허용됩니다.");
        Map<String,JsonNode> sources=new HashMap<>(), previous=new HashMap<>();
        for(JsonNode source:snapshot.path("evidence")) sources.put(source.path("id").asText(),source);
        if(originals!=null) for(JsonNode row:originals) previous.put(row.path("rowId").asText(),row);
        Set<String> seen=new HashSet<>(); ArrayNode result=mapper.createArrayNode();
        for(JsonNode raw:rows) {
            JsonNode basis=raw;
            String id=raw.path("rowId").asText(UUID.randomUUID().toString());
            if(id.isBlank() || !seen.add(id) || id.length()>80) invalid("MENU_ROW_ID_INVALID","기능 행 ID가 중복되거나 올바르지 않습니다.");
            if(originals!=null) {
                basis=previous.get(raw.path("originRowId").asText(id));
                if(basis==null) invalid("MENU_EVIDENCE_INVALID","기존 근거가 있는 행만 수정·분리할 수 있습니다.");
            }
            var path=raw.path("menuPath");
            if(!path.isArray() || path.size()>3) invalid("MENU_DEPTH_EXCEEDED","제품명과 기능명을 포함해 최대 5depth입니다. 상위 분류는 3개까지 입력하세요.");
            for(JsonNode name:path) if(!name.isTextual() || name.asText().isBlank() || name.asText().length()>120 || "-".equals(name.asText())) invalid("MENU_PATH_INVALID","메뉴 경로에는 빈 값이나 -를 입력하지 마세요.");
            String name=raw.path("functionName").asText().trim(), description=raw.path("menuDescription").asText().trim();
            if(name.isBlank() || name.length()>160 || description.isBlank() || description.length()>600 || !description.endsWith("기능")) invalid("MENU_DESCRIPTION_INVALID","메뉴 설명은 600자 이내의 ~하는 기능 형식으로 작성하세요.");
            String location=raw.path("location").asText("UNKNOWN");
            if(!Set.of("HEADER","FOOTER","SIDEBAR","CONTENT","UNKNOWN").contains(location)) invalid("MENU_LOCATION_INVALID","메뉴 위치가 올바르지 않습니다.");
            JsonNode evidence=basis.path("evidence"); JsonNode requestedKeys=basis.path("matchedMenuKeys");
            ArrayNode keys=mapper.createArrayNode(), attached=mapper.createArrayNode();
            if(evidence.isArray())evidence.forEach(ref->attached.add(ref.deepCopy()));
            boolean disconnected=false,automatic=false;
            if(requestedKeys.isArray()) for(JsonNode key:requestedKeys) {
                JsonNode menu=sources.values().stream().filter(source->!source.path("menuKey").asText().isBlank() && source.path("menuKey").asText().equals(key.asText())).findFirst().orElse(null);
                if(menu==null || keys.size()>=7){disconnected=true;continue;}
                if(java.util.stream.StreamSupport.stream(keys.spliterator(),false).anyMatch(k->k.asText().equals(key.asText())))continue;
                keys.add(key.asText());
                if(java.util.stream.StreamSupport.stream(attached.spliterator(),false).noneMatch(ref->ref.path("sourceId").asText().equals(menu.path("id").asText()))) {
                    String text=menu.path("text").asText();attached.add(mapper.createObjectNode().put("sourceId",menu.path("id").asText()).put("quote",text.substring(0,Math.min(240,text.length()))));automatic=true;
                }
            }
            if(!requestedKeys.isArray())invalid("MENU_EVIDENCE_INVALID","메뉴 연결 목록이 필요합니다.");
            evidence=attached;
            if(!evidence.isArray() || evidence.isEmpty() || evidence.size()>8 || !keys.isArray() || keys.size()>8) invalid("MENU_EVIDENCE_INVALID","각 기능에는 1~8개 원문 근거가 필요합니다.");
            Set<String> allowedKeys=new HashSet<>(); boolean document=false,screen=false,incomplete=false;
            ArrayNode verified=mapper.createArrayNode();
            for(JsonNode reference:evidence) {
                JsonNode source=sources.get(reference.path("sourceId").asText());
                String quote=reference.path("quote").asText().trim();
                if(source==null || quote.isBlank() || quote.length()>500 || !source.path("text").asText().contains(quote)) invalid("MENU_EVIDENCE_INVALID","인용문을 고정된 입력 자료에서 확인하지 못했습니다.");
                document|="DOCUMENT".equals(source.path("kind").asText()); screen|=!"DOCUMENT".equals(source.path("kind").asText());
                incomplete|="LOAD_TIMEOUT".equals(source.path("readiness").asText());
                if(!source.path("menuKey").asText().isBlank()) allowedKeys.add(source.path("menuKey").asText());
                verified.add(mapper.createObjectNode().put("sourceId",source.path("id").asText()).put("quote",quote));
            }
            for(JsonNode key:keys) if(!allowedKeys.contains(key.asText())) invalid("MENU_MATCH_EVIDENCE_INVALID","연결한 메뉴의 원문 근거가 필요합니다.");
            boolean webFirst="web-first-v1".equals(snapshot.path("webStructure").path("version").asText());
            boolean placed=!webFirst;boolean manual=false;
            if(webFirst){
                JsonNode deepest=mapper.createArrayNode();List<JsonNode> anchors=new ArrayList<>();
                for(JsonNode key:keys)for(JsonNode source:sources.values())if(source.path("menuKey").asText().equals(key.asText())&&!source.path("webPath").isEmpty())anchors.add(source.path("webPath"));
                for(JsonNode candidate:anchors)if(candidate.size()>deepest.size())deepest=candidate;
                placed=!deepest.isEmpty()&&deepest.size()<=3;
                for(JsonNode candidate:anchors)for(int i=0;i<candidate.size();i++)if(i>=deepest.size()||!candidate.get(i).equals(deepest.get(i)))placed=false;
                if(placed)path=deepest.deepCopy();
                else if(originals!=null && document && raw.path("included").asBoolean(false)){
                    for(JsonNode node:snapshot.path("webStructure").path("nodes"))if(node.path("path").equals(path)&&path.size()<=3){placed=true;manual=true;break;}
                }
                if(!placed){path=mapper.createArrayNode();keys.removeAll();}
            }
            ObjectNode row=mapper.createObjectNode().put("rowId",id).put("functionName",name).put("menuDescription",description)
                    .put("location",location).put("included",placed&&raw.path("included").asBoolean(true)).put("reviewStatus","NEEDS_REVIEW")
                    .put("verificationStatus",incomplete?"INCOMPLETE":document&&screen&&!keys.isEmpty()?"MATCH_PROPOSED":document?"DOCUMENT_ONLY":"SCREEN_OBSERVED");
            row.put("evidenceStatus","VALID").put("connectionStatus",keys.isEmpty()?"NOT_LINKED":"PROPOSED");
            if(webFirst)row.put("rowType",document?"FUNCTION":"MENU_OBSERVATION").put("placementStatus",!placed?"UNASSIGNED":manual?"MANUAL_PLACEMENT":"WEB_ANCHORED");
            row.set("menuPath",path.deepCopy());row.set("evidence",verified);row.set("matchedMenuKeys",keys.deepCopy());
            JsonNode notes=raw.path("reviewNotes");
            ArrayNode cleanNotes=mapper.createArrayNode();
            if(notes.isArray()) for(JsonNode note:notes) { if(cleanNotes.size()>=8 || note.asText().length()>500) invalid("MENU_NOTES_INVALID","검토 메모는 500자 이내, 최대 8개입니다."); cleanNotes.add(note.asText()); }
            if(disconnected){if(cleanNotes.size()>=8)cleanNotes.remove(7);cleanNotes.add("화면 근거를 확인하지 못한 메뉴 연결을 해제했습니다. 문서 기준으로 검토하세요.");}
            if(automatic){if(cleanNotes.size()>=8)cleanNotes.remove(7);cleanNotes.add("프로그램이 제공된 메뉴 근거를 첨부했습니다. 의미적 연결 확인은 검토 전입니다.");}
            if(webFirst&&!placed){if(cleanNotes.size()>=8)cleanNotes.remove(7);cleanNotes.add("웹 메뉴에서 위치를 확인하지 못해 메뉴트리에서 제외했습니다. 별도 배치 검토가 필요합니다.");}
            row.set("reviewNotes",cleanNotes);result.add(row);
        }
        return result;
    }
}
