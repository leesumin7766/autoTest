package com.autotest.test_management_service.application.testcase;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.autotest.test_management_service.application.crawler.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.annotation.PreDestroy;

@Service
@ConditionalOnProperty(name="execution.worker.enabled",havingValue="true",matchIfMissing=true)
public class TestExecutionWorker {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    private final TestExecutionService executions; private final PlaywrightSetupService setups; private final PlaywrightCredentialCipher cipher;
    private final String aiUrl,token;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final AtomicBoolean busy=new AtomicBoolean();
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"tc-execution-worker");t.setDaemon(true);return t;});
    public TestExecutionWorker(JdbcTemplate jdbc,ObjectMapper mapper,TestExecutionService executions,PlaywrightSetupService setups,PlaywrightCredentialCipher cipher,
        @Value("${ai-service.base-url:http://ai-service:8005}") String aiUrl,@Value("${product-description.internal-token:}") String token) {
        this.jdbc=jdbc;this.mapper=mapper;this.executions=executions;this.setups=setups;this.cipher=cipher;this.aiUrl=aiUrl;this.token=token;
    }
    @PreDestroy public void close(){executor.shutdownNow();}
    @EventListener(ApplicationReadyEvent.class) public void recover(){
        jdbc.update("UPDATE test_execution_runs SET status='INTERRUPTED',updated_at=now() WHERE status='RUNNING'");
    }
    @Scheduled(fixedDelay=1000) public void tick(){
        if(token.isBlank()||!busy.compareAndSet(false,true))return;
        executor.submit(()->{try{processNext();}finally{busy.set(false);}});
    }
    public boolean active(UUID run,UUID attempt){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM test_execution_runs WHERE run_id=? AND attempt=? AND status='RUNNING')",Boolean.class,run,attempt));}
    private void save(UUID run,UUID attempt,ObjectNode snapshot,String status){
        jdbc.update("UPDATE test_execution_runs SET snapshot=?::jsonb,status=?,updated_at=now() WHERE run_id=? AND attempt=? AND status='RUNNING'",snapshot.toString(),status,run,attempt);
    }
    private void pendingGroup(ObjectNode snapshot,JsonNode group,String message){
        Set<String> ids=new HashSet<>();group.path("caseIds").forEach(id->ids.add(id.asText()));
        for(var item:snapshot.path("items"))if(ids.contains(item.path("caseId").asText()))((ObjectNode)item).put("executionStatus","NEEDS_CONTEXT").put("reason",message);
    }
    private ArrayNode documents(UUID submission,JsonNode path){
        var excerpts=mapper.createArrayNode();
        jdbc.query("SELECT file_id,role,extracted_text FROM submission_files WHERE submission_id=? AND status='PARSED' AND role IN ('FUNCTION_LIST','MANUAL')",rs->{
            String text=rs.getString(3);if(text==null)return;int position=-1;
            for(int i=path.size()-1;i>=0;i--){String term=path.get(i).asText();if(term.length()>1&&(position=text.indexOf(term))>=0)break;}
            if(position<0)return;
            int start=Math.max(0,position-500),end=Math.min(text.length(),position+2500);
            excerpts.addObject().put("fileId",rs.getString(1)).put("role",rs.getString(2)).put("start",start).put("end",end).put("text",text.substring(start,end)).put("partial",true);
        },submission);return excerpts;
    }
    void processNext(){
        UUID attempt=UUID.randomUUID();
        var claimed=jdbc.query("""
            UPDATE test_execution_runs SET status='RUNNING',attempt=?,updated_at=now() WHERE run_id=(
              SELECT run_id FROM test_execution_runs WHERE status='WAITING_EXECUTOR' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED)
            RETURNING run_id,submission_id,member_id,snapshot::text
            """,(rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getLong(3),rs.getString(4)},attempt);
        if(claimed.isEmpty())return;
        var row=claimed.getFirst();UUID run=(UUID)row[0],submission=(UUID)row[1];long member=(long)row[2];ObjectNode snapshot=null;
        try{
            snapshot=(ObjectNode)mapper.readTree((String)row[3]);
            if(executions.status(submission,member,run).path("run").path("stale").asBoolean())throw new IllegalStateException("EXECUTION_SOURCE_CHANGED");
            var setup=setups.get(submission,member);String password=null;
            if(setup!=null) {
                // Credentials may only be reused for exactly the stored target, never a newly entered host.
                if(!setup.targetUrl().equals(snapshot.path("targetUrl").asText())||setup.stale())throw new IllegalStateException("EXECUTION_LOGIN_SETUP_MISMATCH");
                String encrypted=jdbc.queryForObject("SELECT password_ciphertext FROM playwright_crawl_setups WHERE submission_id=? AND member_id=?",String.class,submission,member);
                if(encrypted!=null)password=cipher.decrypt(submission,encrypted);
            }
            var results=snapshot.putArray("groupResults");
            for(var group:snapshot.path("groups")) {
                if(!active(run,attempt))return;
                if("COMMON".equals(group.path("kind").asText())){pendingGroup(snapshot,group,"공통·문서 시험은 별도 실행 방식이 필요합니다.");save(run,attempt,snapshot,"RUNNING");continue;}
                Set<String> ids=new HashSet<>();group.path("caseIds").forEach(id->ids.add(id.asText()));
                ObjectNode payload=mapper.createObjectNode().put("runId",run.toString()).put("attempt",attempt.toString()).put("targetUrl",snapshot.path("targetUrl").asText()).put("featureId",group.path("featureId").asText()).put("notes",snapshot.path("notes").asText()).put("allowMutation",snapshot.path("allowMutation").asBoolean());
                payload.set("featurePath",group.path("featurePath"));payload.set("documents",documents(submission,group.path("featurePath")));
                if(setup!=null)payload.set("setup",mapper.valueToTree(setup));
                if(password!=null)payload.put("password",password);
                var cases=mapper.createArrayNode();for(var item:snapshot.path("items"))if(ids.contains(item.path("caseId").asText())){
                    var tc=mapper.createObjectNode();for(String key:List.of("id","tcId","ruleId","scenario","inputs","expectedResult","scopeDescription"))tc.set(key,item.path("tc").path(key));cases.add(tc);
                }
                for(var selectedCase:cases){
                if(!active(run,attempt))return;
                payload.set("cases",mapper.createArrayNode().add(selectedCase));
                Set<String> expectedIds=Set.of(selectedCase.path("id").asText());
                snapshot.put("currentCaseId",selectedCase.path("id").asText());
                snapshot.put("currentFeatureId",group.path("featureId").asText());save(run,attempt,snapshot,"RUNNING");
                var request=HttpRequest.newBuilder(URI.create(aiUrl+"/api/v1/test-executions/feature")).timeout(Duration.ofMinutes(18)).header("Content-Type","application/json").header("X-Internal-Token",token).POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
                var response=http.send(request,HttpResponse.BodyHandlers.ofString());
                if(!active(run,attempt))return;
                if(response.statusCode()!=200)throw new IllegalStateException("EXECUTION_AI_UNAVAILABLE");
                JsonNode answer=mapper.readTree(response.body());
                Set<String> received=new HashSet<>();
                for(var value:answer.path("items")) {
                    String id=value.path("caseId").asText();
                    if(!expectedIds.contains(id)||!received.add(id))throw new IllegalStateException("EXECUTION_RESULT_INVALID");
                    String verdict=value.path("result").asText("");if(!Set.of("","null","P","F","N/A").contains(verdict))throw new IllegalStateException("EXECUTION_VERDICT_INVALID");
                    for(var item:snapshot.path("items"))if(id.equals(item.path("caseId").asText())){
                        ((ObjectNode)item).setAll((ObjectNode)value);
                        if("P".equals(value.path("result").asText())){
                            boolean guarded=value.path("requirementCoverageComplete").asBoolean()&&value.path("conditionAssessments").isArray()&&!value.path("conditionAssessments").isEmpty();
                            for(var condition:value.path("conditionAssessments"))guarded &= condition.path("grounded").asBoolean()&&"PASS".equals(condition.path("outcome").asText());
                            boolean previousFailure=false;for(var previous:snapshot.path("previousAttempts"))if(id.equals(previous.path("caseId").asText()))previousFailure=true;
                            if(!guarded||previousFailure)((ObjectNode)item).putNull("result").put("proposedResult","P").put("judgmentStatus","NEEDS_REVIEW").put("reason",previousFailure?"이전 실패 후 성공입니다. 이전 실행과 함께 검토하세요.":"P 확정에 필요한 TC별 확인 조건·증거가 없습니다.");
                        }
                        String actual="";for(var evidence:answer.path("evidence"))if(evidence.hasNonNull("text"))actual=evidence.path("text").asText();
                        ((ObjectNode)item).put("actualResult",actual.isBlank()?"기능별 관찰 증거와 동작 기록을 확인하세요.":actual.substring(0,Math.min(2000,actual.length())));
                    }
                }
                if(!received.equals(expectedIds))throw new IllegalStateException("EXECUTION_RESULT_INCOMPLETE");
                var result=results.addObject().put("featureId",group.path("featureId").asText()).put("caseId",selectedCase.path("id").asText());result.set("evidence",answer.path("evidence"));result.set("plans",answer.path("plans"));
                save(run,attempt,snapshot,"RUNNING");
                }
            }
            boolean partial=false;for(var item:snapshot.path("items"))if(item.path("result").isNull())partial=true;
            snapshot.remove(List.of("currentFeatureId","currentCaseId"));save(run,attempt,snapshot,partial?"PARTIAL":"COMPLETED");
        }catch(Exception failure){
            if(snapshot!=null){String code=failure.getMessage();snapshot.put("errorCode",code!=null&&code.matches("EXECUTION_[A-Z_]+")?code:"EXECUTION_WORKER_ERROR");save(run,attempt,snapshot,"FAILED");}
            else jdbc.update("UPDATE test_execution_runs SET status='FAILED',updated_at=now() WHERE run_id=? AND attempt=? AND status='RUNNING'",run,attempt);
        }
    }
}
