package com.autotest.test_management_service.application.service;

import com.autotest.common_lib.exception.*;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.*;
import com.autotest.test_management_service.exception.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ManualReviewService {
    private static final int MAX_BYTES=50*1024*1024;
    private static final String META="job_id,manual_file_id,filename,status,created_at,updated_at,as_of,error_code,attempt,total_pages,current_page,completed_pages,stage,processor_version,elapsed_ms,attempt_started_at,finished_at";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SubmissionRepository submissions;
    private final SubmissionPersistenceService persistence;
    private final FileStoragePort storage;
    private final TransactionTemplate tx;
    private final String endpoint,token;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private record Input(UUID job,byte[] bytes,LocalDate asOf,int attempt) {}
    private record State(UUID file,String hash,String status,int attempt,LocalDate asOf,String processor,int total,int completed,String error) {}
    private static class ProcessingFailure extends RuntimeException {
        final String code;
        ProcessingFailure(String code){this.code=code;}
    }

    public ManualReviewService(JdbcTemplate jdbc,ObjectMapper mapper,SubmissionRepository submissions,
            SubmissionPersistenceService persistence,FileStoragePort storage,PlatformTransactionManager manager,
            @Value("${MANUAL_REVIEW_SERVICE_URL:http://localhost:8007}")String endpoint,
            @Value("${product-description.internal-token:}")String token) {
        this.jdbc=jdbc;this.mapper=mapper;this.submissions=submissions;this.persistence=persistence;
        this.storage=storage;this.tx=new TransactionTemplate(manager);this.endpoint=endpoint;this.token=token;
    }
    @jakarta.annotation.PreDestroy public void close(){worker.shutdownNow();}
    @EventListener(ApplicationReadyEvent.class) public void recover(){
        var pending=jdbc.query("SELECT job_id,attempt,as_of FROM manual_review_jobs WHERE status IN ('RUNNING','PAUSING')",
            (rs,n)->new Input(rs.getObject(1,UUID.class),null,rs.getDate(3).toLocalDate(),rs.getInt(2)));
        for(var in:pending){
            if(stopRemote(in))terminal(in,"INTERRUPTED","SERVER_RESTARTED");
            else pausing(in,"STOP_NOT_CONFIRMED");
        }
    }
    private void owner(UUID id,long member){
        var submission=submissions.findById(SubmissionId.of(id)).orElseThrow(()->new CommonException(CommonErrorCode.RESOURCE_NOT_FOUND));
        if(submission.memberId().value()!=member)throw new CommonException(CommonErrorCode.ACCESS_DENIED);
    }
    private SubmittedDocument manual(UUID id){
        return persistence.findDocumentsBySubmissionId(SubmissionId.of(id)).stream().filter(doc->doc.role()==SubmissionType.MANUAL).findFirst().orElse(null);
    }
    static void validateManual(SubmittedDocument document){
        if(document==null||document.status()!=SubmissionStatus.PARSED)throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_DOCUMENT_REQUIRED);
        if(document.format()!=FileFormat.PDF)throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_PDF_REQUIRED);
    }
    private void configured(){
        URI uri=URI.create(endpoint);
        if(token.isBlank()||!"http".equals(uri.getScheme())||!Set.of("manual-review-service","localhost","127.0.0.1").contains(uri.getHost()))
            throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_UNAVAILABLE);
    }
    private JsonNode parse(String json){try{return mapper.readTree(json);}catch(Exception e){throw new ProcessingFailure("INVALID_PROCESSOR_RESULT");}}
    private byte[] bytes(SubmittedDocument doc){
        try(var input=storage.load(doc.storedPath())){
            byte[] value=input.readNBytes(MAX_BYTES+1);
            if(value.length>MAX_BYTES||value.length<5||!new String(value,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))
                throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_PDF_REQUIRED);
            return value;
        }catch(java.io.IOException e){throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_UNAVAILABLE);}
    }
    private String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    private State state(UUID id,long member,UUID job,boolean lock){
        var rows=jdbc.query("SELECT * FROM manual_review_jobs WHERE job_id=? AND submission_id=? AND member_id=?"+(lock?" FOR UPDATE":""),
            (rs,n)->new State(rs.getObject("manual_file_id",UUID.class),rs.getString("document_hash"),rs.getString("status"),rs.getInt("attempt"),
                rs.getDate("as_of").toLocalDate(),rs.getString("processor_version"),rs.getInt("total_pages"),rs.getInt("completed_pages"),rs.getString("error_code")),job,id,member);
        if(rows.isEmpty())throw new CommonException(CommonErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }
    private boolean resumable(String status,String processor,int total,int complete,String error){
        return (Set.of("PAUSED","INTERRUPTED","FAILED").contains(status)||("PARTIAL".equals(status)&&processor!=null))
            &&(total==0||complete<total)&&!Set.of("INPUT_CHANGED","CONTEXT_CHANGED","RESULT_TOO_LARGE","TEXT_TOO_LARGE").contains(error==null?"":error);
    }
    private ObjectNode row(java.sql.ResultSet rs)throws java.sql.SQLException{
        String status=rs.getString("status");
        long elapsed=rs.getLong("elapsed_ms");
        var started=rs.getTimestamp("attempt_started_at");
        if(started!=null&&Set.of("RUNNING","PAUSING").contains(status))elapsed+=Math.max(0,Duration.between(started.toInstant(),Instant.now()).toMillis());
        var value=mapper.createObjectNode().put("jobId",rs.getString("job_id")).put("manualFileId",rs.getString("manual_file_id"))
            .put("filename",rs.getString("filename")).put("status",status).put("attempt",rs.getInt("attempt"))
            .put("createdAt",rs.getTimestamp("created_at").toInstant().toString()).put("updatedAt",rs.getTimestamp("updated_at").toInstant().toString())
            .put("asOf",rs.getString("as_of")).put("errorCode",rs.getString("error_code"));
        value.putObject("progress").put("totalPages",rs.getInt("total_pages")).put("currentPage",rs.getInt("current_page"))
            .put("completedPages",rs.getInt("completed_pages")).put("stage",rs.getString("stage")).put("elapsedMs",elapsed);
        value.put("resumable",resumable(status,rs.getString("processor_version"),rs.getInt("total_pages"),rs.getInt("completed_pages"),rs.getString("error_code")));
        return value;
    }
    public ObjectNode get(UUID id,long member,UUID selected){
        owner(id,member);
        var history=jdbc.query("SELECT "+META+" FROM manual_review_jobs WHERE submission_id=? AND member_id=? ORDER BY CASE WHEN status IN ('RUNNING','PAUSING') THEN 0 ELSE 1 END,updated_at DESC,created_at DESC LIMIT 20",(rs,n)->row(rs),id,member);
        var view=mapper.createObjectNode();view.set("history",mapper.valueToTree(history));view.putNull("job");
        var doc=manual(id);if(doc!=null)view.put("currentManualFileId",doc.fileId().toString());
        UUID job=selected!=null?selected:history.isEmpty()?null:UUID.fromString(history.getFirst().path("jobId").asText());
        if(job!=null){
            var details=jdbc.query("SELECT * FROM manual_review_jobs WHERE job_id=? AND submission_id=? AND member_id=?",(rs,n)->{
                var value=row(rs);if(rs.getString("output")!=null)value.set("output",parse(rs.getString("output")));
                boolean stale=doc==null||!doc.fileId().toString().equals(value.path("manualFileId").asText());
                value.put("stale",stale);if(stale)value.put("resumable",false);return value;
            },job,id,member);
            if(details.isEmpty())throw new CommonException(CommonErrorCode.RESOURCE_NOT_FOUND);
            var checkpoints=jdbc.query("SELECT page_no,complete,updated_at FROM manual_review_pages WHERE job_id=? ORDER BY page_no",(rs,n)->mapper.createObjectNode()
                .put("page",rs.getInt(1)).put("complete",rs.getBoolean(2)).put("updatedAt",rs.getTimestamp(3).toInstant().toString()),job);
            details.getFirst().set("checkpoints",mapper.valueToTree(checkpoints));
            var decisions=mapper.createObjectNode();
            jdbc.query("SELECT candidate_id,decision,note,decided_at FROM manual_review_decisions WHERE job_id=? ORDER BY candidate_id",rs->{
                decisions.putObject(rs.getString(1)).put("decision",rs.getString(2)).put("note",rs.getString(3)).put("decidedAt",rs.getTimestamp(4).toInstant().toString());
            },job);
            details.getFirst().set("decisions",decisions);
            view.set("job",details.getFirst());
        }
        return view;
    }
    private boolean hasCandidate(UUID job,String candidate){
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM manual_review_jobs j,jsonb_array_elements(COALESCE(j.output->'candidates','[]'::jsonb)) c WHERE j.job_id=? AND c->>'id'=?)",Boolean.class,job,candidate));
    }
    public ObjectNode decide(UUID id,long member,UUID job,String candidate,String decision,String note){
        owner(id,member);
        state(id,member,job,false);
        if(!hasCandidate(job,candidate))throw new CommonException(CommonErrorCode.RESOURCE_NOT_FOUND);
        if(!Set.of("CONFIRMED","DISMISSED").contains(decision==null?"":decision)||(note!=null&&note.length()>500))throw new CommonException(CommonErrorCode.INVALID_INPUT_VALUE);
        jdbc.update("INSERT INTO manual_review_decisions(job_id,candidate_id,decision,note) VALUES(?,?,?,?) ON CONFLICT(job_id,candidate_id) DO UPDATE SET decision=EXCLUDED.decision,note=EXCLUDED.note,decided_at=now()",
            job,candidate,decision,note==null||note.isBlank()?null:note.strip());
        return get(id,member,job);
    }
    public ObjectNode clearDecision(UUID id,long member,UUID job,String candidate){
        owner(id,member);
        state(id,member,job,false);
        jdbc.update("DELETE FROM manual_review_decisions WHERE job_id=? AND candidate_id=?",job,candidate);
        return get(id,member,job);
    }
    public synchronized ObjectNode start(UUID id,long member){
        owner(id,member);configured();
        Input in=tx.execute(status->{
            submissions.findByIdForUpdate(SubmissionId.of(id)).orElseThrow(()->new CommonException(CommonErrorCode.RESOURCE_NOT_FOUND));
            var doc=manual(id);validateManual(doc);byte[] pdf=bytes(doc);var job=UUID.randomUUID();var asOf=LocalDate.now(ZoneId.of("Asia/Seoul"));
            int inserted=jdbc.update("""
                INSERT INTO manual_review_jobs(job_id,submission_id,member_id,manual_file_id,filename,document_hash,status,as_of,attempt_started_at,stage)
                VALUES(?,?,?,?,?,?,'RUNNING',?,now(),'COUNTING') ON CONFLICT DO NOTHING
                """,job,id,member,doc.fileId(),doc.originalFilename(),hash(pdf),asOf);
            if(inserted==0)throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_BUSY);
            return new Input(job,pdf,asOf,1);
        });
        worker.submit(()->analyze(in));return get(id,member,in.job());
    }
    public synchronized ObjectNode resume(UUID id,long member,UUID job){
        owner(id,member);configured();
        Input in;
        try{in=tx.execute(status->{
            var saved=state(id,member,job,true);
            if(!resumable(saved.status(),saved.processor(),saved.total(),saved.completed(),saved.error()))
                throw new CommonException(CommonErrorCode.STATE_CONFLICT);
            var doc=manual(id);validateManual(doc);
            if(!doc.fileId().equals(saved.file()))throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_INPUT_CHANGED);
            byte[] pdf=bytes(doc);
            if(!hash(pdf).equals(saved.hash()))throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_INPUT_CHANGED);
            int updated=jdbc.update("""
                UPDATE manual_review_jobs SET status='RUNNING',attempt=attempt+1,stage='COUNTING',attempt_started_at=now(),finished_at=NULL,error_code=NULL,updated_at=now()
                WHERE job_id=? AND NOT EXISTS(SELECT 1 FROM manual_review_jobs WHERE status IN ('RUNNING','PAUSING') AND job_id<>?)
                """,job,job);
            if(updated!=1)throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_BUSY);
            return new Input(job,pdf,saved.asOf(),saved.attempt()+1);
        });}catch(DataIntegrityViolationException e){throw new TestManagementException(TestManagementErrorCode.MANUAL_REVIEW_BUSY);}
        worker.submit(()->analyze(in));return get(id,member,job);
    }
    public ObjectNode pause(UUID id,long member,UUID job){
        owner(id,member);configured();
        var saved=tx.execute(status->{
            var current=state(id,member,job,true);
            if(!Set.of("RUNNING","PAUSING","PAUSED","INTERRUPTED").contains(current.status()))throw new CommonException(CommonErrorCode.STATE_CONFLICT);
            if(Set.of("RUNNING","PAUSING").contains(current.status()))pausing(new Input(job,null,current.asOf(),current.attempt()),null);
            return current;
        });
        if(Set.of("RUNNING","PAUSING").contains(saved.status())){
            var in=new Input(job,null,saved.asOf(),saved.attempt());
            if(stopRemote(in))terminal(in,"PAUSED",null);else pausing(in,"STOP_NOT_CONFIRMED");
        }
        return get(id,member,job);
    }
    private boolean active(Input in){return jdbc.queryForObject("SELECT count(*) FROM manual_review_jobs WHERE job_id=? AND attempt=? AND status='RUNNING'",Integer.class,in.job(),in.attempt())==1;}
    private String path(Input in){return "/tasks/"+in.job()+"/"+in.attempt();}
    private HttpRequest request(String method,String path,JsonNode body,long seconds){
        return HttpRequest.newBuilder(URI.create(endpoint+path)).header("X-Internal-Token",token).header("Content-Type","application/json")
            .timeout(Duration.ofSeconds(seconds)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }
    private JsonNode remote(String method,String path,JsonNode body,long seconds)throws Exception{
        var response=http.send(request(method,path,body,seconds),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new ProcessingFailure(response.statusCode()==409?"OCR_BUSY":"OCR_OR_LLM_UNAVAILABLE");
        if(response.body().getBytes(StandardCharsets.UTF_8).length>10*1024*1024)throw new ProcessingFailure("RESULT_TOO_LARGE");
        return parse(response.body());
    }
    private boolean stopRemote(Input in){
        try{return remote("POST",path(in)+"/close",null,12).path("stopped").asBoolean();}catch(Exception e){return false;}
    }
    private void pausing(Input in,String error){
        jdbc.update("UPDATE manual_review_jobs SET status='PAUSING',stage='PAUSING',error_code=?,updated_at=now() WHERE job_id=? AND attempt=? AND status IN ('RUNNING','PAUSING')",error,in.job(),in.attempt());
    }
    private void terminal(Input in,String status,String error){
        String expected="PAUSED".equals(status)?"'PAUSING'":"INTERRUPTED".equals(status)?"'RUNNING','PAUSING'":"'RUNNING'";
        jdbc.update("""
            UPDATE manual_review_jobs SET status=?,stage=?,error_code=?,elapsed_ms=elapsed_ms+CASE WHEN attempt_started_at IS NULL THEN 0
            ELSE GREATEST(0,EXTRACT(EPOCH FROM(now()-attempt_started_at))*1000)::bigint END,attempt_started_at=NULL,finished_at=now(),updated_at=now()
            WHERE job_id=? AND attempt=? AND status IN ("""+expected+")",status,status,error,in.job(),in.attempt());
    }
    private JsonNode page(Input in,int page,long deadline)throws Exception{
        var body=mapper.createObjectNode().put("asOf",in.asOf().toString());
        long remaining=Math.max(1,TimeUnit.NANOSECONDS.toSeconds(deadline-System.nanoTime()));
        var future=http.sendAsync(request("POST",path(in)+"/pages/"+page,body,remaining),HttpResponse.BodyHandlers.ofString());
        try{
            while(active(in)){
                if(System.nanoTime()>deadline)throw new ProcessingFailure("TIME_LIMIT");
                try{
                    var response=future.get(1,TimeUnit.SECONDS);
                    if(response.statusCode()!=200)throw new ProcessingFailure(response.statusCode()==409?"OCR_BUSY":"PAGE_ANALYSIS_FAILED");
                    if(response.body().getBytes(StandardCharsets.UTF_8).length>10*1024*1024)throw new ProcessingFailure("RESULT_TOO_LARGE");
                    return parse(response.body());
                }catch(TimeoutException wait){
                    try{
                        var progress=remote("GET",path(in)+"/progress",null,2);String stage=progress.path("stage").asText();
                        if(progress.path("currentPage").asInt()==page&&Set.of("EXTRACTING","REVIEWING","PAGE_DONE").contains(stage))
                            jdbc.update("UPDATE manual_review_jobs SET stage=?,updated_at=now() WHERE job_id=? AND attempt=? AND status='RUNNING'",stage,in.job(),in.attempt());
                    }catch(Exception ignored){}
                }
            }
            return null;
        }finally{if(!future.isDone())future.cancel(true);}
    }
    private ObjectNode aggregate(UUID job,int total,LocalDate asOf){
        var results=jdbc.query("SELECT output::text FROM manual_review_pages WHERE job_id=? ORDER BY page_no",(rs,n)->parse(rs.getString(1)),job);
        var out=mapper.createObjectNode().put("pageCount",total).put("asOf",asOf.toString()).put("status","COMPLETED").put("ocrModel","PaddleOCR-VL-1.6");
        String[] counters={"reviewedChunks","failedChunks","retriedChunks","blockCount","nativePageCount","ocrPageCount","extractedCharacterCount"};
        for(String counter:counters)out.put(counter,0);
        var candidates=new LinkedHashMap<String,JsonNode>();var warnings=new LinkedHashSet<String>();var failures=out.putObject("failureCodes");
        for(var result:results){
            if("PARTIAL".equals(result.path("status").asText()))out.put("status","PARTIAL");
            for(String counter:counters)out.put(counter,out.path(counter).asInt()+result.path(counter).asInt());
            for(var candidate:result.path("candidates"))candidates.put(candidate.path("id").asText(),candidate);
            for(var warning:result.path("warnings"))warnings.add(warning.asText());
            result.path("failureCodes").fields().forEachRemaining(entry->failures.put(entry.getKey(),failures.path(entry.getKey()).asInt()+entry.getValue().asInt()));
            out.set("catalogProducts",result.path("catalogProducts"));out.put("catalogVersion",result.path("catalogVersion").asText());
        }
        out.set("candidates",mapper.valueToTree(candidates.values()));out.set("warnings",mapper.valueToTree(warnings));
        if(out.path("extractedCharacterCount").asInt()>2_000_000)throw new ProcessingFailure("TEXT_TOO_LARGE");
        if(out.toString().getBytes(StandardCharsets.UTF_8).length>10*1024*1024)throw new ProcessingFailure("RESULT_TOO_LARGE");
        return out;
    }
    private void checkpoint(Input in,int page,int total,JsonNode result){
        if(result.path("page").asInt()!=page||result.path("pageCount").asInt()!=total||!result.path("candidates").isArray()
                ||!Set.of("COMPLETED","PARTIAL").contains(result.path("status").asText()))throw new ProcessingFailure("INVALID_PROCESSOR_RESULT");
        for(var candidate:result.path("candidates"))if(candidate.path("page").asInt()!=page||!"NEEDS_REVIEW".equals(candidate.path("status").asText()))throw new ProcessingFailure("INVALID_PROCESSOR_RESULT");
        tx.executeWithoutResult(status->{
            jdbc.queryForObject("SELECT attempt FROM manual_review_jobs WHERE job_id=? FOR UPDATE",Integer.class,in.job());
            if(!active(in))return;
            boolean complete=result.path("failedChunks").asInt()==0&&result.path("emptyPages").isEmpty();
            jdbc.update("""
                INSERT INTO manual_review_pages(job_id,page_no,complete,output) VALUES(?,?,?,?::jsonb)
                ON CONFLICT(job_id,page_no) DO UPDATE SET complete=EXCLUDED.complete,output=EXCLUDED.output,updated_at=now()
                """,in.job(),page,complete,result.toString());
            var out=aggregate(in.job(),total,in.asOf());
            int count=jdbc.queryForObject("SELECT count(*) FROM manual_review_pages WHERE job_id=? AND complete",Integer.class,in.job());
            jdbc.update("UPDATE manual_review_jobs SET completed_pages=?,output=?::jsonb,updated_at=now() WHERE job_id=? AND attempt=? AND status='RUNNING'",count,out.toString(),in.job(),in.attempt());
        });
    }
    private void analyze(Input in){
        String failure=null;
        try{
            if(!active(in))return;
            var body=mapper.createObjectNode().put("pdfBase64",Base64.getEncoder().encodeToString(in.bytes())).put("asOf",in.asOf().toString());
            var prepared=remote("POST",path(in)+"/prepare",body,60);
            int total=prepared.path("totalPages").asInt();String version=prepared.path("processorVersion").asText();
            if(total<1||total>300||version.isBlank())throw new ProcessingFailure("INVALID_PROCESSOR_RESULT");
            tx.executeWithoutResult(status->{
                String old=jdbc.queryForObject("SELECT processor_version FROM manual_review_jobs WHERE job_id=? FOR UPDATE",String.class,in.job());
                if(!active(in))return;
                if(old!=null&&!old.equals(version))throw new ProcessingFailure("CONTEXT_CHANGED");
                jdbc.update("UPDATE manual_review_jobs SET total_pages=?,processor_version=?,updated_at=now() WHERE job_id=? AND attempt=? AND status='RUNNING'",total,version,in.job(),in.attempt());
            });
            long deadline=System.nanoTime()+TimeUnit.HOURS.toNanos(6);
            for(int current=1;current<=total&&active(in);current++){
                if(System.nanoTime()>deadline)throw new ProcessingFailure("TIME_LIMIT");
                if(jdbc.queryForObject("SELECT count(*) FROM manual_review_pages WHERE job_id=? AND page_no=? AND complete",Integer.class,in.job(),current)>0)continue;
                jdbc.update("UPDATE manual_review_jobs SET current_page=?,stage='EXTRACTING',updated_at=now() WHERE job_id=? AND attempt=? AND status='RUNNING'",current,in.job(),in.attempt());
                var result=page(in,current,deadline);
                if(result==null)return;
                checkpoint(in,current,total,result);
            }
            if(active(in)){
                jdbc.update("UPDATE manual_review_jobs SET stage='FINALIZING',updated_at=now() WHERE job_id=? AND attempt=? AND status='RUNNING'",in.job(),in.attempt());
                if(!stopRemote(in)){pausing(in,"STOP_NOT_CONFIRMED");return;}
                var output=aggregate(in.job(),total,in.asOf());
                terminal(in,output.path("status").asText(),null);
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();failure="INTERRUPTED";}
        catch(ProcessingFailure e){failure=e.code;}
        catch(Exception e){failure="OCR_OR_LLM_UNAVAILABLE";}
        finally{
            boolean stopped=stopRemote(in);
            if(failure!=null){if(stopped)terminal(in,"FAILED",failure);else pausing(in,"STOP_NOT_CONFIRMED");}
        }
    }
}
