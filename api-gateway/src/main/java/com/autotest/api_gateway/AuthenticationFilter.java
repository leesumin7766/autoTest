package com.autotest.api_gateway;

import com.autotest.common_lib.auth.AccessTokens;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(-100)
public class AuthenticationFilter implements WebFilter {
    @Configuration
    static class Config {
        @Bean AccessTokens accessTokens(@Value("${AUTH_JWT_SECRET}") String secret) { return new AccessTokens(secret); }
    }
    private final AccessTokens tokens;
    private final boolean trustProxy;
    private final HashMap<String,Window> loginWindows=new HashMap<>();
    private record Window(long minute,int count) {}
    public AuthenticationFilter(AccessTokens tokens) { this(tokens,false); }
    @org.springframework.beans.factory.annotation.Autowired
    public AuthenticationFilter(AccessTokens tokens,@Value("${AUTH_TRUST_PROXY:false}") boolean trustProxy) { this.tokens=tokens;this.trustProxy=trustProxy; }
    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain) {
        var request=exchange.getRequest();
        String path=request.getURI().getRawPath();
        var sanitized=request.mutate().headers(h->{h.remove("X-Member-Id");h.remove("X-Internal-Token");h.remove("X-Auth-Client-IP");}).build();
        exchange=exchange.mutate().request(sanitized).build();
        if(!path.startsWith("/api/"))return chain.filter(exchange);
        boolean post=request.getMethod()==HttpMethod.POST;
        boolean login=post&&(path.equals("/api/auth/login")||path.equals("/api/auth/refresh"));
        String ip=request.getRemoteAddress()==null?"unknown":request.getRemoteAddress().getAddress().getHostAddress();
        String proxyIp=request.getHeaders().getFirst("X-Auth-Client-IP");
        if(trustProxy&&proxyIp!=null&&proxyIp.matches("[0-9a-fA-F:.]{1,64}"))ip=proxyIp;
        if(login&&!allowLogin(ip))
            return reject(exchange,429,"AUTH_RATE_LIMITED");
        boolean agent=post&&path.matches("/api/crawl-agents/[^/]+/channel/[^/]+");
        if(login||agent||(request.getMethod()==HttpMethod.GET&&path.equals("/api/v1/hub/health")))return chain.filter(exchange);
        String authorization=request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        var cookie=request.getCookies().getFirst("autotest_access");
        boolean bearer=authorization!=null;
        String token=bearer&&authorization.startsWith("Bearer ")?authorization.substring(7):!bearer&&cookie!=null?cookie.getValue():null;
        String member;
        try { member=tokens.verify(token==null?"":token).getClaimAsString("memberId"); }
        catch(RuntimeException invalid) { return reject(exchange,401,"AUTH_REQUIRED"); }
        boolean unsafe=request.getMethod()!=HttpMethod.GET&&request.getMethod()!=HttpMethod.HEAD;
        if(!bearer&&unsafe&&(!"1".equals(request.getHeaders().getFirst("X-AutoTest-Web"))||"null".equals(request.getHeaders().getOrigin())||"cross-site".equals(request.getHeaders().getFirst("Sec-Fetch-Site"))))
            return reject(exchange,403,"AUTH_CSRF_REJECTED");
        return chain.filter(exchange.mutate().request(sanitized.mutate().headers(h->h.set("X-Member-Id",member)).build()).build());
    }
    private synchronized boolean allowLogin(String ip) {
        long minute=Instant.now().getEpochSecond()/60;
        loginWindows.entrySet().removeIf(e->e.getValue().minute()!=minute);
        var window=loginWindows.get(ip);
        if(window==null&&loginWindows.size()>=10000)return false;
        int count=window==null?0:window.count();
        if(count>=60)return false;
        loginWindows.put(ip,new Window(minute,count+1));return true;
    }
    private Mono<Void> reject(ServerWebExchange exchange,int status,String code) {
        var response=exchange.getResponse();response.setStatusCode(HttpStatusCode.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);response.getHeaders().setCacheControl("no-store");
        if(status==429)response.getHeaders().set("Retry-After","60");
        String trace=UUID.randomUUID().toString();response.getHeaders().set("X-Trace-Id",trace);
        String body="{\"code\":\""+code+"\",\"message\":\"Authentication required or request rejected\",\"traceId\":\""+trace+"\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
