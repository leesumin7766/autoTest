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
    public static final String VERSION = "menu-tc-v3";
    private static final String READ_VIEW_RULE = "CRUD-VIEW-01";
    private static final String READ_SEARCH_RULE = "CRUD-SEARCH-01";
    private final ObjectMapper mapper;
    private final JsonNode bank;
    public MenuTcGenerator(ObjectMapper mapper) {
        this.mapper = mapper;
        try (var stream = getClass().getResourceAsStream("/tc-templates/menu-tc-v3.json")) {
            bank = mapper.readTree(Objects.requireNonNull(stream));
            if (!"menu-tc-v3".equals(bank.path("version").asText())) throw new IllegalStateException("TC template version mismatch");
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
        String name = feature.path("functionName").asText().strip().toLowerCase(Locale.ROOT);
        boolean history = name.matches(".*(기록|이력|조회|history|audit).*");
        if (!history && (name.contains("로그인") || name.equals("login"))) return "로그인";
        String[] words = name.split("(?U)\\s+");
        return Set.of("조회", "생성", "삭제", "등록", "수정").contains(words[words.length - 1]) ? "CRUD" : "주요기능";
    }
    public ObjectNode generate(String product, JsonNode features, JsonNode source) {
        if (product == null || product.isBlank() || product.length() > 160) throw invalid("TC_PRODUCT_REQUIRED", "제품명이 필요합니다.");
        if (!features.isArray() || features.isEmpty() || features.size() > 2000) throw invalid("TC_FEATURES_INVALID", "기능은 1~2,000개여야 합니다.");
        var output = mapper.createObjectNode().put("schemaVersion", VERSION).put("templateVersion", VERSION)
            .put("templateDigest", hash(bank.toString()+TcProcedureWriter.VERSION+TcExecutionSpecification.VERSION+READ_VIEW_RULE+"one-per-menu-v1"+READ_SEARCH_RULE)).put("procedureVersion",TcProcedureWriter.VERSION)
            .put("executionSpecificationVersion",TcExecutionSpecification.VERSION).put("generationMode", "TEMPLATE").put("productName", product)
            .put("status", "DRAFT").put("reviewRequired", true);
        output.set("source", source.deepCopy());
        var cases = output.putArray("testCases"); var normalized = output.putArray("features");
        Set<String> ids = new HashSet<>(); Set<String> checkedReadMenus=new HashSet<>(), checkedSearchMenus=new HashSet<>(); int index = 0;
        var ordered=new ArrayList<JsonNode>();features.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(feature -> "로그인".equals(classify(feature)) ? 0 : 1));
        for (JsonNode feature : ordered) {
            String id = feature.path("featureId").asText();
            if (id.isBlank() || !ids.add(id)) throw invalid("TC_FEATURE_ID_INVALID", "기능 ID가 비어 있거나 중복됩니다.");
            if (!feature.path("featurePath").isArray() || feature.path("featurePath").isEmpty() || feature.path("featurePath").size() > 5)
                throw invalid("TC_FEATURE_PATH_INVALID", "기능 경로는 1~5단계여야 합니다.");
            for (JsonNode part : feature.path("featurePath")) if (!part.isTextual() || part.asText().isBlank()) throw invalid("TC_FEATURE_PATH_INVALID", "빈 경로는 허용되지 않습니다.");
            String type = classify(feature);
            ObjectNode normalizedFeature = feature.deepCopy(); normalizedFeature.put("featureType", type);
            normalizedFeature.put("classificationSource", feature.path("featureType").asText().isBlank() ? "SUGGESTED" : "EXPLICIT");
            normalized.add(normalizedFeature);
            expand(cases, normalizedFeature, type.equals("로그인") ? "LOGIN" : type.equals("CRUD") ? "CRUD" : "FEATURE", ++index, type, source);
            if (type.equals("CRUD") && TcTemplateBinding.operation(feature.path("functionName").asText()).equals("READ")
                    && feature.path("matchedMenuKeys").isArray() && !feature.path("matchedMenuKeys").isEmpty()
                    && checkedReadMenus.add(feature.path("matchedMenuKeys").get(0).asText()))
                addReadViewCase(cases, normalizedFeature, index, source);
            if (type.equals("CRUD") && TcTemplateBinding.operation(feature.path("functionName").asText()).equals("READ")
                    && feature.path("menuDescription").asText(feature.path("description").asText()).matches("(?s).*(검색|필터|search|filter).*")
                    && feature.path("matchedMenuKeys").isArray() && !feature.path("matchedMenuKeys").isEmpty()
                    && checkedSearchMenus.add(feature.path("matchedMenuKeys").get(0).asText()))
                addReadSearchCase(cases, normalizedFeature, index, source);
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
        output.put("inputDigest", hash(VERSION + mapper.createObjectNode().put("product", product).set("features", normalized).toString() + source + output.path("templateDigest")));
        return output;
    }
    /** Supplemental screen-availability check; original 16 CRUD checks remain intact. */
    private void addReadViewCase(ArrayNode cases, JsonNode feature, int groupIndex, JsonNode source) {
        String scope=feature.path("featureId").asText();
        String name=feature.path("functionName").asText();
        String scenario=name+": 조회 화면이 열리고 기본 내용을 표시하는지 확인";
        String inputs="저장된 시험 계정으로 접속하여 연결된 조회 메뉴를 연다. 시험 데이터의 정확성은 별도 조회 TC에서 확인한다.";
        String expected="연결된 조회 화면의 제목과 기본 본문이 표시된다.";
        var tc=cases.addObject().put("id",hash(source.toString()+":"+scope+":"+READ_VIEW_RULE))
            .put("tcId","TC-%03d-017".formatted(groupIndex)).put("featureId",scope).put("featureType","CRUD")
            .put("ruleId",READ_VIEW_RULE).put("templateVersion",VERSION).put("qualityCharacteristic","기능적합성")
            .put("subCharacteristic","기능완전성").put("scenario",scenario).put("inputs",inputs)
            .put("expectedResult",expected).put("scopeDescription",feature.path("description").asText())
            .put("testType","PRODUCT_BEHAVIOR").put("reviewRequired",true).put("executionReadiness","READY")
            .put("executionMode","BROWSER_CANDIDATE").put("automationMode","BROWSER_CANDIDATE");
        tc.putNull("result");tc.set("featurePath",feature.path("featurePath").deepCopy());
        tc.set("evidence",feature.path("evidence").deepCopy());tc.set("source",source.deepCopy());
        tc.putObject("defectExample").put("summary",name+" 조회 화면 표시 실패")
            .put("severity","M").put("content","연결된 조회 메뉴에서 화면 제목 또는 기본 본문이 표시되지 않음");
        tc.putArray("reviewNotes").add("화면 접근·표시만 확인합니다. 조회 데이터의 값·건수 정확성은 기존 CRUD TC에서 별도로 판정합니다.");
        var plan=tc.putObject("executionPlan").put("targetUrl",feature.path("targetUrl").asText(source.path("targetUrl").asText()))
            .put("specificationVersion","execution-spec-v1").put("tcContentDigest",hash(scenario+inputs+expected))
            .put("readinessReason","수집 메뉴와 실제 화면 제목·본문을 대조합니다.")
            .put("applicability","수집 근거가 있는 조회 메뉴");
        plan.set("menuKeys",feature.path("matchedMenuKeys").deepCopy());
        plan.putArray("steps").add("저장된 계정으로 로그인한다.").add("연결된 조회 메뉴로 이동한다.").add("실제 화면 제목과 기본 본문을 확인한다.");
        plan.putObject("selection").put("scopeOperation","READ").put("exclusionCandidate",false)
            .put("status","DETERMINISTIC_VIEW_CHECK").put("reason","메뉴·화면 표시만 확인");
        plan.putObject("specification").put("status","READY").put("profile","READ_ONLY_VIEW")
            .put("timeoutSeconds",60);
    }
    /** Search behavior only: compare visible list rows before and after a read-only query. */
    private void addReadSearchCase(ArrayNode cases, JsonNode feature, int groupIndex, JsonNode source) {
        String scope=feature.path("featureId").asText(), name=feature.path("functionName").asText();
        String scenario=name+": 현재 목록의 한 항목명을 검색하여 목록이 좁혀지는지 확인";
        String inputs="조회 화면에 이름이 서로 다른 행이 2개 이상 있을 때 표시된 첫 항목명을 검색어로 사용한다. 저장·변경 동작은 수행하지 않는다.";
        String expected="검색 후 표시된 목록의 항목 수가 줄어들고, 검색에 사용한 원래 항목이 남는다.";
        var tc=cases.addObject().put("id",hash(source.toString()+":"+scope+":"+READ_SEARCH_RULE))
            .put("tcId","TC-%03d-018".formatted(groupIndex)).put("featureId",scope).put("featureType","CRUD")
            .put("ruleId",READ_SEARCH_RULE).put("templateVersion",VERSION).put("qualityCharacteristic","기능적합성")
            .put("subCharacteristic","기능정확성").put("scenario",scenario).put("inputs",inputs)
            .put("expectedResult",expected).put("scopeDescription",feature.path("description").asText())
            .put("testType","PRODUCT_BEHAVIOR").put("reviewRequired",true).put("executionReadiness","READY")
            .put("executionMode","BROWSER_CANDIDATE").put("automationMode","BROWSER_CANDIDATE");
        tc.putNull("result");tc.set("featurePath",feature.path("featurePath").deepCopy());
        tc.set("evidence",feature.path("evidence").deepCopy());tc.set("source",source.deepCopy());
        tc.putObject("defectExample").put("summary",name+" 목록 검색 실패")
            .put("severity","M").put("content","검색어 입력 후 목록 행이 검색어와 일치하도록 좁혀지지 않음");
        tc.putArray("reviewNotes").add("화면에 이미 있는 두 항목으로 검색 동작만 확인합니다. 전체 데이터의 완전성·값 정확성은 기존 CRUD TC에서 별도 판정합니다.");
        var plan=tc.putObject("executionPlan").put("targetUrl",feature.path("targetUrl").asText(source.path("targetUrl").asText()))
            .put("specificationVersion","execution-spec-v1").put("tcContentDigest",hash(scenario+inputs+expected))
            .put("readinessReason","수집 메뉴와 화면의 검색 입력·목록 행을 대조합니다.")
            .put("applicability","검색 입력과 서로 다른 항목 2개 이상이 보이는 목록 화면");
        plan.set("menuKeys",feature.path("matchedMenuKeys").deepCopy());
        plan.putArray("steps").add("저장된 계정으로 로그인하고 연결된 조회 메뉴를 연다.")
            .add("표시된 서로 다른 두 항목명과 검색 입력을 확인한다.")
            .add("첫 항목명을 검색하고 목록 행의 이름을 다시 비교한다.");
        plan.putObject("selection").put("scopeOperation","READ").put("exclusionCandidate",false)
            .put("status","DETERMINISTIC_SEARCH_CHECK").put("reason","현재 화면의 목록 검색 동작만 확인");
        plan.putObject("specification").put("status","READY").put("profile","READ_ONLY_SEARCH")
            .put("timeoutSeconds",60);
    }
    private void expand(ArrayNode cases, JsonNode feature, String group, int groupIndex, String type, JsonNode source) {
        int local = 0;
        var templates=new ArrayList<JsonNode>();bank.path("groups").path(group).forEach(templates::add);
        if(group.equals("LOGIN"))templates.sort(Comparator.comparingInt(t -> "LOGIN-04".equals(t.path("ruleId").asText()) ? 0 : 1));
        for (JsonNode template : templates) {
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
            if(group.equals("LOGIN")&&(rule.equals("LOGIN-17")||rule.equals("LOGIN-18"))) {
                var policy=source.path("authentication");
                tc.set("authenticationPolicy",policy.deepCopy());
                plan.withArray("requiredContext").add("잠금 시험 전용 계정과 잠금 해제·복구 절차 (운영 관리자 계정 사용 금지)");
                var steps=mapper.createArrayNode().add("문서 근거와 시험 계정의 실제 잠금 정책을 확인한다.");
                if(policy.has("failedAttemptLimit")) {
                    int count=policy.path("failedAttemptLimit").asInt();
                    tc.put("scenario",rule.equals("LOGIN-17")?"비밀번호 연속 "+count+"회 실패 시 문서에 명시된 로그인 제한이 적용되는지 확인":"로그인 제한 후 새 세션에서도 제한이 유지되는지 확인");
                    tc.put("inputs","시험 전용 계정, 잘못된 비밀번호, 문서 기준 실패 횟수 "+count+"회");
                    tc.put("expectedResult","문서의 잠금 조건과 해제 절차에 따라 접근이 제한된다. 화면 메시지만으로 성공 판정하지 않는다.");
                    steps.add("새 시험 계정 상태에서 잘못된 비밀번호로 "+count+"회 시도하고 매회 결과를 기록한다.");
                } else {
                    steps.add("잠금 횟수가 확정되지 않으면 반복 시도를 수행하지 않고 정책 확인 대상으로 남긴다.");
                    plan.withArray("requiredContext").add("문서에 근거한 실패 허용 횟수·잠금 조건");
                }
                steps.add("정상 비밀번호의 접근 제한과 새 세션의 상태를 관찰하고, 복구 절차로 계정을 복원한다.");
                plan.set("steps",steps);
                if(policy.path("evidence").isArray())tc.set("evidence",policy.path("evidence").deepCopy());
            }
            TcTemplateBinding.bind(mapper,tc,feature,template,group);
            tc.putArray("reviewNotes").add("공통 시험 기준에서 생성한 초안입니다. 시험 결과와 결함 예시는 별개입니다.")
                .add(feature.path("classificationSource").asText().equals("SUGGESTED") ? "기능구분은 제안값입니다. 검토 후 생성하세요." : "실행 전 필요한 환경·입력·관찰 기준을 보완하세요.");
        }
    }
}
