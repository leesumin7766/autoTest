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

/** TC 원본과 실행 이력을 분리한다. 브라우저 조작·LLM 판정기는 후속 단계에서 연결한다. */
@Service
public class TestExecutionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SubmissionRepository submissions;
    private final MenuTcService cases;
    public TestExecutionService(JdbcTemplate jdbc,ObjectMapper mapper,SubmissionRepository submissions,MenuTcService cases) {
        this.jdbc=jdbc;this.mapper=mapper;this.submissions=submissions;this.cases=cases;
    }
    private ProductDescriptionException error(HttpStatus status,String code,String message) {return new ProductDescriptionException(status,code,message);}
    private void owner(UUID submission,long member,boolean lock) {
        var id=SubmissionId.of(submission);
        var value=(lock?submissions.findByIdForUpdate(id):submissions.findById(id)).orElseThrow(()->error(HttpStatus.NOT_FOUND,"SUBMISSION_NOT_FOUND","문서 세트를 찾을 수 없습니다."));
        if(value.memberId().value()!=member)throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","이 문서 세트에 접근할 수 없습니다.");
    }
    private void current(UUID submission,long member,UUID set) {
        if(cases.status(submission,member,set).path("set").path("stale").asBoolean())throw error(HttpStatus.CONFLICT,"EXECUTION_SOURCE_CHANGED","메뉴트리·문서가 변경되었습니다. TC를 다시 생성하세요.");
    }
    @Transactional
    public ObjectNode prepare(UUID submission,long member,UUID set,String targetUrl,String notes,List<String> selected) {
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
        }
        snapshot.put("caseCount",items.size()).put("groupCount",groups.size());
        UUID run=UUID.randomUUID();jdbc.update("INSERT INTO test_execution_runs(run_id,submission_id,member_id,tc_set_id,status,snapshot) VALUES(?,?,?,?,'PREPARED',?::jsonb)",run,submission,member,set,snapshot.toString());
        return status(submission,member,run);
    }
    public ObjectNode status(UUID submission,long member,UUID selected) {
        owner(submission,member,false);var result=mapper.createObjectNode();result.put("executorConnected",false).put("judgeConnected",false);
        var history=jdbc.query("SELECT run_id,status,created_at FROM test_execution_runs WHERE submission_id=? AND member_id=? ORDER BY created_at DESC,run_id DESC LIMIT 50",(rs,n)->mapper.createObjectNode().put("runId",rs.getString(1)).put("status",rs.getString(2)).put("createdAt",rs.getTimestamp(3).toInstant().toString()),submission,member);
        result.set("history",mapper.valueToTree(history));if(selected==null&&!history.isEmpty())selected=UUID.fromString(history.getFirst().path("runId").asText());
        if(selected==null){result.putNull("run");return result;}
        var values=jdbc.query("SELECT run_id,tc_set_id,status,snapshot::text,created_at,updated_at FROM test_execution_runs WHERE run_id=? AND submission_id=? AND member_id=?",(rs,n)->{
            var run=mapper.createObjectNode().put("runId",rs.getString(1)).put("tcSetId",rs.getString(2)).put("status",rs.getString(3)).put("createdAt",rs.getTimestamp(5).toInstant().toString()).put("updatedAt",rs.getTimestamp(6).toInstant().toString());
            try{run.set("snapshot",mapper.readTree(rs.getString(4)));}catch(Exception bad){throw new IllegalStateException("EXECUTION_STORAGE_INVALID",bad);}return run;
        },selected,submission,member);
        if(values.isEmpty())throw error(HttpStatus.NOT_FOUND,"EXECUTION_NOT_FOUND","시험 실행 이력을 찾을 수 없습니다.");
        var run=values.getFirst();run.put("stale",cases.status(submission,member,UUID.fromString(run.path("tcSetId").asText())).path("set").path("stale").asBoolean());
        run.put("completedCount",0);result.set("run",run);return result;
    }
    @Transactional
    public ObjectNode transition(UUID submission,long member,UUID run,boolean cancel) {
        owner(submission,member,true);var value=status(submission,member,run).path("run");String state=value.path("status").asText();
        if(cancel) {
            if(!state.equals("CANCELED"))jdbc.update("UPDATE test_execution_runs SET status='CANCELED',updated_at=now() WHERE run_id=? AND status IN ('PREPARED','WAITING_EXECUTOR')",run);
        } else {
            current(submission,member,UUID.fromString(value.path("tcSetId").asText()));
            if(state.equals("CANCELED"))throw error(HttpStatus.CONFLICT,"EXECUTION_CANCELED","중단한 실행입니다. 새 실행을 준비하세요.");
            if(state.equals("PREPARED")) {
                if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM test_execution_runs WHERE submission_id=? AND status='WAITING_EXECUTOR')",Boolean.class,submission)))throw error(HttpStatus.CONFLICT,"EXECUTION_ALREADY_WAITING","대기 중인 실행을 먼저 중단하세요.");
                jdbc.update("UPDATE test_execution_runs SET status='WAITING_EXECUTOR',updated_at=now() WHERE run_id=? AND status='PREPARED'",run);
            }
        }
        return status(submission,member,run);
    }
}
