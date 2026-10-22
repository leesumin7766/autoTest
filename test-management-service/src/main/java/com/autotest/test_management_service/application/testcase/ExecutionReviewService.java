package com.autotest.test_management_service.application.testcase;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.autotest.test_management_service.domain.submission.*;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

@Service
public class ExecutionReviewService {
    private final JdbcTemplate jdbc;private final SubmissionRepository submissions;private final TestExecutionService executions;
    public ExecutionReviewService(JdbcTemplate jdbc,SubmissionRepository submissions,TestExecutionService executions){this.jdbc=jdbc;this.submissions=submissions;this.executions=executions;}
    private ProductDescriptionException invalid(HttpStatus status,String code,String message){return new ProductDescriptionException(status,code,message);}
    private ObjectNode closed(UUID submission,long member,UUID run){
        var owner=submissions.findByIdForUpdate(SubmissionId.of(submission)).orElseThrow(()->invalid(HttpStatus.NOT_FOUND,"SUBMISSION_NOT_FOUND","문서 세트를 찾을 수 없습니다."));
        if(owner.memberId().value()!=member)throw invalid(HttpStatus.FORBIDDEN,"FORBIDDEN","접근할 수 없습니다.");
        var value=(ObjectNode)executions.status(submission,member,run).path("run");
        if(Set.of("PREPARED","WAITING_EXECUTOR","RUNNING").contains(value.path("status").asText()))throw invalid(HttpStatus.CONFLICT,"EXECUTION_NOT_CLOSED","실행이 끝난 뒤 검토·재시험하세요.");
        return value;
    }
    @Transactional public ObjectNode review(UUID submission,long member,UUID run,String caseId,int revision,String verdict,String reason,String disposition){
        var value=closed(submission,member,run);var snapshot=(ObjectNode)value.path("snapshot");
        if(snapshot.path("reviewRevision").asInt()!=revision)throw invalid(HttpStatus.CONFLICT,"EXECUTION_REVIEW_CONFLICT","다른 검토가 저장됐습니다. 다시 조회하세요.");
        if(verdict==null||!Set.of("P","F","N/A","UNJUDGED").contains(verdict)||reason==null||reason.isBlank()||reason.length()>2000)throw invalid(HttpStatus.BAD_REQUEST,"EXECUTION_REVIEW_INVALID","판정과 2,000자 이내 검토 사유가 필요합니다.");
        if(disposition==null)disposition="PENDING";
        if(!Set.of("PENDING","CONFIRMED","REJECTED","RETEST").contains(disposition)||("CONFIRMED".equals(disposition)&&!"F".equals(verdict)))throw invalid(HttpStatus.BAD_REQUEST,"EXECUTION_DISPOSITION_INVALID","결함 확정은 F 판정에만 적용할 수 있습니다.");
        ObjectNode selected=null;for(var item:snapshot.path("items"))if(item.path("caseId").asText().equals(caseId))selected=(ObjectNode)item;
        if(selected==null)throw invalid(HttpStatus.NOT_FOUND,"EXECUTION_CASE_NOT_FOUND","시험 항목을 찾을 수 없습니다.");
        if(!selected.has("automaticResult")){selected.set("automaticResult",selected.path("result").deepCopy());selected.put("automaticReason",selected.path("reason").asText());}
        var audit=snapshot.withArray("reviews").addObject().put("caseId",caseId).put("revision",revision+1).put("memberId",member).put("createdAt",Instant.now().toString()).put("reason",reason.trim()).put("disposition",disposition);
        audit.set("before",selected.path("result").deepCopy());if("UNJUDGED".equals(verdict)){selected.putNull("result");audit.putNull("after");}else{selected.put("result",verdict);audit.put("after",verdict);}
        selected.put("reason",reason.trim()).put("judgmentStatus","USER_REVIEWED").put("manualOverride",true).put("defectCandidate","F".equals(verdict)).put("defectDisposition",disposition);
        snapshot.put("reviewRevision",revision+1);
        jdbc.update("UPDATE test_execution_runs SET snapshot=?::jsonb,updated_at=now() WHERE run_id=?",snapshot.toString(),run);
        return executions.status(submission,member,run);
    }
    @Transactional public ObjectNode retry(UUID submission,long member,UUID run,List<String> selected,String productVersion){
        var value=closed(submission,member,run);var snapshot=value.path("snapshot");Set<String> allowed=new HashSet<>();var ids=new ArrayList<String>();
        for(var item:snapshot.path("items")){String id=item.path("caseId").asText();allowed.add(id);if(selected==null&&(item.path("result").isNull()||"F".equals(item.path("result").asText())))ids.add(id);}
        if(selected!=null)ids.addAll(selected);
        if(ids.isEmpty()||!allowed.containsAll(ids))throw invalid(HttpStatus.BAD_REQUEST,"EXECUTION_RETRY_INVALID","재시험할 항목을 선택하세요.");
        var prepared=executions.prepare(submission,member,UUID.fromString(value.path("tcSetId").asText()),snapshot.path("targetUrl").asText(),snapshot.path("notes").asText(),ids,snapshot.path("allowMutation").asBoolean(),productVersion==null?snapshot.path("productVersion").asText():productVersion);
        var next=(ObjectNode)prepared.path("run").path("snapshot");next.put("retryOf",run.toString());
        jdbc.update("UPDATE test_execution_runs SET snapshot=?::jsonb WHERE run_id=?",next.toString(),UUID.fromString(prepared.path("run").path("runId").asText()));
        return prepared;
    }
    public String report(UUID submission,long member,UUID run){
        var value=executions.status(submission,member,run).path("run");var snapshot=value.path("snapshot");
        StringBuilder text=new StringBuilder("# 시험·결함 검토 보고서 초안\n\n");
        for(String key:List.of("productName","productVersion","targetUrl","menuJobId","menuRevision","retryOf"))text.append(key).append(": ").append(clean(snapshot.path(key).asText())).append("\n\n");
        text.append("실행: ").append(run).append("\n\nTC 세트: ").append(value.path("tcSetId").asText()).append("\n\n상태: ").append(value.path("status").asText()).append("\n\n");
        for(var item:snapshot.path("items")){
            text.append("## ").append(clean(item.path("tc").path("tcId").asText())).append(" — ").append(clean(item.path("tc").path("scenario").asText())).append("\n\n");
            text.append("판정: ").append(item.path("result").isNull()?"미판정":item.path("result").asText()).append(" / 결함 검토: ").append(item.path("defectDisposition").asText("미확정")).append("\n\n");
            for(String key:List.of("inputs","expectedResult"))text.append(key).append(": ").append(clean(item.path("tc").path(key).asText())).append("\n\n");
            text.append("실제 결과: ").append(clean(item.path("actualResult").asText())).append("\n\n사유: ").append(clean(item.path("reason").asText())).append("\n\n증거 참조: ").append(clean(item.path("evidenceIds").toString())).append("\n\n");
            text.append("재현 절차·관찰: 기능별 실행 기록과 Qwen 계획을 실행 상세에서 확인하세요.\n\n");
        }
        text.append("## 이전 실패·검토 이력\n\n").append(clean(snapshot.path("previousAttempts").toString())).append("\n\n").append(clean(snapshot.path("reviews").toString()));
        return text.toString();
    }
    private String clean(String value){return value.replace("<","&lt;").replace(">","&gt;").replace("\u0000","");}
}
