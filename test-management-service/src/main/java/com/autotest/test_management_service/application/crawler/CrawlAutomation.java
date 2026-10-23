package com.autotest.test_management_service.application.crawler;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;

/** Structured configuration for the future browser worker. Contains no password. */
public record CrawlAutomation(
        String productVersion, String loginUrl, String username,
        String usernameSelector, String passwordSelector, String submitSelector,
        String successUrl, String successSelector, List<String> allowedOrigins,
        List<String> includePaths, List<String> excludePaths,
        int maxPages, int maxDepth, int maxDurationSeconds, int viewportWidth, int viewportHeight,
        Integer pageLoadTimeoutSeconds, Boolean ignoreHTTPSErrors) {

    public static CrawlAutomation defaults() {
        return new CrawlAutomation("", "", "", "", "", "", "", "", List.of(), List.of(),
                List.of("/logout*", "/signout*"), 500, 10, 3000, 1440, 900, 60, false);
    }

    public CrawlAutomation normalized() {
        return new CrawlAutomation(text(productVersion), text(loginUrl), text(username), text(usernameSelector),
                text(passwordSelector), text(submitSelector), text(successUrl), text(successSelector),
                list(allowedOrigins), list(includePaths), list(excludePaths),
                maxPages, maxDepth, maxDurationSeconds, viewportWidth, viewportHeight,
                pageLoadTimeoutSeconds == null ? 60 : pageLoadTimeoutSeconds, Boolean.TRUE.equals(ignoreHTTPSErrors));
    }

    public void validate(String targetUrl, String loginMethod) {
        if (pageLoadTimeoutSeconds == null || pageLoadTimeoutSeconds < 1 || pageLoadTimeoutSeconds > 60)
            invalid("페이지 로딩 최대 대기 시간은 1~60초로 입력해 주세요.");
        if (productVersion.length() > 160 || username.length() > 320
                || usernameSelector.length() > 500 || passwordSelector.length() > 500
                || submitSelector.length() > 500 || successSelector.length() > 500)
            invalid("로그인 설정 또는 제품 버전이 허용 길이를 초과했습니다.");
        if (maxPages < 1 || maxPages > 500 || maxDepth < 1 || maxDepth > 10
                || maxDurationSeconds < 30 || maxDurationSeconds > 3600
                || viewportWidth < 320 || viewportWidth > 3840 || viewportHeight < 320 || viewportHeight > 2160)
            invalid("화면 수 1~500, 깊이 1~10, 시간 30~3600초, 화면 크기 범위를 확인해 주세요.");
        if (allowedOrigins.size() > 20 || includePaths.size() > 50 || excludePaths.size() > 50)
            invalid("허용 사이트는 20개, 포함·제외 경로는 각각 50개까지 입력할 수 있습니다.");
        Set<String> origins = new java.util.HashSet<>();
        origins.add(origin(targetUrl));
        for (String allowed : allowedOrigins) {
            URI uri = webUrl(allowed);
            if (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))
                invalid("허용 사이트는 경로 없이 주소와 포트만 입력해 주세요.");
            origins.add(origin(allowed));
        }
        for (String url : List.of(loginUrl, successUrl)) {
            if (!url.isEmpty() && !origins.contains(origin(url)))
                invalid("로그인 및 로그인 성공 URL의 사이트를 허용 사이트에 추가해 주세요.");
        }
        for (String path : java.util.stream.Stream.concat(includePaths.stream(), excludePaths.stream()).toList()) {
            if (!path.startsWith("/") || path.startsWith("//") || path.length() > 500
                    || path.contains("?") || path.contains("#") || path.contains("\n") || path.contains("\r"))
                invalid("수집 경로는 /로 시작하는 경로 또는 패턴으로 입력해 주세요. 예: /admin/*");
        }
        if (Set.of("FORM_AUTO", "BASIC_AUTH").contains(loginMethod) && username.isBlank())
            invalid("자동 로그인에 사용할 아이디를 입력해 주세요.");
    }

    static URI webUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (value.length() > 2048 || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0)
                throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new ProductDescriptionException(HttpStatus.BAD_REQUEST, "PLAYWRIGHT_URL_INVALID",
                    "http/https URL을 입력하세요. URL에 계정정보, query parameter, fragment를 포함하지 마세요.");
        }
    }

    static String origin(String value) {
        URI uri = webUrl(value);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort() < 0 ? ("https".equals(scheme) ? 443 : 80) : uri.getPort();
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static List<String> list(List<String> values) {
        return values == null ? List.of() : values.stream().map(CrawlAutomation::text).filter(v -> !v.isEmpty()).distinct().toList();
    }
    private static void invalid(String message) {
        throw new ProductDescriptionException(HttpStatus.BAD_REQUEST, "PLAYWRIGHT_AUTOMATION_INVALID", message);
    }
}
