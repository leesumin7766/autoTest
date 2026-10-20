package com.autotest.test_management_service.application.testcase;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.autotest.test_management_service.application.crawler.PlaywrightSetupService;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

@Service
public class FunctionalMenuService {
    public static final String VERSION="functional-menu-v1";
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final SubmissionPersistenceService persistence;
    private final SubmissionRepository submissions;private final PlaywrightSetupService setups;private final FunctionalMenuValidator validator;private final FunctionalMenuExcel excel;
    private final TransactionTemplate tx;private final String aiUrl,token;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    private final ConcurrentMap<UUID,Future<?>> tasks=new ConcurrentHashMap<>();
    public FunctionalMenuService(JdbcTemplate jdbc,ObjectMapper mapper,SubmissionPersistenceService persistence,SubmissionRepository submissions,
            PlaywrightSetupService setups,FunctionalMenuValidator validator,FunctionalMenuExcel excel,PlatformTransactionManager manager,
            @Value("${ai-service.base-url:http://localhost:8005}") String aiUrl,@Value("${product-description.internal-token:}") String token) {
        this.jdbc=jdbc;this.mapper=mapper;this.persistence=persistence;this.submissions=submissions;this.setups=setups;this.validator=validator;this.excel=excel;
        this.tx=new TransactionTemplate(manager);this.aiUrl=aiUrl;this.token=token;
    }
    @EventListener(ApplicationReadyEvent.class) public void recover(){jdbc.update("UPDATE functional_menu_jobs SET status='FAILED',error_code='INTERRUPTED',updated_at=now() WHERE status='GENERATING'");}
    @jakarta.annotation.PreDestroy public void close(){executor.shutdownNow();}
    private ProductDescriptionException error(HttpStatus status,String code,String text){return new ProductDescriptionException(status,code,text);}
    private void owner(UUID id,long member){var found=submissions.findById(SubmissionId.of(id)).orElseThrow(()->error(HttpStatus.NOT_FOUND,"SUBMISSION_NOT_FOUND","문서 세트를 찾을 수 없습니다."));if(found.memberId().value()!=member)throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","이 문서 세트에 접근할 수 없습니다.");}
    private String digest(UUID id){return DocumentDigest.of(persistence.deliveryDocuments(persistence.findDocumentsBySubmissionId(SubmissionId.of(id))));}
    private JsonNode parse(String value){try{return mapper.readTree(value);}catch(Exception e){throw new IllegalStateException("MENU_STORAGE_INVALID");}}
    private ObjectNode snapshot(UUID id,long member,UUID crawl,String product){
        setups.get(id,member);String digest=digest(id);
        var jobs=jdbc.query("SELECT document_digest,status,COALESCE(setup_snapshot->>'targetUrl','') FROM playwright_crawl_jobs WHERE job_id=? AND submission_id=? AND member_id=? AND purpose='CRAWL'",
                (rs,n)->List.of(rs.getString(1).trim(),rs.getString(2),rs.getString(3)),crawl,id,member);
        if(jobs.isEmpty() || !"COMPLETED".equals(jobs.getFirst().get(1)))throw error(HttpStatus.CONFLICT,"CRAWL_REQUIRED","완료된 화면 수집 실행을 선택하세요.");
        if(!digest.equals(jobs.getFirst().get(0)))throw error(HttpStatus.CONFLICT,"CRAWL_STALE","문서 변경 후 다시 수집한 결과를 선택하세요.");
        ObjectNode input=mapper.createObjectNode().put("productName",product).put("documentDigest",digest).put("crawlJobId",crawl.toString()).put("rulesVersion",VERSION);
        input.put("targetUrl",jobs.getFirst().get(2));
        var evidence=input.putArray("evidence");var warnings=input.putArray("warnings");
        for(var doc:persistence.deliveryDocuments(persistence.findDocumentsBySubmissionId(SubmissionId.of(id)))) {
            if(!Set.of("FUNCTION_LIST","MANUAL").contains(doc.role().name()))continue;
            chunks(evidence,doc.extractedText(),mapper.createObjectNode().put("kind","DOCUMENT").put("fileId",doc.fileId().toString()).put("role",doc.role().name()).put("filename",doc.originalFilename()),"doc:"+doc.fileId());
        }
        Set<String> seen=new HashSet<>();boolean located=false;
        List<JsonNode> pages=jdbc.query("SELECT page_index,url,title,body_text,navigation::text,screen_state::text,nav_transition::text FROM playwright_crawl_pages WHERE job_id=? ORDER BY page_index",
                (rs,n)->{ObjectNode page=mapper.createObjectNode().put("pageIndex",rs.getInt(1)).put("url",rs.getString(2)).put("title",rs.getString(3)).put("bodyText",rs.getString(4));
                    page.set("navigation",parse(rs.getString(5)));page.set("transition",parse(rs.getString(7)));return page;},crawl);
        // Fetch screen status separately to keep original page evidence, including diagnostic captures.
        for(JsonNode page:pages){
            int index=page.path("pageIndex").asInt();
            var state=jdbc.queryForObject("SELECT screen_state::text FROM playwright_crawl_pages WHERE job_id=? AND page_index=?",String.class,crawl,index);
            String readiness=parse(state).path("readiness").asText("UNVERIFIED");
            chunks(evidence,page.path("bodyText").asText(),mapper.createObjectNode().put("kind","SCREEN").put("pageIndex",index).put("url",page.path("url").asText()).put("readiness",readiness),"screen:"+index);
            for(JsonNode nav:page.path("navigation")){
                if("GROUP".equals(nav.path("kind").asText()))continue;
                String key=nav.path("menuKey").asText();if(key.isBlank() || !seen.add(key))continue;
                ObjectNode item=mapper.createObjectNode().put("id","menu:"+key).put("kind","MENU").put("menuKey",key).put("pageIndex",index)
                        .put("url",nav.path("url").asText()).put("location",nav.path("location").asText("UNKNOWN")).put("readiness",readiness);
                item.set("path",nav.path("path").deepCopy());item.put("text",String.join(" → ",mapper.convertValue(nav.path("path"),new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){})));
                located|=!"UNKNOWN".equals(item.path("location").asText());evidence.add(item);
            }
            var controls=parse(jdbc.queryForObject("SELECT elements::text FROM playwright_crawl_pages WHERE job_id=? AND page_index=?",String.class,crawl,index));
            for(JsonNode control:controls){
                String label=control.path("label").asText().trim();if(!"button".equals(control.path("tag").asText())||label.isBlank()||label.length()>160)continue;
                boolean inMenu=false;for(JsonNode nav:page.path("navigation")){JsonNode path=nav.path("path");if(path.isArray()&&!path.isEmpty()&&label.equals(path.get(path.size()-1).asText())){inMenu=true;break;}}if(inMenu)continue;
                String identity=control.path("frameUrl").asText()+page.path("transition").path("menuPath").toString()+label;
                String key;try{key=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}
                if(!seen.add(key))continue;
                var item=evidence.addObject().put("id","control:"+key).put("kind","CONTROL").put("menuKey",key).put("pageIndex",index).put("location",control.path("location").asText("CONTENT"))
                        .put("readiness",readiness).put("text",label);
                var path=item.putArray("path");for(JsonNode part:page.path("transition").path("menuPath"))path.add(part.asText());path.add(label);
            }
        }
        if(!located)warnings.add("상단·하단 위치 정보가 없는 이전 수집입니다. 최신 실행기로 재수집하면 위치별 메뉴를 포함할 수 있습니다.");
        warnings.add("메뉴·문서 연결과 메뉴 설명은 사용자 검토가 필요한 제안입니다. 클릭 확인은 기능의 합격 판정이 아닙니다.");
        if(evidence.size()>5000 || input.toString().getBytes(StandardCharsets.UTF_8).length>10*1024*1024)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"MENU_INPUT_TOO_LARGE","입력 한도(5,000개 근거·10MiB)를 초과했습니다. 수집 범위를 조정하세요. 자료를 임의로 잘라내지 않았습니다.");
        return input;
    }
    private void chunks(ArrayNode out,String text,ObjectNode metadata,String prefix){
        if(text.length()>5_000_000)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"MENU_INPUT_TOO_LARGE","입력 자료가 너무 큽니다. 자료를 임의로 잘라내지 않았습니다.");
        for(int start=0;start<text.length();){if(out.size()>=5000)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"MENU_INPUT_TOO_LARGE","근거가 5,000개를 초과했습니다. 자료를 임의로 잘라내지 않았습니다.");int end=Math.min(text.length(),start+1100);if(end<text.length()&&Character.isLowSurrogate(text.charAt(end)))end--;
            ObjectNode source=metadata.deepCopy().put("id",prefix+":"+start+":"+end).put("characterStart",start).put("characterEnd",end).put("text",text.substring(start,end));out.add(source);
            if(end==text.length())break;start=Math.max(start+1,end-80);if(Character.isLowSurrogate(text.charAt(start)))start--;}
    }
    public ObjectNode start(UUID id,long member,UUID crawl,String product,String mode){
        owner(id,member);if(product==null || product.isBlank() || product.length()>120 || !Set.of("AUTO","EXTERNAL").contains(mode))throw error(HttpStatus.BAD_REQUEST,"MENU_INPUT_INVALID","제품명과 생성 방식을 입력하세요.");
        UUID job=UUID.randomUUID();ObjectNode input=tx.execute(status->{submissions.findByIdForUpdate(SubmissionId.of(id)).orElseThrow();
            if(jdbc.queryForObject("SELECT count(*) FROM functional_menu_jobs WHERE submission_id=? AND status='GENERATING'",Integer.class,id)>0)throw error(HttpStatus.CONFLICT,"MENU_ALREADY_RUNNING","이미 생성 중인 메뉴트리가 있습니다.");
            ObjectNode value=snapshot(id,member,crawl,product.trim());
            jdbc.update("INSERT INTO functional_menu_jobs(job_id,submission_id,member_id,crawl_job_id,document_digest,product_name,rules_version,status,generation_mode,snapshot,output) VALUES (?,?,?,?,?,?,?, ?,?,?::jsonb,?::jsonb)",
                    job,id,member,crawl,value.path("documentDigest").asText(),product.trim(),VERSION,"AUTO".equals(mode)?"GENERATING":"DRAFT",mode,value.toString(),"AUTO".equals(mode)?null:mapper.createObjectNode().put("generationMode","EXTERNAL").set("rows",mapper.createArrayNode()).toString());return value;});
        if("AUTO".equals(mode)){
            var task=new FutureTask<Void>(()->{try{ObjectNode result=(ObjectNode)post("generate",request(job,input),Duration.ofMinutes(45));
                if(!VERSION.equals(result.path("schemaVersion").asText())||!VERSION.equals(result.path("rulesVersion").asText())||!Set.of("REAL","MOCK").contains(result.path("generationMode").asText()))throw error(HttpStatus.BAD_GATEWAY,"MENU_RULE_VERSION_CHANGED","작성 규칙 버전 또는 생성 방식이 일치하지 않습니다.");
                ArrayNode rows=validator.validate(ensureMenus(result.path("rows"),input),input,null);result.set("rows",rows);
                jdbc.update("UPDATE functional_menu_jobs SET status='DRAFT',generation_mode=?,output=?::jsonb,revision=revision+1,updated_at=now() WHERE job_id=? AND status='GENERATING'",result.path("generationMode").asText(),result.toString(),job);
            }catch(Exception ex){String code=ex instanceof ProductDescriptionException p?p.code():"MENU_GENERATION_FAILED";
                jdbc.update("UPDATE functional_menu_jobs SET status='FAILED',error_code=?,updated_at=now() WHERE job_id=? AND status='GENERATING'",code,job);
            }finally{tasks.remove(job);}return null;});tasks.put(job,task);executor.execute(task);
        }
        return status(id,member,job);
    }
    private ObjectNode request(UUID job,JsonNode input){ObjectNode body=mapper.createObjectNode().put("jobId",job.toString()).put("productName",input.path("productName").asText());body.set("evidence",input.path("evidence"));return body;}
    private JsonNode post(String path,JsonNode body,Duration timeout){
        if(token.isBlank())throw error(HttpStatus.SERVICE_UNAVAILABLE,"INTERNAL_AUTH_NOT_CONFIGURED","생성 서비스 인증 설정이 없습니다.");
        try{var response=http.send(HttpRequest.newBuilder(URI.create(aiUrl+"/api/v1/functional-menus/"+path)).timeout(timeout).header("X-Internal-Token",token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()/100!=2){String detail=parse(response.body()).path("detail").asText();String code=detail.matches("[A-Z_]{5,80}")?detail:"MENU_GENERATION_FAILED";throw error(HttpStatus.BAD_GATEWAY,code,"메뉴트리 생성 응답을 검증하지 못했습니다. 오류 코드: "+code);}return parse(response.body());
        }catch(ProductDescriptionException known){throw known;}catch(Exception failed){throw error(HttpStatus.BAD_GATEWAY,"MENU_SERVICE_UNAVAILABLE","생성 서비스에 연결하지 못했거나 중단되었습니다.");}
    }
    private ArrayNode ensureMenus(JsonNode rows,JsonNode input){
        if(!rows.isArray())throw error(HttpStatus.BAD_REQUEST,"MENU_ROWS_INVALID","rows 배열이 필요합니다.");
        ArrayNode result=rows.deepCopy();Set<String> matched=new HashSet<>();for(JsonNode row:rows)for(JsonNode key:row.path("matchedMenuKeys"))matched.add(key.asText());
        for(JsonNode source:input.path("evidence"))if(Set.of("MENU","CONTROL").contains(source.path("kind").asText())&&!matched.contains(source.path("menuKey").asText())){
            var path=new ArrayList<String>();for(JsonNode part:source.path("path"))if(!Set.of("Global","Local","메뉴","탭").contains(part.asText()))path.add(part.asText());
            String name=path.isEmpty()?source.path("text").asText():path.removeLast();
            var row=result.addObject().put("functionName",name).put("menuDescription",name+" 메뉴를 제공하는 기능").put("location",source.path("location").asText("UNKNOWN")).put("included","MENU".equals(source.path("kind").asText()));
            row.set("menuPath",mapper.valueToTree(path.stream().limit(3).toList()));row.putArray("matchedMenuKeys").add(source.path("menuKey").asText());
            row.putArray("evidence").addObject().put("sourceId",source.path("id").asText()).put("quote",source.path("text").asText().substring(0,Math.min(300,source.path("text").asText().length())));
            row.putArray("reviewNotes").add("실제 메뉴 존재 근거입니다. 세부 동작과 분류는 검토가 필요합니다.");
        }
        Map<String,Integer> menuOrder=new HashMap<>();for(JsonNode source:input.path("evidence"))if(Set.of("MENU","CONTROL").contains(source.path("kind").asText()))menuOrder.put(source.path("menuKey").asText(),menuOrder.size());
        List<JsonNode> ordered=new ArrayList<>();result.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(row->{int order=100000;for(JsonNode key:row.path("matchedMenuKeys"))order=Math.min(order,menuOrder.getOrDefault(key.asText(),100000));return order;}));
        return mapper.valueToTree(ordered);
    }
    private ObjectNode stored(UUID id,long member,UUID job){owner(id,member);var rows=jdbc.query("SELECT *,snapshot::text AS input_json,output::text AS output_json FROM functional_menu_jobs WHERE submission_id=? AND job_id=? AND member_id=?",(rs,n)->{
        ObjectNode value=mapper.createObjectNode().put("jobId",rs.getString("job_id")).put("crawlJobId",rs.getString("crawl_job_id")).put("status",rs.getString("status")).put("productName",rs.getString("product_name"))
                .put("revision",rs.getInt("revision")).put("generationMode",rs.getString("generation_mode")).put("errorCode",rs.getString("error_code")).put("documentDigest",rs.getString("document_digest").trim());
        value.set("snapshot",parse(rs.getString("input_json")));value.set("output",rs.getString("output_json")==null?mapper.nullNode():parse(rs.getString("output_json")));return value;},id,job,member);
        if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"MENU_JOB_NOT_FOUND","메뉴트리 작업을 찾을 수 없습니다.");return rows.getFirst();}
    public ObjectNode status(UUID id,long member,UUID selected){owner(id,member);ObjectNode response=mapper.createObjectNode();
        var history=jdbc.query("SELECT job_id,status,product_name FROM functional_menu_jobs WHERE submission_id=? ORDER BY created_at DESC LIMIT 20",(rs,n)->Map.of("jobId",rs.getString(1),"status",rs.getString(2),"productName",rs.getString(3)),id);
        response.set("history",mapper.valueToTree(history));response.set("crawls",mapper.valueToTree(jdbc.query("SELECT job_id,page_count,created_at,COALESCE(setup_snapshot->>'targetUrl','') FROM playwright_crawl_jobs WHERE submission_id=? AND status='COMPLETED' AND purpose='CRAWL' ORDER BY created_at DESC LIMIT 20",(rs,n)->Map.of("jobId",rs.getString(1),"pageCount",rs.getInt(2),"createdAt",rs.getTimestamp(3).toInstant().toString(),"targetUrl",rs.getString(4)),id)));
        if(selected==null&&!history.isEmpty())selected=UUID.fromString(history.getFirst().get("jobId"));
        if(selected==null){response.putNull("job");return response;}
        ObjectNode job=stored(id,member,selected);job.put("stale",!job.path("documentDigest").asText().equals(digest(id)));
        var newest=jdbc.query("SELECT job_id FROM playwright_crawl_jobs WHERE submission_id=? AND purpose='CRAWL' AND status='COMPLETED' ORDER BY created_at DESC LIMIT 1",(rs,n)->rs.getString(1),id);
        job.put("newerCrawlAvailable",!newest.isEmpty()&&!newest.getFirst().equals(job.path("crawlJobId").asText()));
        JsonNode input=job.path("snapshot");job.set("warnings",input.path("warnings"));job.put("targetUrl",input.path("targetUrl").asText());
        var sources=job.putArray("sources");for(JsonNode source:input.path("evidence")){ObjectNode metadata=source.deepCopy();metadata.remove("text");sources.add(metadata);}
        if("GENERATING".equals(job.path("status").asText())){try{var progress=http.send(HttpRequest.newBuilder(URI.create(aiUrl+"/api/v1/functional-menus/progress/"+selected)).timeout(Duration.ofSeconds(2)).header("X-Internal-Token",token).GET().build(),HttpResponse.BodyHandlers.ofString());if(progress.statusCode()==200)job.set("progress",parse(progress.body()));}catch(Exception ignored){}}
        job.remove(List.of("snapshot","documentDigest"));response.set("job",job);return response;
    }
    public JsonNode prompt(UUID id,long member,UUID job){var stored=stored(id,member,job);return post("prompt",request(job,stored.path("snapshot")),Duration.ofSeconds(30));}
    public JsonNode evidence(UUID id,long member,UUID job,String sourceId){for(JsonNode source:stored(id,member,job).path("snapshot").path("evidence"))if(sourceId.equals(source.path("id").asText()))return source;throw error(HttpStatus.NOT_FOUND,"MENU_EVIDENCE_NOT_FOUND","근거를 찾을 수 없습니다.");}
    public ObjectNode imported(UUID id,long member,UUID job,int revision,JsonNode rows){var old=stored(id,member,job);
        if("GENERATING".equals(old.path("status").asText()))throw error(HttpStatus.CONFLICT,"MENU_RUNNING","생성이 끝난 뒤 가져오세요.");
        ArrayNode validated=validator.validate(ensureMenus(rows,old.path("snapshot")),old.path("snapshot"),null);ObjectNode output=mapper.createObjectNode().put("schemaVersion",VERSION).put("rulesVersion",VERSION).put("generationMode","EXTERNAL");output.set("rows",validated);
        if(jdbc.update("UPDATE functional_menu_jobs SET status='DRAFT',generation_mode='EXTERNAL',output=?::jsonb,revision=revision+1,error_code=NULL,updated_at=now() WHERE job_id=? AND revision=? AND status<>'GENERATING'",output.toString(),job,revision)!=1)throw error(HttpStatus.CONFLICT,"MENU_REVISION_CONFLICT","다른 변경이 있습니다. 다시 조회하세요.");return status(id,member,job);}
    public ObjectNode edit(UUID id,long member,UUID job,int revision,JsonNode rows,boolean approve){
        owner(id,member);
        tx.execute(status->{submissions.findByIdForUpdate(SubmissionId.of(id)).orElseThrow();var old=stored(id,member,job);
            if(!Set.of("DRAFT","APPROVED").contains(old.path("status").asText()))throw error(HttpStatus.CONFLICT,"MENU_NOT_READY","검토 가능한 초안이 없습니다.");
            setups.get(id,member);if(!digest(id).equals(old.path("documentDigest").asText()))throw error(HttpStatus.CONFLICT,"MENU_STALE","문서가 변경되어 다시 생성해야 합니다.");
            if(approve&&"MOCK".equals(old.path("generationMode").asText()))throw error(HttpStatus.CONFLICT,"MENU_MOCK_NOT_APPROVABLE","모의 결과는 최종 승인할 수 없습니다. 실제 생성 또는 외부 LLM 결과를 사용하세요.");
            ArrayNode valid=validator.validate(rows,old.path("snapshot"),old.path("output").path("rows"));
            if(approve&&java.util.stream.StreamSupport.stream(valid.spliterator(),false).noneMatch(row->row.path("included").asBoolean()))throw error(HttpStatus.BAD_REQUEST,"MENU_EMPTY","한 개 이상의 기능을 포함하세요.");
            ObjectNode output=old.path("output").deepCopy();output.set("rows",valid);
            if(jdbc.update("UPDATE functional_menu_jobs SET status=?,output=?::jsonb,revision=revision+1,updated_at=now() WHERE job_id=? AND revision=? AND status IN ('DRAFT','APPROVED')",approve?"APPROVED":"DRAFT",output.toString(),job,revision)!=1)throw error(HttpStatus.CONFLICT,"MENU_REVISION_CONFLICT","다른 변경이 있습니다. 다시 조회하세요.");return null;});return status(id,member,job);
    }
    public void cancel(UUID id,long member,UUID job){stored(id,member,job);jdbc.update("UPDATE functional_menu_jobs SET status='CANCELED',updated_at=now() WHERE job_id=? AND status='GENERATING'",job);var future=tasks.remove(job);if(future!=null)future.cancel(true);}
    public byte[] download(UUID id,long member,UUID job,boolean draft){var value=stored(id,member,job);boolean approved="APPROVED".equals(value.path("status").asText());
        if(!(approved||draft&&"DRAFT".equals(value.path("status").asText())))throw error(HttpStatus.CONFLICT,"MENU_NOT_APPROVED","초안을 검토·승인한 뒤 다운로드하세요.");
        if(!digest(id).equals(value.path("documentDigest").asText()))throw error(HttpStatus.CONFLICT,"MENU_STALE","문서가 변경되어 다시 생성해야 합니다.");
        return excel.export(value.path("productName").asText(),value.path("output"),value.path("snapshot"),approved);
    }
}
