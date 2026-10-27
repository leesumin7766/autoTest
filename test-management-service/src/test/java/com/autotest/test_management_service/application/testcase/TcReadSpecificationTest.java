package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcReadSpecificationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode generate(String name, String description) {
        var features = mapper.createArrayNode();
        var feature = features.addObject().put("featureId", "reports").put("functionName", name)
            .put("featureType", "CRUD").put("description", description);
        feature.putArray("featurePath").add("제품").add("보고서").add(name);
        feature.putArray("matchedMenuKeys").add("reports-menu");
        feature.putArray("evidence");
        return new MenuTcGenerator(mapper).generate("제품", features, mapper.createObjectNode().put("targetUrl", "https://example.test/"));
    }

    private JsonNode rule(JsonNode output, String id) {
        for (var tc : output.path("testCases")) if (id.equals(tc.path("ruleId").asText())) return tc;
        throw new AssertionError(id);
    }

    @Test void readRecipeHasStableStepsAndSpecificMissingBindingsWithoutInventedData() {
        var output = generate("보고서 조회", "저장된 보고서를 조회하는 기능");
        for (String id : new String[]{"CRUD-01", "CRUD-08"}) {
            var tc = rule(output, id); var plan = tc.path("executionPlan"); var spec = plan.path("specification");
            assertEquals(TcExecutionSpecification.VERSION, plan.path("specificationVersion").asText());
            assertEquals("NEEDS_CONTEXT", spec.path("status").asText());
            assertEquals("READ_ONLY_VIEW", spec.path("profile").asText());
            assertEquals("NAVIGATE_TARGET", spec.path("steps").get(0).path("action").asText());
            assertEquals("COMPARE_RESULTS", spec.path("steps").get(3).path("action").asText());
            assertEquals(tc.path("featurePath"), spec.path("target").path("featurePath"));
            assertEquals("reports-menu", spec.path("target").path("menuKeys").get(0).asText());
            assertEquals(plan.path("tcContentDigest"), spec.path("sourceTcDigest"));
            assertEquals(spec.path("checks").size() + 2, spec.path("missingBindings").size());
            assertTrue(spec.path("bindings").path("target").isNull());
            for (var check : spec.path("checks")) {
                assertTrue(tc.path("expectedResult").asText().contains(check.path("requirementQuote").asText()));
                assertFalse(check.has("expected"));
            }
            assertTrue(tc.path("result").isNull());
        }
        assertEquals(84, output.path("testCases").size());
    }

    @Test void writesAndInteractiveReadsRetainTheirExistingRecipes() {
        var read = generate("보고서 조회", "저장된 보고서를 조회하는 기능");
        assertEquals("execution-spec-v1", rule(read, "CRUD-06").path("executionPlan").path("specificationVersion").asText());
        assertTrue(rule(read, "CRUD-06").path("executionPlan").path("selection").path("exclusionCandidate").asBoolean());
        for (String description : new String[]{"조건을 검색하고 보고서를 조회하는 기능", "보고서를 조회하고 삭제하는 기능"}) {
            var tc = rule(generate("보고서 조회", description), "CRUD-08");
            assertEquals("execution-spec-v1", tc.path("executionPlan").path("specificationVersion").asText());
        }
    }

    @Test void executionRecipeUpgradeDoesNotRewriteHumanFields() {
        var tc = (ObjectNode) rule(generate("보고서 조회", "저장된 보고서를 조회하는 기능"), "CRUD-08");
        var before = tc.deepCopy();
        var feature = mapper.createObjectNode().put("functionName", "보고서 조회");
        TcExecutionSpecification.upgradeRead(mapper, tc, feature);
        tc.remove("executionPlan"); before.remove("executionPlan");
        assertEquals(before, tc);
    }

    @Test void unboundReadsArePreparationItemsAndChangedTcCannotReuseItsRecipe() {
        var tc = (ObjectNode) rule(generate("보고서 조회", "저장된 보고서를 조회하는 기능"), "CRUD-08");
        assertTrue(TcExecutionSpecification.preparationReason(tc).contains("C1"));
        assertTrue(TcExecutionSpecification.preparationReason(tc).contains("조회 대상"));
        assertEquals("", TcExecutionSpecification.preparationReason(rule(generate("보고서 조회", "보고서 조회 기능"), "CRUD-06")));
        tc.put("expectedResult", "새로운 조건");
        assertTrue(TcExecutionSpecification.preparationReason(tc).contains("변경"));
    }
}
