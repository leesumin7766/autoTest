package com.autotest.auth_service;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="AUTH_TEST_DB_URL",matches="jdbc:postgresql://[^/]+/autotest_test")
class AuthIntegrationTest {
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        String url=System.getenv("AUTH_TEST_DB_URL");
        if(url==null||!url.matches("jdbc:postgresql://[^/]+/autotest_test"))throw new IllegalStateException("Isolated autotest_test database required");
        r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"test");r.add("spring.datasource.password",()->"isolated-test-only");
        r.add("AUTH_JWT_SECRET",()->"test-only-secret-at-least-thirty-two-bytes-long");
        r.add("AUTH_BOOTSTRAP_ADMIN_USERNAME",()->"admin-test");r.add("AUTH_BOOTSTRAP_ADMIN_PASSWORD",()->"isolated-admin-test-password");
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired AuthService service;
    @org.junit.jupiter.api.BeforeEach void cleanTestAccounts() {
        db.update("delete from auth.refresh_tokens");
        db.update("delete from auth.users where member_id<>1");
    }
    ResponseEntity<Map> post(String path,Object body,String access) {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        if(access!=null)headers.setBearerAuth(access);
        return http.exchange("/api/auth/"+path,HttpMethod.POST,new HttpEntity<>(body,headers),Map.class);
    }
    Map login(String name,String password) {
        var response=post("login",Map.of("username",name,"password",password,"client","hub"),null);
        assertEquals(200,response.getStatusCode().value());return response.getBody();
    }
    @Test void lifecycleAndIsolation() {
        assertNotNull(db.queryForObject("select to_regclass('auth.auth_schema_history')::text",String.class));
        assertNull(db.queryForObject("select to_regclass('public.users')::text",String.class));
        var admin=login("admin-test","isolated-admin-test-password");String access=(String)admin.get("accessToken");
        var headers=new HttpHeaders();headers.setBearerAuth(access);
        var me=http.exchange("/api/auth/me",HttpMethod.GET,new HttpEntity<>(headers),Map.class);
        assertEquals(1,((Number)me.getBody().get("memberId")).intValue());
        service.bootstrap("ignored-admin","ignored-admin-password","Ignored");
        assertEquals(1,db.queryForObject("select count(*) from auth.users",Integer.class));
        db.execute("create table if not exists public.crawl_agents(member_id bigint)");
        db.update("delete from public.crawl_agents");db.update("insert into public.crawl_agents values (42)");
        var created=post("users",Map.of("username","alice-test","password","alice-test-password","displayName","Alice","role","USER"),access);
        assertEquals(201,created.getStatusCode().value());long id=((Number)created.getBody().get("id")).longValue();
        assertTrue(((Number)created.getBody().get("memberId")).longValue()>42);
        var alice=login("alice-test","alice-test-password");
        assertEquals(403,post("users",Map.of("username","forbidden","password","forbidden-password","displayName","X","role","ADMIN"),(String)alice.get("accessToken")).getStatusCode().value());
        String refresh=(String)alice.get("refreshToken");
        assertFalse(db.queryForObject("select exists(select 1 from auth.refresh_tokens where token_hash=?)",Boolean.class,refresh));
        var rotated=post("refresh",Map.of("refreshToken",refresh,"client","hub"),null);assertEquals(200,rotated.getStatusCode().value());
        assertNotEquals(refresh,rotated.getBody().get("refreshToken"));
        assertEquals(401,post("refresh",Map.of("refreshToken",refresh,"client","hub"),null).getStatusCode().value());
        assertEquals(401,post("refresh",Map.of("refreshToken",rotated.getBody().get("refreshToken"),"client","hub"),null).getStatusCode().value());
        var fresh=login("alice-test","alice-test-password");
        assertEquals(204,post("users/"+id+"/password",Map.of("password","changed-alice-password"),access).getStatusCode().value());
        assertEquals(401,post("refresh",Map.of("refreshToken",fresh.get("refreshToken"),"client","hub"),null).getStatusCode().value());
        assertEquals(204,post("users/"+id+"/disable",Map.of(),access).getStatusCode().value());
        assertEquals(401,post("login",Map.of("username","alice-test","password","changed-alice-password","client","hub"),null).getStatusCode().value());
    }
    @Test void concurrentRefreshHasOneWinnerAndLogoutRevokesSessions() throws Exception {
        var admin=login("admin-test","isolated-admin-test-password");String raw=(String)admin.get("refreshToken");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->post("refresh",Map.of("refreshToken",raw,"client","hub"),null).getStatusCode().value());
            var second=pool.submit(()->post("refresh",Map.of("refreshToken",raw,"client","hub"),null).getStatusCode().value());
            var statuses=new java.util.ArrayList<>(java.util.List.of(first.get(),second.get()));java.util.Collections.sort(statuses);
            assertEquals(java.util.List.of(200,401),statuses);
        }
        var fresh=login("admin-test","isolated-admin-test-password");
        assertEquals(204,post("logout",Map.of(),(String)fresh.get("accessToken")).getStatusCode().value());
        assertEquals(401,post("refresh",Map.of("refreshToken",fresh.get("refreshToken"),"client","hub"),null).getStatusCode().value());
        var expired=login("admin-test","isolated-admin-test-password");
        db.update("update auth.refresh_tokens set expires_at=now()-interval '1 second'");
        assertEquals(401,post("refresh",Map.of("refreshToken",expired.get("refreshToken"),"client","hub"),null).getStatusCode().value());
    }
    @Test void failedLoginsPersistLockAndWebCookiesAreHttpOnly() {
        var admin=login("admin-test","isolated-admin-test-password");String access=(String)admin.get("accessToken");
        post("users",Map.of("username","locked-test","password","locked-test-password","displayName","Locked","role","USER"),access);
        for(int i=0;i<5;i++)assertEquals(401,post("login",Map.of("username","locked-test","password","wrong","client","hub"),null).getStatusCode().value());
        assertEquals(401,post("login",Map.of("username","locked-test","password","locked-test-password","client","hub"),null).getStatusCode().value());
        assertEquals(5,db.queryForObject("select failed_count from auth.users where username='locked-test'",Integer.class));
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-AutoTest-Web","1");
        var response=http.exchange("/api/auth/login",HttpMethod.POST,new HttpEntity<>(Map.of("username","admin-test","password","isolated-admin-test-password","client","web"),headers),Map.class);
        assertEquals(200,response.getStatusCode().value());assertFalse(response.getBody().containsKey("accessToken"));
        var cookies=response.getHeaders().get(HttpHeaders.SET_COOKIE);assertEquals(2,cookies.size());
        assertTrue(cookies.stream().allMatch(c->c.contains("HttpOnly")&&c.contains("Secure")&&c.contains("SameSite=Strict")));
        assertEquals(403,post("login",Map.of("username","admin-test","password","isolated-admin-test-password","client","web"),null).getStatusCode().value());
    }
}
