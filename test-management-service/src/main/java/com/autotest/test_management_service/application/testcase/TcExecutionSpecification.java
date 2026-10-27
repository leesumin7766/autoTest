package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.Pattern;

/** Program-facing read recipe. Product values and screen bindings are never guessed. */
final class TcExecutionSpecification {
    static final String VERSION = "execution-spec-v2-read";
    private static final Pattern INTERACTIVE = Pattern.compile(
        "검색|필터|선택|업로드|다운로드|인쇄|내보내|search|filter|select|upload|download|print|export|create|update|delete|edit"
        + "|(등록|생성|수정|삭제|편집|저장)(?:을|를)?\\s*(?:하|할|기능|후|및|가능|버튼|화면|$)", Pattern.CASE_INSENSITIVE);

    static void upgradeRead(ObjectMapper mapper, ObjectNode tc, JsonNode feature) {
        var plan = (ObjectNode) tc.path("executionPlan");
        var selection = plan.path("selection");
        if (!Set.of("FEATURE-01", "CRUD-01", "CRUD-08").contains(tc.path("ruleId").asText())
                || !"READ".equals(selection.path("scopeOperation").asText())
                || selection.path("exclusionCandidate").asBoolean()
                || plan.path("steps").size() != 4
                || INTERACTIVE.matcher(feature.path("functionName").asText() + " " + tc.path("scopeDescription").asText()).find()) return;

        plan.put("specificationVersion", VERSION);
        var spec = (ObjectNode) plan.path("specification");
        spec.put("schemaVersion", VERSION).put("profile", "READ_ONLY_VIEW")
            .put("sourceTcDigest", plan.path("tcContentDigest").asText());
        var target = spec.putObject("target");
        target.set("featurePath", tc.path("featurePath").deepCopy());
        target.set("menuKeys", plan.path("menuKeys").deepCopy());
        spec.putObject("start").put("url", plan.path("targetUrl").asText())
            .put("authentication", "SAVED_SETUP_REFERENCE").put("session", "FRESH_PER_TC");
        var bindings = spec.putObject("bindings");
        bindings.putNull("target").putNull("readContext");
        var missing = spec.putArray("missingBindings");
        missing.addObject().put("code", "TARGET_BINDING_REQUIRED").put("field", "bindings.target")
            .put("message", "수집 근거의 메뉴·주소·화면 제목과 조회 대상을 연결해야 합니다.");
        missing.addObject().put("code", "READ_CONTEXT_REQUIRED").put("field", "bindings.readContext")
            .put("message", "조건 입력 없이 조회하는 화면인지와 기대 데이터의 근거를 확인해야 합니다.");
        var steps = spec.withArray("steps");
        String[] actions = {"NAVIGATE_TARGET", "PREPARE_READ_CONTEXT", "WAIT_TARGET_READY", "COMPARE_RESULTS"};
        for (int i = 0; i < steps.size(); i++) {
            ((ObjectNode) steps.get(i)).put("id", "S" + (i + 1)).put("action", actions[i])
                .put("bindingRef", i == 0 || i == 2 ? "bindings.target" : i == 1 ? "bindings.readContext" : "checks");
        }
        int index = 0;
        for (var check : spec.path("checks")) {
            ((ObjectNode) check).put("id", "C" + (++index)).putNull("oracle");
            missing.addObject().put("code", "CHECK_BINDING_REQUIRED").put("field", "checks[" + (index - 1) + "]")
                .put("message", "C" + index + "의 기대값·비교 방법·근거를 연결해야 합니다: " + check.path("requirementQuote").asText());
        }
        plan.put("readinessReason", "추가 정보 필요 " + missing.size() + "건: 조회 화면 연결·조회 전제·기대 조건별 검증 근거");
        var context = plan.putArray("requiredContext");
        missing.forEach(issue -> context.add(issue.path("message").asText()));
    }

    static String preparationReason(JsonNode tc) {
        var plan = tc.path("executionPlan");
        if (!VERSION.equals(plan.path("specificationVersion").asText())) return "";
        var spec = plan.path("specification");
        String digest = MenuTcGenerator.hash(tc.path("scenario").asText() + tc.path("inputs").asText() + tc.path("expectedResult").asText());
        if (!digest.equals(spec.path("sourceTcDigest").asText()) || !digest.equals(plan.path("tcContentDigest").asText()))
            return "실행 명세 준비 필요: TestCase 내용이 변경되어 명세를 다시 검토해야 합니다.";
        if ("READY".equals(spec.path("status").asText()) && spec.path("missingBindings").isEmpty()) return "";
        var reasons = new LinkedHashSet<String>();
        for (var issue : spec.path("missingBindings")) if (!issue.path("message").asText().isBlank()) reasons.add(issue.path("message").asText());
        return "실행 명세 준비 필요: " + (reasons.isEmpty() ? "조회 단계·대상 화면·기대 조건 연결을 확인하세요." : String.join(" / ", reasons));
    }
}
