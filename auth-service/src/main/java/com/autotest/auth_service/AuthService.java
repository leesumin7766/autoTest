package com.autotest.auth_service;

import com.autotest.common_lib.auth.AccessTokens;
import com.autotest.common_lib.exception.CommonException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public record User(long id,long memberId,String username,String displayName,String role,boolean disabled) {
        public List<String> roles() { return List.of(role); }
    }
    record Credentials(User user,String hash,int failed,Instant lockedUntil) {}
    public record Session(String accessToken,String refreshToken,long expiresIn,User user) {}
    private final JdbcTemplate db;
    private final AccessTokens tokens;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    private final SecureRandom random=new SecureRandom();
    private final String dummy=passwords.encode(UUID.randomUUID().toString());
    public AuthService(JdbcTemplate db,@Value("${AUTH_JWT_SECRET}") String secret) { this.db=db;tokens=new AccessTokens(secret); }

    @Transactional
    public void bootstrap(String username,String password,String displayName) {
        allocationLock();
        if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from auth.users)",Boolean.class)))return;
        validateUser(username,password,displayName,"ADMIN");
        db.update("insert into auth.users(member_id,username,password_hash,display_name,role) values (1,?,?,?,'ADMIN')",username,passwords.encode(password),displayName);
    }
    @Transactional(noRollbackFor=CommonException.class)
    public Session login(String username,String password,String client) {
        validateClient(client);
        if(username==null||password==null||username.length()>64||password.getBytes(StandardCharsets.UTF_8).length>72)throw error(AuthError.AUTH_REQUIRED);
        var matches=db.query("select * from auth.users where username=? for update",this::credentials,username);
        if(matches.isEmpty()) { passwords.matches(password,dummy);throw error(AuthError.AUTH_REQUIRED); }
        var row=matches.getFirst();Instant now=Instant.now();
        if(row.user.disabled()||(row.lockedUntil!=null&&row.lockedUntil.isAfter(now)))throw error(AuthError.AUTH_REQUIRED);
        if(!passwords.matches(password,row.hash)) {
            int count=row.lockedUntil!=null?1:row.failed+1;
            db.update("update auth.users set failed_count=?,locked_until=? where id=?",count,count>=5?Timestamp.from(now.plusSeconds(900)):null,row.user.id());
            throw error(AuthError.AUTH_REQUIRED);
        }
        db.update("update auth.users set failed_count=0,locked_until=null where id=?",row.user.id());
        return session(row.user,client,null);
    }
    @Transactional(noRollbackFor=CommonException.class)
    public Session refresh(String raw,String client) {
        validateClient(client);String hash=hash(raw);
        var owners=db.queryForList("select user_id from auth.refresh_tokens where token_hash=?",Long.class,hash);
        if(owners.isEmpty())throw error(AuthError.AUTH_REQUIRED);
        User user=lockedUser(owners.getFirst());
        var rows=db.queryForList("select id,client,expires_at,revoked_at from auth.refresh_tokens where token_hash=? for update",hash);
        var row=rows.getFirst();
        if(user.disabled()||row.get("revoked_at")!=null||!client.equals(row.get("client"))||!((Timestamp)row.get("expires_at")).toInstant().isAfter(Instant.now())) {
            revoke(user.id());throw error(AuthError.AUTH_REQUIRED);
        }
        return session(user,client,(UUID)row.get("id"));
    }
    private Session session(User user,String client,UUID previous) {
        byte[] bytes=new byte[32];random.nextBytes(bytes);String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id=UUID.randomUUID();Instant now=Instant.now();
        db.update("insert into auth.refresh_tokens(id,user_id,token_hash,client,expires_at) values (?,?,?,?,?)",id,user.id(),hash(raw),client,Timestamp.from(now.plusSeconds(30L*86400)));
        if(previous!=null)db.update("update auth.refresh_tokens set revoked_at=now(),replaced_by=? where id=?",id,previous);
        return new Session(tokens.issue(user.id(),user.memberId(),user.roles(),now),raw,AccessTokens.LIFETIME_SECONDS,user);
    }
    public User authenticate(String token) {
        long id;
        try { id=Long.parseLong(tokens.verify(token).getSubject()); }
        catch(RuntimeException invalid) { throw error(AuthError.AUTH_REQUIRED); }
        var users=db.query("select * from auth.users where id=?",this::credentials,id);
        if(users.isEmpty()||users.getFirst().user.disabled())throw error(AuthError.AUTH_REQUIRED);
        return users.getFirst().user;
    }
    @Transactional
    public void logout(User user) { lockedUser(user.id());revoke(user.id()); }
    @Transactional
    public User create(User actor,String username,String password,String displayName,String role) {
        allocationLock();admin(actor);validateUser(username,password,displayName,role);
        if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from auth.users where username=?)",Boolean.class,username)))throw error(AuthError.AUTH_USER_EXISTS);
        long member=db.queryForObject("select coalesce(max(member_id),1)+1 from auth.users",Long.class);
        // Do not assign an existing unauthenticated workspace/agent owner to a newly created account.
        for(String table:List.of("submissions","crawl_agents","playwright_crawl_setups","playwright_crawl_jobs","product_description_jobs","tc_generation_jobs","menu_tree_approvals","functional_menu_jobs","menu_tc_sets","test_execution_runs","manual_review_jobs")) {
            if(db.queryForObject("select to_regclass(?)::text",String.class,"public."+table)!=null)
                member=Math.max(member,db.queryForObject("select coalesce(max(member_id),1)+1 from public."+table,Long.class));
        }
        long id=db.queryForObject("insert into auth.users(member_id,username,password_hash,display_name,role) values (?,?,?,?,?) returning id",Long.class,member,username,passwords.encode(password),displayName,role);
        return new User(id,member,username,displayName,role,false);
    }
    @Transactional
    public void resetPassword(User actor,long id,String password) {
        allocationLock();admin(actor);validatePassword(password);lockedUser(id);
        db.update("update auth.users set password_hash=?,failed_count=0,locked_until=null where id=?",passwords.encode(password),id);revoke(id);
    }
    @Transactional
    public void disable(User actor,long id) {
        allocationLock();admin(actor);if(actor.id()==id)throw error(AuthError.AUTH_INVALID_INPUT);
        lockedUser(id);db.update("update auth.users set disabled=true where id=?",id);revoke(id);
    }
    private void admin(User actor) { var current=lockedUser(actor.id());if(current.disabled()||!current.role().equals("ADMIN"))throw error(AuthError.AUTH_FORBIDDEN); }
    private void revoke(long id) { db.update("update auth.refresh_tokens set revoked_at=coalesce(revoked_at,now()) where user_id=?",id); }
    private void allocationLock() { db.execute("select pg_advisory_xact_lock(47011351)"); }
    private User lockedUser(long id) {
        var rows=db.query("select * from auth.users where id=? for update",this::credentials,id);
        if(rows.isEmpty())throw error(AuthError.AUTH_USER_NOT_FOUND);return rows.getFirst().user;
    }
    private Credentials credentials(ResultSet r,int index)throws SQLException {
        var until=r.getTimestamp("locked_until");
        return new Credentials(new User(r.getLong("id"),r.getLong("member_id"),r.getString("username"),r.getString("display_name"),r.getString("role"),r.getBoolean("disabled")),r.getString("password_hash"),r.getInt("failed_count"),until==null?null:until.toInstant());
    }
    private void validateUser(String username,String password,String displayName,String role) {
        if(username==null||!username.matches("[A-Za-z0-9._-]{3,64}")||displayName==null||displayName.isBlank()||displayName.length()>100||!List.of("ADMIN","USER").contains(role==null?"":role))throw error(AuthError.AUTH_INVALID_INPUT);
        validatePassword(password);
    }
    private void validatePassword(String password) { if(password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)throw error(AuthError.AUTH_INVALID_INPUT); }
    private void validateClient(String client) { if(!"web".equals(client)&&!"hub".equals(client))throw error(AuthError.AUTH_INVALID_INPUT); }
    private String hash(String token) {
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw error(AuthError.AUTH_REQUIRED);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private CommonException error(AuthError code) { return new CommonException(code); }
}
