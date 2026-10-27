package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.autotest.test_management_service.application.crawler.PlaywrightSetupService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.domain.submission.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuTcService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SubmissionRepository submissions;
    private final FunctionalMenuService menus;
    private final PlaywrightSetupService setups;
    private final MenuTcGenerator generator;
    private final MenuTcWorkbook workbook;
    public MenuTcService(JdbcTemplate jdbc, ObjectMapper mapper, SubmissionRepository submissions, FunctionalMenuService menus,
            PlaywrightSetupService setups, MenuTcGenerator generator, MenuTcWorkbook workbook) {
        this.jdbc=jdbc; this.mapper=mapper; this.submissions=submissions; this.menus=menus; this.setups=setups; this.generator=generator; this.workbook=workbook;
    }
    private ProductDescriptionException conflict(String code, String message) { return new ProductDescriptionException(HttpStatus.CONFLICT, code, message); }
    private void owner(UUID submission, long member, boolean lock) {
        var id=SubmissionId.of(submission);
        var value=(lock ? submissions.findByIdForUpdate(id) : submissions.findById(id)).orElseThrow(() -> new ProductDescriptionException(HttpStatus.NOT_FOUND,"SUBMISSION_NOT_FOUND","문서 세트를 찾을 수 없습니다."));
        if(value.memberId().value()!=member)throw new ProductDescriptionException(HttpStatus.FORBIDDEN,"FORBIDDEN","이 문서 세트에 접근할 수 없습니다.");
    }
    private JsonNode parse(String json) { try { return mapper.readTree(json); } catch(Exception bad) { throw new IllegalStateException("TC_STORAGE_INVALID",bad); } }
    public ArrayNode features(JsonNode menu, Map<String,String> overrides) {
        ArrayNode features=mapper.createArrayNode(); Set<String> allowed=new HashSet<>();
        for(JsonNode row:menu.path("output").path("rows")) {
            if(!row.path("included").asBoolean() || "MENU_OBSERVATION".equals(row.path("rowType").asText()))continue;
            String id=row.path("rowId").asText(); allowed.add(id);
            var feature=features.addObject().put("featureId",id).put("functionName",row.path("functionName").asText())
                .put("description",row.path("menuDescription").asText()).put("targetUrl",row.path("targetUrl").asText(menu.path("targetUrl").asText()));
            var path=feature.putArray("featurePath").add(menu.path("productName").asText());
            row.path("menuPath").forEach(path::add); path.add(row.path("functionName").asText());
            if(overrides.containsKey(id))feature.put("featureType",overrides.get(id));
            else if(row.hasNonNull("featureType"))feature.put("featureType",row.path("featureType").asText());
            feature.set("evidence",row.path("evidence").deepCopy()); feature.set("matchedMenuKeys",row.path("matchedMenuKeys").deepCopy());
        }
        if(!allowed.containsAll(overrides.keySet()))throw MenuTcGenerator.invalid("TC_FEATURE_OVERRIDE_INVALID","포함된 기능의 구분만 지정할 수 있습니다.");
        if(features.isEmpty())throw conflict("TC_FUNCTIONS_REQUIRED","승인 메뉴트리에 포함된 기능이 없습니다. 메뉴 구조 행은 TC 생성 대상이 아닙니다.");
        return features;
    }
    private JsonNode source(UUID submission,long member,UUID job) {
        JsonNode menu=menus.status(submission,member,job).path("job");
        requireApproved(menu);
        return menu;
    }
    public static void requireApproved(JsonNode menu) {
        if(menu.isMissingNode() || menu.isNull() || !"APPROVED".equals(menu.path("status").asText()))
            throw new ProductDescriptionException(HttpStatus.CONFLICT,"TC_MENU_APPROVAL_REQUIRED","메뉴트리를 검토·승인한 뒤 TC를 생성하세요.");
        if(menu.path("stale").asBoolean() || "MOCK".equals(menu.path("generationMode").asText()) || "PARTIAL".equals(menu.path("output").path("completionStatus").asText()))
            throw new ProductDescriptionException(HttpStatus.CONFLICT,"TC_MENU_NOT_CURRENT","현재 문서와 일치하는 완전한 실제 승인 메뉴트리가 필요합니다.");
    }
    @Transactional
    public ObjectNode create(UUID submission,long member,UUID menuJob,Map<String,String> overrides) {
        return create(submission,member,menuJob,overrides,null);
    }
    @Transactional
    public ObjectNode create(UUID submission,long member,UUID menuJob,Map<String,String> overrides,String loginUrl) {
        owner(submission,member,true); setups.get(submission,member);
        JsonNode menu=source(submission,member,menuJob);
        var source=mapper.createObjectNode().put("kind","APPROVED_MENU").put("submissionId",submission.toString())
            .put("menuJobId",menu.path("jobId").asText()).put("menuRevision",menu.path("revision").asInt())
            .put("crawlJobId",menu.path("crawlJobId").asText()).put("targetUrl",menu.path("targetUrl").asText());
        ObjectNode authentication=(ObjectNode)menus.authentication(submission,member,menuJob);
        String target=menu.path("targetUrl").asText();if(target.isBlank())target=authentication.path("targetUrl").asText();source.put("targetUrl",target);
        if(loginUrl!=null&&!loginUrl.isBlank()) {
            if(!authentication.path("required").asBoolean()||!(loginUrl.equals(authentication.path("targetUrl").asText())||loginUrl.equals(authentication.path("loginUrl").asText())))
                throw MenuTcGenerator.invalid("TC_LOGIN_URL_INVALID","저장된 기본 접속 주소 또는 로그인 주소를 선택해 주세요.");
            authentication.put("loginUrl",loginUrl);
        }
        if(authentication.path("required").asBoolean()&&!authentication.hasNonNull("loginUrl"))authentication.put("loginUrl",target);
        source.set("authentication",authentication);
        var prepared=AuthenticationTcContext.prepare(mapper,menu.path("productName").asText(),features(menu,overrides),authentication);
        for(var feature:prepared)if(feature.path("targetUrl").asText().isBlank())((ObjectNode)feature).put("targetUrl",target);
        ObjectNode output=generator.generate(menu.path("productName").asText(),prepared,source);
        UUID set=UUID.randomUUID();
        jdbc.update("INSERT INTO menu_tc_sets(set_id,submission_id,member_id,menu_job_id,menu_revision,input_digest,template_version,output) VALUES(?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(submission_id,input_digest) DO NOTHING",
            set,submission,member,UUID.fromString(menu.path("jobId").asText()),menu.path("revision").asInt(),output.path("inputDigest").asText(),MenuTcGenerator.VERSION,output.toString());
        UUID saved=jdbc.queryForObject("SELECT set_id FROM menu_tc_sets WHERE submission_id=? AND input_digest=?",UUID.class,submission,output.path("inputDigest").asText());
        return status(submission,member,saved);
    }
    public ObjectNode status(UUID submission,long member,UUID selected) {
        owner(submission,member,false);var result=mapper.createObjectNode();
        var history=jdbc.query("SELECT set_id,menu_job_id,menu_revision,created_at FROM menu_tc_sets WHERE submission_id=? AND member_id=? ORDER BY created_at DESC LIMIT 30",
            (rs,n)->mapper.createObjectNode().put("setId",rs.getString(1)).put("menuJobId",rs.getString(2)).put("menuRevision",rs.getInt(3)).put("createdAt",rs.getTimestamp(4).toInstant().toString()),submission,member);
        result.set("history",mapper.valueToTree(history));if(selected==null&&!history.isEmpty())selected=UUID.fromString(history.getFirst().path("setId").asText());
        if(selected==null){result.putNull("set");return result;}
        var sets=jdbc.query("SELECT set_id,menu_job_id,menu_revision,output::text FROM menu_tc_sets WHERE set_id=? AND submission_id=? AND member_id=?",
            (rs,n)->{var value=mapper.createObjectNode().put("setId",rs.getString(1)).put("menuJobId",rs.getString(2)).put("menuRevision",rs.getInt(3));value.set("output",parse(rs.getString(4)));return value;},selected,submission,member);
        if(sets.isEmpty())throw new ProductDescriptionException(HttpStatus.NOT_FOUND,"TC_SET_NOT_FOUND","TC 생성 결과를 찾을 수 없습니다.");
        var set=sets.getFirst();JsonNode menu=menus.status(submission,member,UUID.fromString(set.path("menuJobId").asText())).path("job");
        set.put("stale",menu.path("stale").asBoolean()||!"APPROVED".equals(menu.path("status").asText())||set.path("menuRevision").asInt()!=menu.path("revision").asInt());
        result.set("set",set);return result;
    }
    public byte[] download(UUID submission,long member,UUID selected) {
        var set=status(submission,member,selected).path("set");
        if(set.path("stale").asBoolean())throw conflict("TC_SOURCE_CHANGED","메뉴트리·문서가 변경되었습니다. 새 승인본으로 TC를 생성하세요.");
        return workbook.export(set.path("output"));
    }
}
