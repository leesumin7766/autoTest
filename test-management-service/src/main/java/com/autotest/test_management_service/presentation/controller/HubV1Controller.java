package com.autotest.test_management_service.presentation.controller;

import com.autotest.common_lib.exception.CommonErrorCode;
import com.autotest.common_lib.exception.CommonException;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.service.SubmissionWorkspaceService;
import com.autotest.test_management_service.application.service.SubmissionWorkspaceService.Workspace;
import com.autotest.test_management_service.application.testcase.ExecutionReviewService;
import com.autotest.test_management_service.application.testcase.TestExecutionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * 업무 허브 전용 조회 API v1. 게이트웨이 ApiKeyFilter가 키를 강제하고 X-Member-Id를 키의 회원으로 넣는다.
 * 계약: docs/hub-api-v1.openapi.yaml. v1에서는 필드를 삭제하거나 의미를 바꾸지 않고 추가만 한다.
 * 내부 snapshot 등 웹 화면용 구조는 그대로 노출하지 않고 이 클래스에서 고정 형태로 옮긴다.
 */
@RestController
@RequestMapping("/api/v1/hub")
public class HubV1Controller {
    static final String API_VERSION = "1";
    private static final Pattern VERSION = Pattern.compile("(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})(?:[-+].*)?");
    private final SubmissionWorkspaceService workspaces;
    private final TestExecutionService executions;
    private final ExecutionReviewService reviews;
    private final String minHubVersion;

    public HubV1Controller(SubmissionWorkspaceService workspaces, TestExecutionService executions, ExecutionReviewService reviews,
                           @Value("${HUB_MIN_VERSION:0.0.0}") String minHubVersion) {
        if (!VERSION.matcher(minHubVersion).matches()) throw new IllegalStateException("HUB_MIN_VERSION must be x.y.z");
        this.workspaces = workspaces; this.executions = executions; this.reviews = reviews; this.minHubVersion = minHubVersion;
    }

    public record Health(String status, String apiVersion, String minHubVersion) {}
    public record Counts(int documents, int parsedDocuments, int crawls, int menus, int tcSets, int runs) {}
    public record WorkspaceItem(UUID id, String name, long productId, String lastPage, String createdAt, String updatedAt,
                                String documentDelivery, Counts counts, String webPath) {}
    public record WorkspacePage(List<WorkspaceItem> workspaces, boolean hasMore) {}
    public record RunCounts(int total, int completed, int pass, int fail, int na, int unjudged, int pending, int error,
                            int notExecuted, int needsReview, int excluded) {}
    public record Run(UUID runId, String status, boolean active, String createdAt, String updatedAt, long elapsedMs,
                      boolean tcSetChanged, RunCounts counts, String webPath) {}
    public record RunItem(UUID runId, String status, String createdAt) {}
    public record Summary(WorkspaceItem workspace, Run latestRun) {}
    public record RunList(List<RunItem> runs) {}

    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }

    @GetMapping("/health")
    public ResponseEntity<Health> health() { return ok(new Health("UP", API_VERSION, minHubVersion)); }

    @GetMapping("/workspaces")
    public ResponseEntity<WorkspacePage> list(@RequestHeader("X-Member-Id") long member, @RequestHeader(value = "X-Hub-Version", required = false) String hub,
                                              @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit) {
        checkVersion(hub);
        if (offset < 0 || limit < 1 || limit > 50) throw new CommonException(CommonErrorCode.INVALID_INPUT_VALUE);
        var items = workspaces.list(member, offset, limit + 1);
        return ok(new WorkspacePage(items.stream().limit(limit).map(w -> item(w, member)).toList(), items.size() > limit));
    }

    @GetMapping("/workspaces/{id}/summary")
    public ResponseEntity<Summary> summary(@RequestHeader("X-Member-Id") long member, @RequestHeader(value = "X-Hub-Version", required = false) String hub,
                                           @PathVariable UUID id) {
        checkVersion(hub);
        var workspace = workspaces.get(id, member);
        JsonNode run = executions.status(id, member, null).path("run");
        return ok(new Summary(item(workspace, member), run.isMissingNode() || run.isNull() ? null : run(run, id, member)));
    }

    @GetMapping("/workspaces/{id}/runs")
    public ResponseEntity<RunList> runs(@RequestHeader("X-Member-Id") long member, @RequestHeader(value = "X-Hub-Version", required = false) String hub,
                                        @PathVariable UUID id) {
        checkVersion(hub);
        List<RunItem> runs = new ArrayList<>();
        for (JsonNode h : executions.status(id, member, null).path("history"))
            runs.add(new RunItem(UUID.fromString(h.path("runId").asText()), h.path("status").asText(), h.path("createdAt").asText()));
        return ok(new RunList(runs));
    }

    @GetMapping("/workspaces/{id}/runs/{run}")
    public ResponseEntity<Run> run(@RequestHeader("X-Member-Id") long member, @RequestHeader(value = "X-Hub-Version", required = false) String hub,
                                   @PathVariable UUID id, @PathVariable UUID run) {
        checkVersion(hub);
        return ok(run(executions.status(id, member, run).path("run"), id, member));
    }

    @GetMapping("/workspaces/{id}/runs/{run}/report")
    public ResponseEntity<String> report(@RequestHeader("X-Member-Id") long member, @RequestHeader(value = "X-Hub-Version", required = false) String hub,
                                         @PathVariable UUID id, @PathVariable UUID run) {
        checkVersion(hub);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8")).body(reviews.report(id, member, run));
    }

    void checkVersion(String hub) {
        var actual = hub == null ? null : VERSION.matcher(hub.trim());
        if (actual == null || !actual.matches())
            throw new ProductDescriptionException(HttpStatus.BAD_REQUEST, "HUB_VERSION_REQUIRED", "X-Hub-Version 헤더(x.y.z)가 필요합니다.");
        var min = VERSION.matcher(minHubVersion); min.matches();
        for (int i = 1; i <= 3; i++) {
            int a = Integer.parseInt(actual.group(i)), b = Integer.parseInt(min.group(i));
            if (a > b) return;
            if (a < b) throw new ProductDescriptionException(HttpStatus.UPGRADE_REQUIRED, "HUB_UPDATE_REQUIRED", "허브를 " + minHubVersion + " 이상으로 업데이트해야 합니다.");
        }
    }

    static String webPath(UUID workspace, long member, String page) {
        return "/?member=" + member + "&workspace=" + workspace + "&page=" + page;
    }

    private static WorkspaceItem item(Workspace w, long member) {
        return new WorkspaceItem(w.id(), w.name(), w.productId(), w.lastPage(), w.createdAt().toString(), w.updatedAt().toString(),
                w.deliveryStatus(), new Counts(w.documentCount(), w.parsedDocumentCount(), w.crawlCount(), w.menuCount(), w.tcSetCount(), w.runCount()),
                webPath(w.id(), member, w.lastPage()));
    }

    private static Run run(JsonNode r, UUID workspace, long member) {
        String status = r.path("status").asText();
        var counts = new RunCounts(r.path("snapshot").path("caseCount").asInt(), r.path("completedCount").asInt(), r.path("passCount").asInt(),
                r.path("failCount").asInt(), r.path("naCount").asInt(), r.path("unjudgedCount").asInt(), r.path("pendingCount").asInt(),
                r.path("errorCount").asInt(), r.path("notExecutedCount").asInt(), r.path("reviewCount").asInt(), r.path("excludedCount").asInt());
        return new Run(UUID.fromString(r.path("runId").asText()), status, Set.of("WAITING_EXECUTOR", "RUNNING").contains(status),
                r.path("createdAt").asText(), r.path("updatedAt").asText(), r.path("elapsedMs").asLong(), r.path("stale").asBoolean(),
                counts, webPath(workspace, member, "execution"));
    }
}
