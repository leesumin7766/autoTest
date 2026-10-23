package com.autotest.test_management_service.application.testcase;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.domain.submission.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TC 원본·실행 이력과 사전 실행 가능 여부를 분리한다. */
@Service
public class TestExecutionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SubmissionRepository submissions;
    private final MenuTcService cases;
    private final com.autotest.test_management_service.application.crawler.PlaywrightSetupService setups;
    public TestExecutionService(JdbcTemplate jdbc,ObjectMapper mapper,SubmissionRepository submissions,MenuTcService cases,com.autotest.test_management_service.application.crawler.PlaywrightSetupService setups) {
        this.jdbc=jdbc;this.mapper=mapper;this.submissions=submissions;this.cases=cases;this.setups=setups;
    }
    private ProductDescriptionException error(HttpStatus status,String code,String message) {return new ProductDescriptionException(status,code,message);}
    private String prerequisite(JsonNode tc){
        if(tc.path("featureId").asText().startsWith("scope:"))return "공통·문서 시험: 별도 문서·환경·수동 수행 증거가 필요합니다.";
        String scope=tc.path("scopeDescription").asText(),scenario=tc.path("scenario").asText();
        if((scope.matches("(?is).*(업로드|upload).*")&&"FEATURE-01".equals(tc.path("ruleId").asText()))||(scope.matches("(?is).*(업로드|upload|import|가져오|파일).*")&&scenario.matches("(?s).*(확장자|스크립트 파일|최대 개수|파일의.*크기).*")))return "사전 입력 필요: 시험용 파일과 기대 확장자·크기·개수. 현재 파일 공급 기능 미지원입니다.";
        return "";
    }
    private void owner(UUID submission,long member,boolean lock) {
        var id=SubmissionId.of(submission);
        var value=(lock?submissions.findByIdForUpdate(id):submissions.findById(id)).orElseThrow(()->error(HttpStatus.NOT_FOUND,"SUBMISSION_NOT_FOUND","문서 세트를 찾을 수 없습니다."));
        if(value.memberId().value()!=member)throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","이 문서 세트에 접근할 수 없습니다.");
    }
    private void current(UUID submission,long member,UUID set) {
        if(cases.status(submission,member,set).path("set").path("stale").asBoolean())throw error(HttpStatus.CONFLICT,"EXECUTION_SOURCE_CHANGED","메뉴트리·문서가 변경되었습니다. TC를 다시 생성하세요.");
    }
    @Transactional
    public ObjectNode prepare(UUID submission,long member,UUID set,String targetUrl,String notes,List<String> selected,boolean allowMutation) {
        return prepare(submission,member,set,targetUrl,notes,selected,allowMutation,"");
    }
    @Transactional
    public ObjectNode prepare(UUID submission,long member,UUID set,String targetUrl,String notes,List<String> selected,boolean allowMutation,String productVersion) {
        owner(submission,member,true);
        if(set==null)throw error(HttpStatus.BAD_REQUEST,"TC_SET_REQUIRED","실행할 TC 세트를 선택하세요.");
        var source=cases.status(submission,member,set).path("set");current(submission,member,set);
        try {var url=URI.create(targetUrl==null?"":targetUrl.trim());if(!Set.of("http","https").contains(url.getScheme())||url.getHost()==null||url.getUserInfo()!=null||url.getFragment()!=null)throw new IllegalArgumentException();}
        catch(Exception bad){throw error(HttpStatus.BAD_REQUEST,"EXECUTION_URL_INVALID","계정 정보가 없는 http 또는 https 주소를 입력하세요.");}
        if(targetUrl.length()>2000||(notes!=null&&notes.length()>4000))throw error(HttpStatus.BAD_REQUEST,"EXECUTION_INPUT_TOO_LONG","주소 또는 시험 메모가 너무 깁니다.");
        var output=source.path("output"); Set<String> ids=new HashSet<>();output.path("testCases").forEach(tc->ids.add(tc.path("id").asText()));
        Set<String> wanted=selected==null?ids:new HashSet<>(selected);
        if(wanted.isEmpty()||!ids.containsAll(wanted))throw error(HttpStatus.BAD_REQUEST,"EXECUTION_CASES_INVALID","실행할 TC를 올바르게 선택하세요.");
        var snapshot=mapper.createObjectNode().put("schemaVersion","execution-v1").put("productName",output.path("productName").asText()).put("targetUrl",targetUrl.trim()).put("notes",notes==null?"":notes.trim())
            .put("menuJobId",source.path("menuJobId").asText()).put("menuRevision",source.path("menuRevision").asInt()).put("templateVersion",output.path("templateVersion").asText());
        var groups=snapshot.putArray("groups");var items=snapshot.putArray("items"); Map<String,ObjectNode> grouped=new LinkedHashMap<>();
        for(var tc:output.path("testCases")) {
            if(!wanted.contains(tc.path("id").asText()))continue;
            String feature=tc.path("featureId").asText();
            var group=grouped.computeIfAbsent(feature,key->{var g=groups.addObject().put("featureId",key).put("kind",key.startsWith("scope:")?"COMMON":"FUNCTION");g.set("featurePath",tc.path("featurePath").deepCopy());g.putArray("caseIds");return g;});
            ((ArrayNode)group.path("caseIds")).add(tc.path("id").asText());
            var item=items.addObject().put("caseId",tc.path("id").asText()).put("featureId",feature).put("executionStatus","PENDING").put("judgmentStatus","PENDING");
            item.set("tc",tc.deepCopy());item.putNull("result");item.putNull("actualResult");item.putNull("reason");item.putArray("evidence");
            String missing=prerequisite(tc);if(!missing.isEmpty())item.put("executionStatus","NEEDS_CONTEXT").put("judgmentStatus","NOT_EXECUTED").put("reason",missing).put("reasonCode","EXECUTION_PREREQUISITE_REQUIRED");
        }
        snapshot.put("caseCount",items.size()).put("groupCount",groups.size());
        if(productVersion!=null&&productVersion.length()>120)throw error(HttpStatus.BAD_REQUEST,"EXECUTION_VERSION_INVALID","제품·패치 버전은 120자 이내입니다.");
        snapshot.put("productVersion",productVersion==null?"":productVersion.trim());snapshot.put("reviewRevision",0);
        var previous=snapshot.putArray("previousAttempts");
        jdbc.query("""
            SELECT DISTINCT ON (item->>'caseId') r.run_id,item->>'caseId' AS case_id,item->>'result' AS result,item->>'proposedResult' AS proposed,item->>'reason' AS reason,r.created_at
            FROM test_execution_runs r CROSS JOIN LATERAL jsonb_array_elements(r.snapshot->'items') item
            WHERE r.submission_id=? AND r.member_id=? AND r.tc_set_id=?
              AND (item->>'result'='F' OR item->>'proposedResult'='F')
              AND COALESCE(item->>'defectDisposition','PENDING')<>'REJECTED'
            ORDER BY item->>'caseId',r.created_at DESC
            """,rs->{if(wanted.contains(rs.getString("case_id")))previous.addObject().put("sourceRunId",rs.getString(1)).put("caseId",rs.getString(2)).put("result",rs.getString(3)).put("proposedResult",rs.getString(4)).put("reason",rs.getString(5)).put("createdAt",rs.getTimestamp(6).toInstant().toString());},submission,member,set);
        snapshot.put("allowMutation",allowMutation);
        snapshot.put("setupVersion",setups.configurationVersion(submission,member));
        UUID run=UUID.randomUUID();jdbc.update("INSERT INTO test_execution_runs(run_id,submission_id,member_id,tc_set_id,status,snapshot) VALUES(?,?,?,?,'PREPARED',?::jsonb)",run,submission,member,set,snapshot.toString());
        return status(submission,member,run);
    }
    public ObjectNode status(UUID submission,long member,UUID selected) {
        owner(submission,member,false);var result=mapper.createObjectNode();result.put("executorConnected",false).put("judgeConnected",false).put("planner","qwen3:4b").put("judge","Jev");
        var history=jdbc.query("SELECT run_id,status,created_at FROM test_execution_runs WHERE submission_id=? AND member_id=? ORDER BY created_at DESC,run_id DESC LIMIT 50",(rs,n)->mapper.createObjectNode().put("runId",rs.getString(1)).put("status",rs.getString(2)).put("createdAt",rs.getTimestamp(3).toInstant().toString()),submission,member);
        result.set("history",mapper.valueToTree(history));if(selected==null&&!history.isEmpty())selected=UUID.fromString(history.getFirst().path("runId").asText());
        if(selected==null){result.putNull("run");return result;}
        var values=jdbc.query("SELECT run_id,tc_set_id,status,snapshot::text,created_at,updated_at FROM test_execution_runs WHERE run_id=? AND submission_id=? AND member_id=?",(rs,n)->{
            var run=mapper.createObjectNode().put("runId",rs.getString(1)).put("tcSetId",rs.getString(2)).put("status",rs.getString(3)).put("createdAt",rs.getTimestamp(5).toInstant().toString()).put("updatedAt",rs.getTimestamp(6).toInstant().toString());
            try{run.set("snapshot",mapper.readTree(rs.getString(4)));}catch(Exception bad){throw new IllegalStateException("EXECUTION_STORAGE_INVALID",bad);}return run;
        },selected,submission,member);
        if(values.isEmpty())throw error(HttpStatus.NOT_FOUND,"EXECUTION_NOT_FOUND","시험 실행 이력을 찾을 수 없습니다.");
        var run=values.getFirst();run.put("stale",cases.status(submission,member,UUID.fromString(run.path("tcSetId").asText())).path("set").path("stale").asBoolean());
        int completed=0,p=0,f=0,na=0,notExecuted=0,review=0,pending=0,errors=0,excluded=0;
        for(var item:run.path("snapshot").path("items")) {
            if(!"PENDING".equals(item.path("executionStatus").asText()))completed++;
            switch(item.path("executionStatus").asText()){case "NEEDS_CONTEXT"->notExecuted++;case "EXCLUDED"->excluded++;case "ERROR"->errors++;case "PENDING"->pending++;case "COMPLETED"->{if(item.path("result").isNull())review++;}default->{}}
            switch(item.path("result").asText()) { case "P"->p++;case "F"->f++;case "N/A"->na++;default->{} }
        }
        run.put("completedCount",completed).put("passCount",p).put("failCount",f).put("naCount",na).put("unjudgedCount",run.path("snapshot").path("caseCount").asInt()-p-f-na);
        run.put("notExecutedCount",notExecuted).put("reviewCount",review).put("pendingCount",pending).put("errorCount",errors).put("excludedCount",excluded);
        boolean active=Set.of("WAITING_EXECUTOR","RUNNING").contains(run.path("status").asText());
        try{var start=java.time.Instant.parse(run.path("snapshot").path("startedAt").asText(run.path("snapshot").path("requestedAt").asText()));var end=active?java.time.Instant.now():java.time.Instant.parse(run.path("snapshot").path("finishedAt").asText(run.path("updatedAt").asText()));run.put("elapsedMs",Math.max(0,java.time.Duration.between(start,end).toMillis()));}catch(Exception ignored){run.put("elapsedMs",0);}
        result.set("run",run);return result;
    }
    public JsonNode evidence(UUID submission,long member,UUID run,String caseId){
        var current=status(submission,member,run).path("run");
        var rows=jdbc.query("SELECT payload::text FROM test_execution_evidence WHERE run_id=? AND case_id=?",(rs,n)->{try{return mapper.readTree(rs.getString(1));}catch(Exception bad){throw new IllegalStateException("EXECUTION_EVIDENCE_INVALID");}},run,caseId);
        if(!rows.isEmpty())return rows.getFirst();
        for(var group:current.path("snapshot").path("groupResults"))if(caseId.equals(group.path("caseId").asText()))return group;
        throw error(HttpStatus.NOT_FOUND,"EXECUTION_EVIDENCE_NOT_FOUND","해당 TC의 수행 증거가 없습니다.");
    }
    @Transactional
    public ObjectNode transition(UUID submission,long member,UUID run,boolean cancel) {
        owner(submission,member,true);var value=status(submission,member,run).path("run");String state=value.path("status").asText();
        if(cancel) {
            if(!state.equals("CANCELED"))jdbc.update("UPDATE test_execution_runs SET status='CANCELED',snapshot=jsonb_set(snapshot,'{finishedAt}',to_jsonb(?::text)),updated_at=now() WHERE run_id=? AND status IN ('PREPARED','WAITING_EXECUTOR','RUNNING')",java.time.Instant.now().toString(),run);
        } else {
            current(submission,member,UUID.fromString(value.path("tcSetId").asText()));
            if(!Set.of("PREPARED","WAITING_EXECUTOR","RUNNING").contains(state))throw error(HttpStatus.CONFLICT,"EXECUTION_CLOSED","종료한 실행입니다. 새 실행을 준비하세요.");
            if(state.equals("PREPARED")) {
                if(value.path("snapshot").has("setupVersion")&&!value.path("snapshot").path("setupVersion").asText().equals(setups.configurationVersion(submission,member)))throw error(HttpStatus.CONFLICT,"EXECUTION_LOGIN_SETUP_CHANGED","시험 준비 후 접속 설정이 변경됐습니다. 현재 설정으로 시험을 다시 준비하세요.");
                boolean executable=false;for(var item:value.path("snapshot").path("items"))if("PENDING".equals(item.path("executionStatus").asText())&&!item.path("tc").path("featureId").asText().startsWith("scope:"))executable=true;
                if(!executable)throw error(HttpStatus.CONFLICT,"EXECUTION_PREREQUISITES_REQUIRED","자동으로 수행할 TC가 없습니다. 필요한 파일·입력과 공통·수동 시험 범위를 확인하세요.");
                if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM test_execution_runs WHERE submission_id=? AND status IN ('WAITING_EXECUTOR','RUNNING'))",Boolean.class,submission)))throw error(HttpStatus.CONFLICT,"EXECUTION_ALREADY_WAITING","진행 중인 실행을 먼저 중단하세요.");
                jdbc.update("UPDATE test_execution_runs SET status='WAITING_EXECUTOR',snapshot=jsonb_set(snapshot,'{requestedAt}',to_jsonb(?::text)),updated_at=now() WHERE run_id=? AND status='PREPARED'",java.time.Instant.now().toString(),run);
            }
        }
        return status(submission,member,run);
    }
}
