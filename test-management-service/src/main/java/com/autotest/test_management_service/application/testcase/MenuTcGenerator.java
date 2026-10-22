package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import org.springframework.http.HttpStatus;

/** Deterministic expansion. Templates describe checks, never observed product behavior. */
@Component
public class MenuTcGenerator {
    public static final String VERSION = "menu-tc-v1";
    private final ObjectMapper mapper;
    private final JsonNode bank;
    public MenuTcGenerator(ObjectMapper mapper) {
        this.mapper = mapper;
        try (var stream = getClass().getResourceAsStream("/tc-templates/menu-tc-v1.json")) {
            bank = mapper.readTree(Objects.requireNonNull(stream));
            if (!VERSION.equals(bank.path("version").asText())) throw new IllegalStateException("TC template version mismatch");
        } catch (IOException failure) { throw new IllegalStateException("Cannot load TC templates", failure); }
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    public static ProductDescriptionException invalid(String code, String message) {
        return new ProductDescriptionException(HttpStatus.BAD_REQUEST, code, message);
    }
    public String classify(JsonNode feature) {
        String explicit = feature.path("featureType").asText("");
        if (!explicit.isBlank()) {
            if (!Set.of("로그인", "주요기능", "부가기능", "CRUD").contains(explicit)) throw invalid("TC_FEATURE_TYPE_INVALID", "기능구분은 로그인·주요기능·부가기능·CRUD 중 하나여야 합니다.");
            return explicit;
        }
        String name = feature.path("functionName").asText().toLowerCase(Locale.ROOT);
        boolean history = name.matches(".*(기록|이력|조회|history|audit).*");
        return !history && (name.contains("로그인") || name.contains("로그아웃") || name.equals("login") || name.equals("logout")) ? "로그인" : "주요기능";
    }
    public ObjectNode generate(String product, JsonNode features, JsonNode source) {
        if (product == null || product.isBlank() || product.length() > 160) throw invalid("TC_PRODUCT_REQUIRED", "제품명이 필요합니다.");
        if (!features.isArray() || features.isEmpty() || features.size() > 2000) throw invalid("TC_FEATURES_INVALID", "기능은 1~2,000개여야 합니다.");
        var output = mapper.createObjectNode().put("schemaVersion", VERSION).put("templateVersion", VERSION)
            .put("templateDigest", hash(bank.toString())).put("generationMode", "TEMPLATE").put("productName", product)
            .put("status", "DRAFT").put("reviewRequired", true);
        output.set("source", source.deepCopy());
        var cases = output.putArray("testCases"); var normalized = output.putArray("features");
        Set<String> ids = new HashSet<>(); int index = 0;
        for (JsonNode feature : features) {
            String id = feature.path("featureId").asText();
            if (id.isBlank() || !ids.add(id)) throw invalid("TC_FEATURE_ID_INVALID", "기능 ID가 비어 있거나 중복됩니다.");
            if (!feature.path("featurePath").isArray() || feature.path("featurePath").isEmpty() || feature.path("featurePath").size() > 5)
                throw invalid("TC_FEATURE_PATH_INVALID", "기능 경로는 1~5단계여야 합니다.");
            for (JsonNode part : feature.path("featurePath")) if (!part.isTextual() || part.asText().isBlank()) throw invalid("TC_FEATURE_PATH_INVALID", "빈 경로는 허용되지 않습니다.");
            String type = classify(feature);
            ObjectNode normalizedFeature = feature.deepCopy(); normalizedFeature.put("featureType", type);
            normalizedFeature.put("classificationSource", feature.path("featureType").asText().isBlank() ? "SUGGESTED" : "EXPLICIT");
            normalized.add(normalizedFeature);
            expand(cases, normalizedFeature, type.equals("로그인") ? "LOGIN" : "FEATURE", ++index, type, source);
        }
        for (String group : List.of("COMMON", "PRODUCT_DOCUMENT", "USER_MANUAL")) {
            String title = switch (group) { case "COMMON" -> "공통"; case "PRODUCT_DOCUMENT" -> "제품설명서"; default -> "사용자취급설명서"; };
            var scope = mapper.createObjectNode().put("featureId", "scope:" + group).put("description", title).put("classificationSource", "POLICY");
            scope.putArray("featurePath").add(title); scope.putArray("evidence");
            expand(cases, scope, group, ++index, "공통", source);
        }
        output.put("featureCount", features.size()).put("caseCount", cases.size());
        var summary = output.putObject("summary");
        for (String type : List.of("로그인", "주요기능", "부가기능", "CRUD")) {
            int count = 0; for (JsonNode f : normalized) if (type.equals(f.path("featureType").asText())) count++;
            summary.put(type, count);
        }
        summary.put("commonCaseCount", 68);
        output.put("inputDigest", hash(mapper.createObjectNode().put("product", product).set("features", normalized).toString() + source + output.path("templateDigest")));
        return output;
    }
    private void expand(ArrayNode cases, JsonNode feature, String group, int groupIndex, String type, JsonNode source) {
        int local = 0;
        for (JsonNode template : bank.path("groups").path(group)) {
            String rule = template.path("ruleId").asText(); String scope = feature.path("featureId").asText();
            String scenario = template.path("scenario").asText();
            local++;
            if (group.equals("FEATURE") && local == 1) scenario = (type.equals("CRUD") ? "해당 CRUD 기능" : type) + "이 목적에 맞게 정상적으로 동작하는지 확인";
            // FEATURE's first row is the only classification-specific template text.
            var tc = cases.addObject().put("id", hash(source.toString() + ":" + scope + ":" + rule))
                .put("tcId", "TC-%03d-%03d".formatted(groupIndex, local)).put("featureId", scope)
                .put("featureType", type).put("ruleId", rule).put("templateVersion", VERSION)
                .put("qualityCharacteristic", template.path("qualityCharacteristic").asText().replaceAll("\\s+", ""))
                .put("subCharacteristic", template.path("subCharacteristic").asText()).put("scenario", scenario)
                .put("inputs", template.path("inputs").asText()).put("expectedResult", template.path("expectedResult").asText())
                .put("scopeDescription", feature.path("description").asText())
                .put("testType", group.equals("PRODUCT_DOCUMENT") || group.equals("USER_MANUAL") ? "DOCUMENT_CONFORMANCE" : "PRODUCT_BEHAVIOR")
                .put("reviewRequired", true).put("executionReadiness", "NEEDS_CONTEXT").put("executionMode", "REVIEW_REQUIRED");
            tc.putNull("result"); tc.set("featurePath", feature.path("featurePath").deepCopy());
            tc.set("evidence", feature.path("evidence").isArray() ? feature.path("evidence").deepCopy() : mapper.createArrayNode());
            tc.set("source", source.deepCopy()); tc.set("defectExample", template.path("defectExample").deepCopy());
            if (group.equals("FEATURE") && local == 1) {
                tc.put("inputs", type + " · " + feature.path("description").asText());
                ((ObjectNode)tc.path("defectExample")).put("severity", type.equals("부가기능") ? "M" : "H");
            }
            String applicability = scenario.startsWith("***") ? scenario.lines().findFirst().orElse("") : "해당 기능·시험환경에 적용되는지 실행 시 확인";
            var plan = tc.putObject("executionPlan"); plan.put("targetUrl", feature.path("targetUrl").asText(source.path("targetUrl").asText()));
            plan.put("applicability", applicability).put("naPolicy", "적용 대상이 아닌 경우 실행 근거와 사유를 남기고 N/A 판정");
            plan.set("menuKeys", feature.path("matchedMenuKeys").isArray() ? feature.path("matchedMenuKeys").deepCopy() : mapper.createArrayNode());
            plan.putArray("steps").add("기능 경로와 대상 화면을 확인한다.").add(scenario).add("기대 결과와 실제 동작·증거를 비교한다.");
            plan.putArray("requiredContext").add("접속·인증 정보").add("시험 계정·권한·데이터").add("적용 조건·입력 제약").add("화면 요소·관찰 가능한 판정 기준");
            tc.putArray("reviewNotes").add("공통 시험 기준에서 생성한 초안입니다. 시험 결과와 결함 예시는 별개입니다.")
                .add(feature.path("classificationSource").asText().equals("SUGGESTED") ? "기능구분은 제안값입니다. 검토 후 생성하세요." : "실행 전 필요한 환경·입력·관찰 기준을 보완하세요.");
        }
    }
}
