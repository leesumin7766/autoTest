package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuPlacementRecoveryTest {
    final ObjectMapper mapper=new ObjectMapper();
    final FunctionalMenuValidator validator=new FunctionalMenuValidator(mapper);
    @Test void loginScreenIsAnAuthenticationRouteNotAnInventedSidebarMenu() throws Exception {
        var s=snapshot();s.putObject("authentication").put("required",true).put("loginUrl","https://example.com/");
        var input=row();input.put("functionName","로그인").putArray("menuPath").add("로그인 화면");
        var result=validate(input,s);assertTrue(result.path("included").asBoolean());assertEquals("AUTHENTICATION_ROUTE",result.path("placementStatus").asText());
        assertEquals("https://example.com/",result.path("targetUrl").asText());assertTrue(result.path("matchedMenuKeys").isEmpty());
        input.put("functionName","작업 수정");assertFalse(validate(input,s).path("included").asBoolean());
        input.put("functionName","로그인");s.withObject("authentication").put("required",false);assertFalse(validate(input,s).path("included").asBoolean());
    }
    ObjectNode snapshot() throws Exception {return (ObjectNode)mapper.readTree("""
        {"webStructure":{"version":"web-first-v1","nodes":[{"kind":"MENU","path":["Jobs"]}]},
        "evidence":[{"id":"doc","kind":"DOCUMENT","text":"6. Jobs: 작업 수정 화면에서 표시명을 변경하는 기능"},
        {"id":"menu","kind":"MENU","menuKey":"jobs","webPath":["Jobs"],"text":"메뉴 → Jobs"}]}
        """);}
    ObjectNode row() throws Exception {return (ObjectNode)mapper.readTree("""
        {"rowId":"f","menuPath":["Jobs"],"functionName":"작업 수정","menuDescription":"작업의 표시명을 변경하는 기능",
        "location":"CONTENT","matchedMenuKeys":[],"evidence":[{"sourceId":"doc","quote":"6. Jobs: 작업 수정 화면에서 표시명을 변경하는 기능"}],"reviewNotes":[]}
        """);}
    JsonNode validate(ObjectNode row,ObjectNode snapshot) {return validator.validate(mapper.createArrayNode().add(row),snapshot,null).get(0);}
    @Test void exactObservedPathAndDocumentLabelRestoreOnlyMissingReference() throws Exception {
        var input=row();var result=validate(input,snapshot());
        assertEquals(input.path("menuPath"),result.path("menuPath"));assertTrue(result.path("included").asBoolean());
        assertEquals("jobs",result.path("matchedMenuKeys").get(0).asText());assertEquals(2,result.path("evidence").size());
        assertEquals("EXACT_PATH_DOCUMENT_REFERENCE",result.path("placementReview").path("method").asText());
        assertEquals("PROPOSED",result.path("connectionStatus").asText());assertEquals("NEEDS_REVIEW",result.path("reviewStatus").asText());
        assertEquals(input.path("menuDescription"),result.path("menuDescription"));assertEquals(1,input.path("evidence").size());
    }
    @Test void unknownBlankOrNonExactPathsAndUnrelatedDocumentsRemainPending() throws Exception {
        for(String name:new String[]{"","jobs","Job","Invented"}) {
            var input=row();input.putArray("menuPath");if(!name.isEmpty())input.withArray("menuPath").add(name);
            var result=validate(input,snapshot());assertFalse(result.path("included").asBoolean());assertTrue(result.path("menuPath").isEmpty());
            assertEquals(input.path("menuPath"),result.path("placementReview").path("proposedMenuPath"));
        }
        var s=snapshot();((ObjectNode)s.path("evidence").get(0)).put("text","OtherJobs: unrelated");var input=row();((ObjectNode)input.path("evidence").get(0)).put("quote","OtherJobs: unrelated");
        assertFalse(validate(input,s).path("included").asBoolean());
    }
    @Test void conflictingReferencesDuplicateLabelsAndEvidenceLimitAreNotSilentlyOverridden() throws Exception {
        var input=row();input.withArray("matchedMenuKeys").add("missing");assertFalse(validate(input,snapshot()).path("included").asBoolean());
        var s=snapshot();s.withObject("webStructure").withArray("nodes").addObject().put("kind","MENU").putArray("path").add("Other").add("Jobs");
        assertFalse(validate(row(),s).path("included").asBoolean());
        input=row();for(int i=1;i<8;i++)input.withArray("evidence").add(input.path("evidence").get(0).deepCopy());
        var result=validate(input,snapshot());assertFalse(result.path("included").asBoolean());assertEquals(8,result.path("evidence").size());
    }
    @Test void fabricatedQuotesAreRejectedBeforeAnyAutomaticPlacement() throws Exception {
        var input=row();((ObjectNode)input.path("evidence").get(0)).put("quote","Jobs invented quote");
        assertThrows(RuntimeException.class,()->validate(input,snapshot()));
    }
    @Test void explicitExclusionAndSaveAuditArePreserved() throws Exception {
        var input=row();input.put("included",false);var excluded=validate(input,snapshot());assertFalse(excluded.path("included").asBoolean());
        var originals=mapper.createArrayNode().add(validate(row(),snapshot()));var edited=originals.deepCopy();((ObjectNode)edited.get(0)).put("included",false);
        var saved=validator.validate(edited,snapshot(),originals).get(0);
        assertFalse(saved.path("included").asBoolean());assertEquals(originals.get(0).path("placementReview"),saved.path("placementReview"));
        assertEquals(2,saved.path("evidence").size());
    }
    @Test void numberedExternalContractCanOmitMenuRefWithoutLosingCorroboratedPath() throws Exception {
        var contract=new SelfLlmMenuContract(mapper);var job=java.util.UUID.randomUUID();var s=snapshot();
        var input=row();input.remove(java.util.List.of("evidence","matchedMenuKeys"));input.putArray("evidenceRefIds").add("E0001");
        var restored=contract.restore(job,s,contract.describe(job,s).path("requestId").asText(),mapper.createArrayNode().add(input));
        var result=validator.validate(restored,s,null).get(0);assertTrue(result.path("included").asBoolean());assertEquals("jobs",result.path("matchedMenuKeys").get(0).asText());
    }
}
