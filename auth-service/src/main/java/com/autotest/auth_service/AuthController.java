package com.autotest.auth_service;

import com.autotest.common_lib.exception.CommonException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    record Login(String username,String password,String client) {}
    record Refresh(String refreshToken,String client) {}
    record Create(String username,String password,String displayName,String role) {}
    record Password(String password) {}
    private final AuthService service;
    private final boolean secure;
    public AuthController(AuthService service,@Value("${AUTH_COOKIE_SECURE:true}") boolean secure) { this.service=service;this.secure=secure; }
    @PostMapping(value="/login",consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> login(@RequestBody Login input,HttpServletRequest request) {
        if("web".equals(input.client()))web(request);
        return session(service.login(input.username(),input.password(),input.client()),input.client());
    }
    @PostMapping(value="/refresh",consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> refresh(@RequestBody(required=false) Refresh input,HttpServletRequest request) {
        String client=input==null||input.client()==null?"web":input.client();
        if("web".equals(client))web(request);
        String raw="web".equals(client)?cookie(request,"autotest_refresh"):input.refreshToken();
        return session(service.refresh(raw,client),client);
    }
    @GetMapping("/me") ResponseEntity<?> me(HttpServletRequest request) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile(user(request))); }
    @PostMapping("/logout") ResponseEntity<?> logout(HttpServletRequest request) {
        service.logout(user(request));var headers=new HttpHeaders();cookies(headers,"","",0,0);
        return ResponseEntity.noContent().headers(headers).build();
    }
    @PostMapping(value="/users",consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> create(@RequestBody Create input,HttpServletRequest request) {
        var created=service.create(user(request),input.username(),input.password(),input.displayName(),input.role());
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(profile(created));
    }
    @PostMapping(value="/users/{id}/password",consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> password(@PathVariable long id,@RequestBody Password input,HttpServletRequest request) { service.resetPassword(user(request),id,input.password());return ResponseEntity.noContent().build(); }
    @PostMapping("/users/{id}/disable")
    ResponseEntity<?> disable(@PathVariable long id,HttpServletRequest request) { service.disable(user(request),id);return ResponseEntity.noContent().build(); }
    private AuthService.User user(HttpServletRequest request) {
        String authorization=request.getHeader("Authorization");
        String token;
        if(authorization!=null)token=authorization.startsWith("Bearer ")?authorization.substring(7):"";
        else { token=cookie(request,"autotest_access");if(!"GET".equals(request.getMethod()))web(request); }
        return service.authenticate(token==null?"":token);
    }
    private void web(HttpServletRequest request) {
        if(!"1".equals(request.getHeader("X-AutoTest-Web"))||"null".equals(request.getHeader("Origin"))||"cross-site".equals(request.getHeader("Sec-Fetch-Site")))throw new CommonException(AuthError.AUTH_CSRF_REJECTED);
    }
    private String cookie(HttpServletRequest request,String name) {
        if(request.getCookies()!=null)for(var cookie:request.getCookies())if(cookie.getName().equals(name))return cookie.getValue();return null;
    }
    private ResponseEntity<?> session(AuthService.Session session,String client) {
        var headers=new HttpHeaders();headers.setCacheControl("no-store");
        if("web".equals(client)) { cookies(headers,session.accessToken(),session.refreshToken(),900,30L*86400);return ResponseEntity.ok().headers(headers).body(profile(session.user())); }
        return ResponseEntity.ok().headers(headers).body(Map.of("accessToken",session.accessToken(),"refreshToken",session.refreshToken(),"expiresIn",session.expiresIn()));
    }
    private Map<String,Object> profile(AuthService.User user) { return Map.of("id",user.id(),"memberId",user.memberId(),"username",user.username(),"displayName",user.displayName(),"roles",user.roles()); }
    private void cookies(HttpHeaders headers,String access,String refresh,long accessAge,long refreshAge) {
        headers.add(HttpHeaders.SET_COOKIE,ResponseCookie.from("autotest_access",access).httpOnly(true).secure(secure).sameSite("Strict").path("/api").maxAge(accessAge).build().toString());
        headers.add(HttpHeaders.SET_COOKIE,ResponseCookie.from("autotest_refresh",refresh).httpOnly(true).secure(secure).sameSite("Strict").path("/api/auth").maxAge(refreshAge).build().toString());
    }
}
