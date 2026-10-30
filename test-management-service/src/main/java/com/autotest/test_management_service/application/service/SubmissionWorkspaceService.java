package com.autotest.test_management_service.application.service;

import com.autotest.common_lib.exception.CommonException;
import static com.autotest.common_lib.exception.CommonErrorCode.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Named entry point for existing submission artifacts, never a duplicate artifact store. */
@Service
public class SubmissionWorkspaceService {
    private final JdbcTemplate jdbc;
    private static final Set<String> PAGES=Set.of("documents","crawl","menu","tc-generate","execution","defects","reports");
    public SubmissionWorkspaceService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Workspace(UUID id,long productId,String name,String lastPage,Instant createdAt,Instant updatedAt,
                            int documentCount,int parsedDocumentCount,String deliveryStatus,int crawlCount,int menuCount,int tcSetCount,int runCount){}
    private static final String SELECT="""
        SELECT s.submission_id,s.product_id,COALESCE(w.name,'시험 작업 ' || left(s.submission_id::text,8)) AS name,
               COALESCE(w.last_page,'documents') AS last_page,s.uploaded_at,COALESCE(w.updated_at,s.uploaded_at) AS updated_at,
               (SELECT count(*) FROM submission_files f WHERE f.submission_id=s.submission_id) AS documents,
               (SELECT count(*) FROM submission_files f WHERE f.submission_id=s.submission_id AND f.status='PARSED') AS parsed,
               COALESCE((SELECT d.status FROM submission_ai_deliveries d WHERE d.submission_id=s.submission_id),'NOT_READY') AS delivery,
               (SELECT count(*) FROM playwright_crawl_jobs c WHERE c.submission_id=s.submission_id AND c.purpose='CRAWL') AS crawls,
               (SELECT count(*) FROM functional_menu_jobs m WHERE m.submission_id=s.submission_id) AS menus,
               (SELECT count(*) FROM menu_tc_sets t WHERE t.submission_id=s.submission_id) AS tc_sets,
               (SELECT count(*) FROM test_execution_runs r WHERE r.submission_id=s.submission_id) AS runs
        FROM submissions s LEFT JOIN submission_workspaces w ON w.submission_id=s.submission_id
        WHERE s.member_id=?
        """;
    private List<Workspace> query(String tail,Object...args){
        return jdbc.query(SELECT+tail,(rs,n)->new Workspace(rs.getObject("submission_id",UUID.class),rs.getLong("product_id"),
            rs.getString("name"),rs.getString("last_page"),rs.getTimestamp("uploaded_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),
            rs.getInt("documents"),rs.getInt("parsed"),rs.getString("delivery"),rs.getInt("crawls"),rs.getInt("menus"),rs.getInt("tc_sets"),rs.getInt("runs")),args);
    }
    public List<Workspace> list(long member,int offset,int limit){
        if(member<1||offset<0||limit<1||limit>51)throw new CommonException(INVALID_INPUT_VALUE);
        return query(" ORDER BY s.uploaded_at DESC,s.submission_id LIMIT ? OFFSET ?",member,limit,offset);
    }
    public Workspace get(UUID id,long member){
        return query(" AND s.submission_id=?",member,id).stream().findFirst().orElseThrow(()->new CommonException(RESOURCE_NOT_FOUND));
    }
    public static String validName(String name){
        if(name==null||name.strip().isEmpty()||name.strip().length()>120||name.codePoints().anyMatch(Character::isISOControl))
            throw new CommonException(INVALID_INPUT_VALUE);
        return name.strip();
    }
    @Transactional
    public Workspace update(UUID id,long member,String name,String lastPage){
        if(name==null&&lastPage==null)throw new CommonException(INVALID_INPUT_VALUE);
        if(name!=null)name=validName(name);
        if(lastPage!=null&&!PAGES.contains(lastPage))throw new CommonException(INVALID_INPUT_VALUE);
        // Serialize metadata updates and verify ownership before inserting or changing anything.
        var owners=jdbc.query("SELECT member_id FROM submissions WHERE submission_id=? FOR UPDATE",(rs,n)->rs.getLong(1),id);
        if(owners.isEmpty()||owners.getFirst()!=member)throw new CommonException(RESOURCE_NOT_FOUND);
        jdbc.update("""
            INSERT INTO submission_workspaces(submission_id,name,last_page) VALUES(?,COALESCE(?,'시험 작업 ' || left(?::text,8)),COALESCE(?,'documents'))
            ON CONFLICT(submission_id) DO UPDATE SET name=COALESCE(?,submission_workspaces.name),
                last_page=COALESCE(?,submission_workspaces.last_page),updated_at=now()
            """,id,name,id,lastPage,name,lastPage);
        return get(id,member);
    }
}
