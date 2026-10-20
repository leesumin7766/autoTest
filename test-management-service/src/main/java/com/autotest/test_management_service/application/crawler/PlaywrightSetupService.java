package com.autotest.test_management_service.application.crawler;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionJobRepository;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PlaywrightSetupService {
    private static final Set<String> LOGIN_METHODS = Set.of("NONE", "MANUAL", "SSO", "OTHER");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|cookie|secret|authorization|api[-_ ]?key)\\b\\s*[:=]\\s*\\S+");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SubmissionPersistenceService submissions;
    private final SubmissionRepository repository;
    private final ProductDescriptionJobRepository verdicts;
    private final TransactionTemplate transactions;

    public PlaywrightSetupService(JdbcTemplate jdbc, ObjectMapper mapper,
            SubmissionPersistenceService submissions, SubmissionRepository repository,
            ProductDescriptionJobRepository verdicts, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.submissions = submissions;
        this.repository = repository;
        this.verdicts = verdicts;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    private ProductDescriptionException error(HttpStatus status, String code, String message) {
        return new ProductDescriptionException(status, code, message);
    }

    private record Verified(String digest) { }

    private Verified verifyReadySubmission(UUID id, long memberId) {
        var submission = submissions.findById(SubmissionId.of(id))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "문서 세트를 찾을 수 없습니다."));
        if (submission.memberId().value() != memberId)
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 문서 세트에 접근할 수 없습니다.");
        var docs = submissions.deliveryDocuments(submissions.findDocumentsBySubmissionId(SubmissionId.of(id)));
        var roles = docs.stream().map(d -> d.role()).collect(java.util.stream.Collectors.toSet());
        if (docs.size() < 2 || docs.size() > 3 || roles.size() != docs.size()
                || !roles.containsAll(Set.of(
                        com.autotest.test_management_service.domain.submission.SubmissionType.FUNCTION_LIST,
                        com.autotest.test_management_service.domain.submission.SubmissionType.MANUAL))
                || docs.stream().anyMatch(d -> d.status() != SubmissionStatus.PARSED
                        || d.extractedText() == null || d.extractedText().isBlank()))
            throw error(HttpStatus.CONFLICT, "DOCUMENTS_NOT_READY", "기능리스트와 매뉴얼이 정상 파싱되어야 합니다.");
        var verdict = verdicts.findDeliveryVerdict(id).orElse(null);
        if (verdict == null || !"DELIVERED".equals(verdict.status()))
            throw error(HttpStatus.CONFLICT, "PREFLIGHT_NOT_VERIFIED", "Jev 사전 검증 완료 후 준비 설정을 저장할 수 있습니다.");
        try {
            var preflight = mapper.readTree(verdict.preflightJson() == null ? "{}" : verdict.preflightJson());
            if (!Set.of("READY", "READY_WITH_WARNINGS").contains(preflight.path("decision").asText())
                    || (preflight.path("generationGate").has("allowed")
                            && !preflight.path("generationGate").path("allowed").asBoolean()))
                throw error(HttpStatus.CONFLICT, "PREFLIGHT_BLOCKED", "Jev 사전 검증이 차단되어 Playwright 준비를 진행할 수 없습니다.");
            String currentDigest = DocumentDigest.of(docs);
            if (!currentDigest.equals(verdict.documentDigest()))
                throw error(HttpStatus.CONFLICT, "DOCUMENT_VERSION_MISMATCH", "문서가 변경되어 Jev 사전 검증을 다시 해야 합니다.");
            return new Verified(currentDigest);
        } catch (ProductDescriptionException known) {
            throw known;
        } catch (Exception failure) {
            throw error(HttpStatus.CONFLICT, "PREFLIGHT_NOT_VERIFIED", "Jev 검증 기록을 확인할 수 없습니다.");
        }
    }

    public SetupView get(UUID id, long memberId) {
        String currentDigest = verifyReadySubmission(id, memberId).digest();
        var rows = jdbc.query("""
                SELECT target_url, login_method, login_instructions, test_account_label,
                       crawl_scope_notes, test_data_notes, test_environment_confirmed, document_digest
                FROM playwright_crawl_setups WHERE submission_id=? AND member_id=?
                """, (rs, row) -> new SetupView(rs.getString("target_url"), rs.getString("login_method"),
                        rs.getString("login_instructions"), rs.getString("test_account_label"),
                        rs.getString("crawl_scope_notes"), rs.getString("test_data_notes"),
                        rs.getBoolean("test_environment_confirmed"),
                        !currentDigest.equals(rs.getString("document_digest").trim()),
                        "READY_TO_CRAWL"), id, memberId);
        return rows.stream().findFirst().orElse(null);
    }

    public SetupView save(UUID id, long memberId, SetupRequest request) {
        validate(request);
        Verified initial = verifyReadySubmission(id, memberId);
        transactions.execute(tx -> {
            repository.findByIdForUpdate(SubmissionId.of(id))
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "문서 세트를 찾을 수 없습니다."));
            Verified current = verifyReadySubmission(id, memberId);
            if (!initial.digest().equals(current.digest()))
                throw error(HttpStatus.CONFLICT, "DOCUMENT_VERSION_MISMATCH", "준비 설정을 저장하는 동안 문서가 변경되었습니다.");
            jdbc.update("""
                    INSERT INTO playwright_crawl_setups(
                        submission_id, member_id, target_url, login_method, login_instructions,
                        test_account_label, crawl_scope_notes, test_data_notes,
                        test_environment_confirmed, document_digest, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (submission_id) DO UPDATE SET
                        member_id=EXCLUDED.member_id, target_url=EXCLUDED.target_url,
                        login_method=EXCLUDED.login_method, login_instructions=EXCLUDED.login_instructions,
                        test_account_label=EXCLUDED.test_account_label, crawl_scope_notes=EXCLUDED.crawl_scope_notes,
                        test_data_notes=EXCLUDED.test_data_notes,
                        test_environment_confirmed=EXCLUDED.test_environment_confirmed,
                        document_digest=EXCLUDED.document_digest, updated_at=now()
                    """, id, memberId, request.targetUrl().trim(), request.loginMethod(),
                    clean(request.loginInstructions()), clean(request.testAccountLabel()),
                    clean(request.crawlScopeNotes()), clean(request.testDataNotes()),
                    request.testEnvironmentConfirmed(), current.digest());
            return null;
        });
        return get(id, memberId);
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }

    private void validate(SetupRequest request) {
        if (request == null || request.targetUrl() == null || request.loginMethod() == null)
            throw error(HttpStatus.BAD_REQUEST, "PLAYWRIGHT_SETUP_INVALID", "접속 URL과 로그인 방식을 입력해 주세요.");
        String url = request.targetUrl().trim();
        try {
            URI uri = URI.create(url);
            if (url.length() > 2048 || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("unsupported URL");
        } catch (IllegalArgumentException invalid) {
            throw error(HttpStatus.BAD_REQUEST, "PLAYWRIGHT_URL_INVALID",
                    "http 또는 https URL을 입력하세요. URL에 계정정보, query parameter, fragment를 포함하지 마세요.");
        }
        if (!LOGIN_METHODS.contains(request.loginMethod()))
            throw error(HttpStatus.BAD_REQUEST, "PLAYWRIGHT_LOGIN_METHOD_INVALID", "로그인 방식을 선택해 주세요.");
        if (length(request.loginInstructions()) > 2000 || length(request.testAccountLabel()) > 160
                || length(request.crawlScopeNotes()) > 4000 || length(request.testDataNotes()) > 4000)
            throw error(HttpStatus.BAD_REQUEST, "PLAYWRIGHT_SETUP_TOO_LARGE", "설정 메모가 허용 길이를 초과했습니다.");
        String notes = String.join(" ", clean(request.loginInstructions()), clean(request.testAccountLabel()),
                clean(request.crawlScopeNotes()), clean(request.testDataNotes()));
        if (SECRET_ASSIGNMENT.matcher(notes).find())
            throw error(HttpStatus.BAD_REQUEST, "PLAYWRIGHT_SECRET_INPUT_NOT_ALLOWED",
                    "메모에 비밀번호·쿠키·토큰처럼 보이는 값이 있습니다. 비밀정보는 저장하지 마세요.");
        if (!request.testEnvironmentConfirmed())
            throw error(HttpStatus.BAD_REQUEST, "TEST_ENVIRONMENT_CONFIRMATION_REQUIRED",
                    "접속 권한이 있는 테스트 환경임을 확인해 주세요.");
    }

    private int length(String value) { return value == null ? 0 : value.length(); }

    public record SetupRequest(String targetUrl, String loginMethod, String loginInstructions,
            String testAccountLabel, String crawlScopeNotes, String testDataNotes,
            boolean testEnvironmentConfirmed) { }
    public record SetupView(String targetUrl, String loginMethod, String loginInstructions,
            String testAccountLabel, String crawlScopeNotes, String testDataNotes,
            boolean testEnvironmentConfirmed, boolean stale, String status) { }
}
