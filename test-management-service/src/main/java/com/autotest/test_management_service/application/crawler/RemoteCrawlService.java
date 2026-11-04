package com.autotest.test_management_service.application.crawler;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class RemoteCrawlService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PlaywrightSetupService setups;
    private final PlaywrightCredentialCipher cipher;
    private final SubmissionRepository submissions;
    private final TransactionTemplate tx;
    private final LocalCrawlDispatcher local;
    private static final Set<String> FAILURES=Set.of("LOGIN_FAILED","LOGIN_CONFIRMATION_REQUIRED","LOGIN_METHOD_UNSUPPORTED",
            "TARGET_UNREACHABLE","CERTIFICATE_ERROR","CRAWL_FAILED","AGENT_DISCONNECTED","CAPTURE_TOO_LARGE",
            "LOGIN_NOT_CONFIRMED","LOCAL_RUNNER_UNAVAILABLE","CONNECTION_SCOPE_BLOCKED","PAGE_LOAD_TIMEOUT");
    public RemoteCrawlService(JdbcTemplate jdbc,ObjectMapper mapper,PlaywrightSetupService setups,
            PlaywrightCredentialCipher cipher,SubmissionRepository submissions,PlatformTransactionManager manager,LocalCrawlDispatcher local) {
        this.jdbc=jdbc; this.mapper=mapper; this.setups=setups; this.cipher=cipher; this.submissions=submissions;
        this.tx=new TransactionTemplate(manager);
        this.local=local;
    }
    private ProductDescriptionException error(HttpStatus status,String code,String message) {
        return new ProductDescriptionException(status,code,message);
    }
    public record Agent(UUID agentId,String name,boolean online,Instant lastSeenAt,boolean busy) { }
    public record Provisioned(Agent agent,String token) {
        @Override public String toString() { return "ProvisionedAgent[redacted]"; }
    }
    public List<Agent> agents(long member) {
        recover();
        return jdbc.query("""
                SELECT a.*, last_seen_at>now()-interval '30 seconds' AS online,
                    EXISTS(SELECT 1 FROM playwright_crawl_jobs j WHERE j.agent_id=a.agent_id AND j.status IN ('QUEUED','RUNNING')) AS busy
                FROM crawl_agents a WHERE member_id=? AND disabled=false ORDER BY created_at
                """,(rs,n)->new Agent(rs.getObject("agent_id",UUID.class),rs.getString("name"),rs.getBoolean("online"),
                        rs.getTimestamp("last_seen_at")==null?null:rs.getTimestamp("last_seen_at").toInstant(),rs.getBoolean("busy")),member);
    }
    public Provisioned provision(long member,String name) {
        if(name==null || name.isBlank() || name.length()>160) throw error(HttpStatus.BAD_REQUEST,"AGENT_NAME_INVALID","실행 PC 이름을 입력해 주세요.");
        if(jdbc.queryForObject("SELECT count(*) FROM crawl_agents WHERE member_id=? AND disabled=false",Integer.class,member)>=20)
            throw error(HttpStatus.CONFLICT,"AGENT_LIMIT","실행기는 회원별로 최대 20개까지 등록할 수 있습니다.");
        UUID id=UUID.randomUUID(); byte[] key=new byte[32]; new SecureRandom().nextBytes(key);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        jdbc.update("INSERT INTO crawl_agents(agent_id,member_id,name,secret_ciphertext) VALUES (?,?,?,?)",id,member,name.trim(),cipher.encrypt(id,token));
        return new Provisioned(new Agent(id,name.trim(),false,null,false),token);
    }
    public void revoke(long member,UUID agent) {
        tx.executeWithoutResult(status->{
            if(jdbc.update("UPDATE crawl_agents SET disabled=true,secret_ciphertext='' WHERE agent_id=? AND member_id=? AND disabled=false",agent,member)==0)
                throw error(HttpStatus.NOT_FOUND,"AGENT_NOT_FOUND","실행기를 찾을 수 없습니다.");
            jdbc.update("UPDATE playwright_crawl_jobs SET status='CANCELED',password_ciphertext=NULL,updated_at=now() WHERE agent_id=? AND status IN ('QUEUED','RUNNING')",agent);
        });
    }
    public void seen(UUID agent) { jdbc.update("UPDATE crawl_agents SET last_seen_at=now() WHERE agent_id=? AND disabled=false",agent); }
    private void owner(UUID submission,long member) {
        var rows=jdbc.query("SELECT member_id FROM submissions WHERE submission_id=?",(rs,n)->rs.getLong(1),submission);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"SUBMISSION_NOT_FOUND","문서 세트를 찾을 수 없습니다.");
        if(rows.getFirst()!=member) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","이 문서 세트에 접근할 수 없습니다.");
    }
    private void recover() {
        jdbc.update("""
                UPDATE playwright_crawl_jobs SET status='FAILED',error_code=CASE WHEN execution_mode='LOCAL' THEN 'LOCAL_RUNNER_UNAVAILABLE' ELSE 'AGENT_DISCONNECTED' END,password_ciphertext=NULL,updated_at=now()
                WHERE (status='RUNNING' AND lease_expires_at<now()) OR (status='QUEUED' AND created_at<now()-interval '10 minutes')
                """);
    }
    public ObjectNode start(UUID submission,long member,UUID agentId) {
        return start(submission,member,agentId,"REMOTE","CRAWL",null);
    }
    public ObjectNode start(UUID submission,long member,UUID agentId,String executionMode,String purpose,PlaywrightSetupService.SetupRequest draft) {
        if(!Set.of("LOCAL","REMOTE").contains(executionMode) || !Set.of("CONNECTION","LOGIN","CRAWL").contains(purpose))
            throw error(HttpStatus.BAD_REQUEST,"CRAWL_REQUEST_INVALID","실행 위치와 테스트 유형을 확인해 주세요.");
        recover(); owner(submission,member);
        var available=agents(member).stream().filter(a->a.online()&&!a.busy()).toList();
        UUID selected=agentId;
        if(selected==null && available.size()==1 && "REMOTE".equals(executionMode)) selected=available.getFirst().agentId();
        if("LOCAL".equals(executionMode)) selected=null;
        UUID agent=selected;
        if("REMOTE".equals(executionMode) && (agent==null || available.stream().noneMatch(a->a.agentId().equals(agent))))
            throw error(HttpStatus.CONFLICT,"AGENT_NOT_READY","연결된 실행 PC를 선택해 주세요. 실행기가 하나면 자동 선택합니다.");
        UUID id=UUID.randomUUID();
        try {
            tx.executeWithoutResult(status->{
                submissions.findByIdForUpdate(SubmissionId.of(submission)).orElseThrow();
                var prepared="CRAWL".equals(purpose)?null:setups.prepareCheck(submission,member,draft,"CONNECTION".equals(purpose));
                var setup=prepared==null?setups.get(submission,member):prepared.setup();
                if(setup==null || setup.stale()) throw error(HttpStatus.CONFLICT,"SETUP_REQUIRED","현재 문서 기준의 접속 설정을 먼저 저장해 주세요.");
                if(!Set.of("NONE","FORM_AUTO","BASIC_AUTH").contains(setup.loginMethod()))
                    throw error(HttpStatus.CONFLICT,"LOGIN_METHOD_UNSUPPORTED","현재 자동 실행은 일반 로그인·Basic 인증·로그인 없음만 지원합니다.");
                String password=prepared==null?jdbc.queryForObject("SELECT password_ciphertext FROM playwright_crawl_setups WHERE submission_id=?",String.class,submission):prepared.passwordCiphertext();
                if(!"NONE".equals(setup.loginMethod()) && password==null) throw error(HttpStatus.CONFLICT,"PLAYWRIGHT_PASSWORD_REQUIRED","로그인 비밀번호를 저장해 주세요.");
                jdbc.update("""
                        INSERT INTO playwright_crawl_jobs(job_id,submission_id,member_id,agent_id,status,setup_snapshot,password_ciphertext,document_digest,created_at,execution_mode,purpose)
                        VALUES (?,?,?,?,'QUEUED',?::jsonb,?,?,clock_timestamp(),?,?)
                        """,id,submission,member,agent,json(setup),password,setups.verifiedDigest(submission,member),executionMode,purpose);
            });
        } catch(DuplicateKeyException busy) { throw error(HttpStatus.CONFLICT,"CRAWL_ALREADY_ACTIVE","이 문서 또는 실행 위치에서 테스트·수집 작업이 이미 진행 중입니다."); }
        if("LOCAL".equals(executionMode)) {
            ObjectNode payload=tx.execute(status->{
                var rows=jdbc.query("SELECT job_id,submission_id,member_id,document_digest,setup_snapshot::text,password_ciphertext,purpose FROM playwright_crawl_jobs WHERE job_id=? AND status='QUEUED' FOR UPDATE",
                        (rs,n)->new Pending(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getLong(3),rs.getString(4).trim(),rs.getString(5),rs.getString(6),rs.getString(7)),id);
                return claimJob(rows.getFirst());
            });
            if(payload.has("jobId")) local.dispatch(payload,code->jdbc.update("UPDATE playwright_crawl_jobs SET status='FAILED',error_code=?,password_ciphertext=NULL,updated_at=now() WHERE job_id=? AND status='RUNNING'",code,id));
        }
        return status(submission,member);
    }
    public ObjectNode status(UUID submission,long member) {
        owner(submission,member); recover();
        var rows=jdbc.query("""
                SELECT j.*,COALESCE(a.name,'현재 서버') AS agent_name FROM playwright_crawl_jobs j LEFT JOIN crawl_agents a USING(agent_id)
                WHERE submission_id=? ORDER BY j.created_at DESC
                """,(rs,n)->{
                    ObjectNode value=mapper.createObjectNode();
                    value.put("jobId",rs.getString("job_id")); value.put("status",rs.getString("status"));
                    value.put("agentName",rs.getString("agent_name")); value.put("pageCount",rs.getInt("page_count"));
                    value.put("errorCode",rs.getString("error_code")); value.put("stopReason",rs.getString("stop_reason"));
                    value.put("purpose",rs.getString("purpose"));value.put("executionMode",rs.getString("execution_mode"));
                    value.set("result",rs.getString("check_result")==null?mapper.nullNode():parse(rs.getString("check_result")));
                    value.set("crawlResult",rs.getString("crawl_result")==null?mapper.nullNode():parse(rs.getString("crawl_result")));
                    return value;
                },submission);
        ObjectNode result=mapper.createObjectNode();
        var crawl=rows.stream().filter(row->"CRAWL".equals(row.path("purpose").asText())).findFirst();
        result.set("job",crawl.isEmpty()?mapper.nullNode():crawl.get());
        ObjectNode checks=result.putObject("checks");
        for(String type:List.of("CONNECTION","LOGIN")) rows.stream().filter(row->type.equals(row.path("purpose").asText())).findFirst().ifPresent(row->checks.set(type,row));
        if(crawl.isPresent()) {
            UUID job=UUID.fromString(crawl.get().path("jobId").asText());
            result.set("pages",mapper.valueToTree(jdbc.query("SELECT page_index,url,title,screen_state::text,nav_transition::text FROM playwright_crawl_pages WHERE job_id=? ORDER BY page_index",
                    (rs,n)->new PageSummary(rs.getInt(1),rs.getString(2),rs.getString(3),parse(rs.getString(4)),parse(rs.getString(5))),job)));
        } else result.putArray("pages");
        return result;
    }
    public record PageSummary(int pageIndex,String url,String title,JsonNode screenState,JsonNode transition) { }
    public ObjectNode menuTree(UUID submission,long member,UUID job) {
        owner(submission,member);
        var states=jdbc.query("SELECT status FROM playwright_crawl_jobs WHERE job_id=? AND submission_id=? AND purpose='CRAWL'",
                (rs,n)->rs.getString(1),job,submission);
        if(states.isEmpty()) throw error(HttpStatus.NOT_FOUND,"CRAWL_NOT_FOUND","수집 작업을 찾을 수 없습니다.");
        var pages=jdbc.query("SELECT page_index,url,title,navigation::text,screen_state::text,nav_transition::text FROM playwright_crawl_pages WHERE job_id=? ORDER BY page_index",
                (rs,n)->new CapturedMenuTree.Page(rs.getInt(1),rs.getString(2),rs.getString(3),parse(rs.getString(4)),parse(rs.getString(5)),parse(rs.getString(6))),job);
        return CapturedMenuTree.build(mapper,pages).put("jobId",job.toString()).put("crawlStatus",states.getFirst());
    }
    public void cancel(UUID submission,long member,UUID job) {
        owner(submission,member);
        jdbc.update("UPDATE playwright_crawl_jobs SET status='CANCELED',password_ciphertext=NULL,updated_at=now() WHERE job_id=? AND submission_id=? AND status IN ('QUEUED','RUNNING')",job,submission);
    }
    public byte[] screenshot(UUID submission,long member,UUID job,int index) {
        owner(submission,member);
        var rows=jdbc.query("""
                SELECT p.screenshot FROM playwright_crawl_pages p JOIN playwright_crawl_jobs j USING(job_id)
                WHERE j.submission_id=? AND p.job_id=? AND p.page_index=?
                """,(rs,n)->rs.getBytes(1),submission,job,index);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"PAGE_NOT_FOUND","수집 화면을 찾을 수 없습니다.");
        return rows.getFirst();
    }
    public JsonNode evidence(UUID submission,long member,UUID job,int index) {
        owner(submission,member);
        var rows=jdbc.query("""
                SELECT p.url,p.title,p.body_text,p.elements::text,p.links::text,p.navigation::text,p.screen_state::text,p.nav_transition::text FROM playwright_crawl_pages p
                JOIN playwright_crawl_jobs j USING(job_id) WHERE j.submission_id=? AND p.job_id=? AND p.page_index=?
                """,(rs,n)->{
                    var value=mapper.createObjectNode(); value.put("url",rs.getString(1));value.put("title",rs.getString(2));
                    value.put("bodyText",rs.getString(3));value.set("elements",parse(rs.getString(4)));value.set("links",parse(rs.getString(5)));value.set("navigation",parse(rs.getString(6)));
                    value.set("screenState",parse(rs.getString(7)));value.set("transition",parse(rs.getString(8)));return value;
                },submission,job,index);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"PAGE_NOT_FOUND","수집 화면을 찾을 수 없습니다.");
        return rows.getFirst();
    }
    public ObjectNode claim(UUID agent) {
        seen(agent); recover();
        return tx.execute(status->{
            var rows=jdbc.query("SELECT job_id,submission_id,member_id,document_digest,setup_snapshot::text,password_ciphertext,purpose FROM playwright_crawl_jobs WHERE agent_id=? AND status='QUEUED' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
                    (rs,n)->new Pending(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getLong(3),rs.getString(4).trim(),rs.getString(5),rs.getString(6),rs.getString(7)),agent);
            if(rows.isEmpty()) return mapper.createObjectNode();
            return claimJob(rows.getFirst());
        });
    }
    private ObjectNode claimJob(Pending job) {
            try {
                if(!job.digest().equals(setups.verifiedDigest(job.submission(),job.member()))) throw new IllegalStateException();
            } catch(Exception changed) {
                jdbc.update("UPDATE playwright_crawl_jobs SET status='FAILED',error_code='DOCUMENT_VERSION_MISMATCH',password_ciphertext=NULL,updated_at=now() WHERE job_id=?",job.id());
                return mapper.createObjectNode();
            }
            UUID token=UUID.randomUUID();
            ObjectNode payload=(ObjectNode)parse(job.snapshot());
            if(job.password()!=null) payload.put("password",cipher.decrypt(job.submission(),job.password()));
            payload.put("jobId",job.id().toString());payload.put("claimToken",token.toString());
            payload.put("purpose",job.purpose());
            jdbc.update("UPDATE playwright_crawl_jobs SET status='RUNNING',claim_token=?,lease_expires_at=now()+interval '45 seconds',updated_at=now() WHERE job_id=?",token,job.id());
            return payload;
    }
    private record Pending(UUID id,UUID submission,long member,String digest,String snapshot,String password,String purpose) {
        @Override public String toString(){return "PendingCrawl[redacted]";}
    }
    public ObjectNode worker(UUID agent,JsonNode body) {
        UUID job; UUID token;
        try { job=UUID.fromString(body.path("jobId").asText()); token=UUID.fromString(body.path("claimToken").asText()); }
        catch(Exception invalid) { throw error(HttpStatus.BAD_REQUEST,"CRAWL_REQUEST_INVALID","작업 식별자를 확인해 주세요."); }
        return tx.execute(status->{
            var rows=jdbc.query("SELECT setup_snapshot::text,page_count,screenshot_bytes,purpose FROM playwright_crawl_jobs WHERE job_id=? AND agent_id IS NOT DISTINCT FROM ?::uuid AND claim_token=? AND status='RUNNING' AND lease_expires_at>now() FOR UPDATE",
                    (rs,n)->new Active(parse(rs.getString(1)),rs.getInt(2),rs.getLong(3),rs.getString(4)),job,agent,token);
            if(rows.isEmpty()) throw error(HttpStatus.CONFLICT,"CRAWL_LEASE_LOST","수집이 중단되었거나 실행 권한이 만료되었습니다.");
            Active active=rows.getFirst();
            String event=body.path("event").asText();
            if("page".equals(event)) {
                if(!"CRAWL".equals(active.purpose())) throw error(HttpStatus.BAD_REQUEST,"CRAWL_EVENT_INVALID","연결·로그인 테스트는 화면을 수집하지 않습니다.");
                storePage(job,active,body);
            }
            else if("complete".equals(event)) {
                if(!"CRAWL".equals(active.purpose())) {
                    JsonNode source=body.path("result");ObjectNode result=mapper.createObjectNode();
                    result.put("httpStatus",source.path("httpStatus").asInt()); result.put("elapsedMs",Math.max(0,Math.min(120000,source.path("elapsedMs").asInt())));
                    result.put("loginVerified","LOGIN".equals(active.purpose()) && source.path("loginVerified").asBoolean());
                    int http=result.path("httpStatus").asInt();
                    boolean verified="CONNECTION".equals(active.purpose()) ? http>=100 && http<=599 : result.path("loginVerified").asBoolean() && http>=200 && http<400;
                    jdbc.update("UPDATE playwright_crawl_jobs SET status=?,error_code=?,check_result=?::jsonb,password_ciphertext=NULL,updated_at=now() WHERE job_id=?",
                            verified?"COMPLETED":"FAILED",verified?null:"LOGIN_NOT_CONFIRMED",json(result),job);
                } else {
                String reason=body.path("stopReason").asText("DONE");
                if(!Set.of("DONE","PAGE_LIMIT","DEPTH_LIMIT","TIME_LIMIT","STORAGE_LIMIT").contains(reason)) reason="DONE";
                ObjectNode summary=mapper.createObjectNode();
                for(String field:List.of("incompleteCount","navigationFailureCount","noChangeCount","uniqueScreenCount"))
                    summary.put(field,Math.max(0,Math.min(500,body.path("result").path(field).asInt())));
                var skipped=body.path("result").path("skipped");
                if(skipped.isArray() && skipped.size()<=500 && json(skipped).length()<200_000) summary.set("skipped",skipped);
                jdbc.update("UPDATE playwright_crawl_jobs SET status=?,error_code=?,stop_reason=?,crawl_result=?::jsonb,password_ciphertext=NULL,updated_at=now() WHERE job_id=?",
                        active.count()>0?"COMPLETED":"FAILED",active.count()>0?null:"NO_PAGES_CAPTURED",reason,json(summary),job);
                }
            } else if("failed".equals(event)) {
                String code=body.path("errorCode").asText();
                jdbc.update("UPDATE playwright_crawl_jobs SET status='FAILED',error_code=?,password_ciphertext=NULL,updated_at=now() WHERE job_id=?",FAILURES.contains(code)?code:"CRAWL_FAILED",job);
            } else if(!"heartbeat".equals(event)) throw error(HttpStatus.BAD_REQUEST,"CRAWL_EVENT_INVALID","지원하지 않는 수집 이벤트입니다.");
            jdbc.update("UPDATE playwright_crawl_jobs SET lease_expires_at=now()+interval '45 seconds',updated_at=now() WHERE job_id=? AND status='RUNNING'",job);
            if(agent!=null) seen(agent);
            return mapper.createObjectNode().put("accepted",true);
        });
    }
    private record Active(JsonNode setup,int count,long bytes,String purpose) { }
    private void storePage(UUID job,Active active,JsonNode body) {
        int index=body.path("pageIndex").asInt(-1);
        if(index<active.count() && index>=0) return; // Retry after a lost acknowledgement.
        JsonNode config=active.setup().path("automation");
        if(index!=active.count() || index>=config.path("maxPages").asInt(100)) throw error(HttpStatus.CONFLICT,"CRAWL_PAGE_LIMIT","수집 화면 순서 또는 한도를 초과했습니다.");
        String url=body.path("url").asText();
        if(!permitted(active.setup(),url)) throw error(HttpStatus.BAD_REQUEST,"CRAWL_SCOPE_INVALID","허용 범위 밖의 수집 결과입니다.");
        byte[] image;
        try { image=Base64.getDecoder().decode(body.path("screenshot").asText()); }
        catch(Exception invalid) { throw error(HttpStatus.BAD_REQUEST,"CRAWL_PAGE_INVALID","스크린샷 형식이 올바르지 않습니다."); }
        String title=body.path("title").asText(), text=body.path("bodyText").asText();
        JsonNode elements=body.path("elements"), links=body.path("links");
        JsonNode navigation=body.has("navigation")?body.get("navigation"):mapper.createArrayNode();
        if(!navigation.isArray() || navigation.size()>500 || json(navigation).length()>500_000)
            throw error(HttpStatus.BAD_REQUEST,"CRAWL_PAGE_INVALID","메뉴 수집 결과의 크기 또는 형식이 올바르지 않습니다.");
        for(JsonNode item:navigation) {
            JsonNode path=item.path("path");
            if(!path.isArray() || path.isEmpty() || path.size()>12
                    || !Set.of("LINK","GROUP","TAB","BUTTON").contains(item.path("kind").asText())
                    || !permitted(active.setup(),item.path("frameUrl").asText())
                    || (!item.path("url").asText().isEmpty() && !permitted(active.setup(),item.path("url").asText())))
                throw error(HttpStatus.BAD_REQUEST,"CRAWL_PAGE_INVALID","메뉴 경로 또는 대상 URL이 올바르지 않습니다.");
            for(JsonNode label:path) if(!label.isTextual() || label.asText().isBlank() || label.asText().length()>160)
                throw error(HttpStatus.BAD_REQUEST,"CRAWL_PAGE_INVALID","메뉴 이름이 올바르지 않습니다.");
        }
        if(image.length<3 || image.length>2*1024*1024 || image[0]!=(byte)0xff || image[1]!=(byte)0xd8
                || title.length()>300 || text.length()>32_000 || !elements.isArray() || elements.size()>500
                || !links.isArray() || links.size()>500 || json(elements).length()>200_000 || json(links).length()>200_000)
            throw error(HttpStatus.BAD_REQUEST,"CRAWL_PAGE_INVALID","수집 결과의 크기 또는 형식이 올바르지 않습니다.");
        if(active.bytes()+image.length>64L*1024*1024) throw error(HttpStatus.CONFLICT,"CRAWL_STORAGE_LIMIT","작업당 화면 저장 한도 64 MiB를 초과했습니다.");
        JsonNode screen=body.has("screenState")?body.get("screenState"):mapper.createObjectNode();
        JsonNode transition=body.has("transition")?body.get("transition"):mapper.createObjectNode();
        if(!screen.isObject() || !transition.isObject() || json(screen).length()>100_000 || json(transition).length()>30_000
                || (!screen.isEmpty() && (!screen.path("screenKey").asText().matches("[a-f0-9]{64}")
                    || !Set.of("READY","LOAD_TIMEOUT").contains(screen.path("readiness").asText())))
                || (!transition.isEmpty() && (!Set.of("CONFIRMED","NO_STATE_CHANGE","LOAD_TIMEOUT").contains(transition.path("outcome").asText())
                    || !transition.path("menuKey").asText().matches("(?:[a-f0-9]{64})?"))))
            throw error(HttpStatus.BAD_REQUEST,"CRAWL_PAGE_INVALID","화면 상태 또는 메뉴 이동 근거가 올바르지 않습니다.");
        jdbc.update("INSERT INTO playwright_crawl_pages(job_id,page_index,url,title,body_text,elements,links,screenshot,navigation,screen_state,nav_transition) VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?,?::jsonb,?::jsonb,?::jsonb)",
                job,index,url,title,text,json(elements),json(links),image,json(navigation),json(screen),json(transition));
        jdbc.update("UPDATE playwright_crawl_jobs SET page_count=page_count+1,screenshot_bytes=screenshot_bytes+? WHERE job_id=?",image.length,job);
    }
    static boolean permitted(JsonNode setup,String value) {
        try {
            URI uri=URI.create(value);
            if(value.length()>2048 || uri.getUserInfo()!=null || uri.getRawQuery()!=null) return false;
            String origin=CrawlAutomation.origin(uri.getScheme()+"://"+uri.getRawAuthority());
            JsonNode config=setup.path("automation");
            boolean allowed=origin.equals(CrawlAutomation.origin(setup.path("targetUrl").asText()));
            for(JsonNode extra:config.path("allowedOrigins")) allowed|=origin.equals(CrawlAutomation.origin(extra.asText()));
            if(!allowed) return false;
            String path=uri.getRawPath()==null || uri.getRawPath().isEmpty()?"/":uri.getRawPath();
            for(JsonNode pattern:config.path("excludePaths")) if(glob(pattern.asText(),path)) return false;
            if(config.path("includePaths").isEmpty()) return true;
            for(JsonNode pattern:config.path("includePaths")) if(glob(pattern.asText(),path)) return true;
            return false;
        } catch(Exception invalid) { return false; }
    }
    private static boolean glob(String pattern,String path) {
        return path.matches(java.util.Arrays.stream(pattern.split("\\*",-1)).map(java.util.regex.Pattern::quote).collect(java.util.stream.Collectors.joining(".*")));
    }
    private String json(Object value) { try {return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Invalid crawl data");} }
    private JsonNode parse(String value) { try {return mapper.readTree(value);}catch(Exception e){throw new IllegalStateException("Invalid stored crawl data");} }
}
