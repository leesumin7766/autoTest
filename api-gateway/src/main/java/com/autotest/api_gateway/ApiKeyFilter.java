package com.autotest.api_gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 외부 프로그램(업무 허브 등)용 API 키 인증.
 *
 * <ul>
 *   <li>키는 서버에 SHA-256 해시로만 둔다(AUTOTEST_API_KEYS). 원문은 발급할 때 한 번만 보인다.</li>
 *   <li>Authorization 헤더가 있으면 반드시 유효한 키여야 한다. 유효하면 X-Member-Id를 키에 묶인 회원으로 덮어쓴다.</li>
 *   <li>/api/v1/hub/** 는 키가 필수다.</li>
 *   <li>Authorization 없는 요청(사내망 웹 화면)은 기존과 같이 통과한다.</li>
 *   <li>X-Internal-Token·X-Api-Client 는 외부 입력을 항상 버린다.</li>
 * </ul>
 */
@Component
@Order(-100)
public class ApiKeyFilter implements WebFilter {
    static final String CLIENT_HEADER = "X-Api-Client";
    static final String HUB_PREFIX = "/api/v1/hub/";
    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private static final Pattern KEY = Pattern.compile("atk_[A-Za-z0-9_-]{43}");
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    private static final Pattern MEMBER = Pattern.compile("[1-9][0-9]{0,17}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    record ApiClient(String name, String memberId, byte[] hash) {}

    private final List<ApiClient> clients;

    public ApiKeyFilter(@Value("${AUTOTEST_API_KEYS:}") String config) {
        this.clients = parse(config);
        log.info("API key clients configured: {}", clients.stream().map(ApiClient::name).toList());
    }

    /** "name:memberId:sha256hex" 를 쉼표로 구분. 형식이 틀리면 기동을 멈춘다(조용히 무시하지 않음). */
    static List<ApiClient> parse(String config) {
        List<ApiClient> result = new ArrayList<>();
        if (config == null || config.isBlank()) return result;
        for (String raw : config.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":");
            if (parts.length != 3 || !NAME.matcher(parts[0]).matches() || !MEMBER.matcher(parts[1]).matches()
                    || !HASH.matcher(parts[2]).matches())
                throw new IllegalStateException("AUTOTEST_API_KEYS entry must be name:memberId:sha256hex (entry #"
                        + (result.size() + 1) + ")");
            if (result.stream().anyMatch(c -> c.name().equals(parts[0])))
                throw new IllegalStateException("AUTOTEST_API_KEYS has duplicate name: " + parts[0]);
            result.add(new ApiClient(parts[0], parts[1], HexFormat.of().parseHex(parts[2])));
        }
        return List.copyOf(result);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getURI().getRawPath();
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        var cleaned = request.mutate().headers(h -> { h.remove("X-Internal-Token"); h.remove(CLIENT_HEADER); });

        if (authorization == null) {
            if (path.startsWith(HUB_PREFIX)) return reject(exchange, "API_KEY_REQUIRED");
            return chain.filter(exchange.mutate().request(cleaned.build()).build());
        }
        ApiClient client = authorization.startsWith("Bearer ") ? match(authorization.substring(7).trim()) : null;
        if (client == null) {
            var remote = request.getRemoteAddress();
            log.warn("API key rejected path={} remote={}", path, remote == null ? "-" : remote.getAddress().getHostAddress());
            return reject(exchange, "API_KEY_INVALID");
        }
        var authenticated = cleaned.headers(h -> {
            h.remove(HttpHeaders.AUTHORIZATION);
            h.set("X-Member-Id", client.memberId());
            h.set(CLIENT_HEADER, client.name());
        }).build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private ApiClient match(String key) {
        if (!KEY.matcher(key).matches()) return null;
        byte[] hash = sha256(key);
        ApiClient found = null;
        for (ApiClient client : clients)  // 끝까지 비교해 일치 위치가 시간으로 드러나지 않게 한다.
            if (MessageDigest.isEqual(client.hash(), hash) && found == null) found = client;
        return found;
    }

    static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Mono<Void> reject(ServerWebExchange exchange, String code) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        String trace = UUID.randomUUID().toString();
        response.getHeaders().set("X-Trace-Id", trace);
        String message = code.equals("API_KEY_REQUIRED") ? "API 키가 필요합니다." : "API 키가 올바르지 않거나 폐기되었습니다.";
        String body = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"traceId\":\"" + trace + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
