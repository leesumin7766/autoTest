package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FunctionalMenuValidatorTest {
    final ObjectMapper mapper=new ObjectMapper();
    final FunctionalMenuValidator validator=new FunctionalMenuValidator(mapper);
    ObjectNode snapshot() throws Exception {return (ObjectNode)mapper.readTree("""
        {"evidence":[{"id":"doc","kind":"DOCUMENT","text":"공통 설정을 조회하는 기능"},
        {"id":"menu","kind":"MENU","text":"Global Properties","menuKey":"menu-key"}]}
        """);}
    ObjectNode row() throws Exception{return (ObjectNode)mapper.readTree("""
        {"menuPath":["설정"],"functionName":"공통 설정","menuDescription":"공통 설정을 조회하는 기능","location":"SIDEBAR",
        "matchedMenuKeys":["menu-key"],"evidence":[{"sourceId":"doc","quote":"공통 설정을 조회하는 기능"}],"reviewNotes":[]}
        """);}
    @Test void webHierarchyOverridesModelGroupsAndUnmatchedFactsStayOutsideTree() throws Exception {
        var snapshot=snapshot();snapshot.put("productName","NeoFlow Server");
        var menu=(ObjectNode)snapshot.path("evidence").get(1);menu.put("location","SIDEBAR");menu.putArray("path").add("메뉴").add("운영").add("실시간");
        snapshot.set("webStructure",WebMenuStructure.build(mapper,(com.fasterxml.jackson.databind.node.ArrayNode)snapshot.path("evidence"),"NeoFlow Server"));
        var input=row();input.putArray("menuPath").add("Excel 1depth");
        var result=validator.validate(mapper.createArrayNode().add(input),snapshot,null).get(0);
        assertEquals("운영",result.path("menuPath").get(0).asText());assertEquals("실시간",result.path("menuPath").get(1).asText());
        assertEquals("WEB_ANCHORED",result.path("placementStatus").asText());
        input.putArray("matchedMenuKeys").add("not-observed");
        result=validator.validate(mapper.createArrayNode().add(input),snapshot,null).get(0);
        assertFalse(result.path("included").asBoolean());assertTrue(result.path("menuPath").isEmpty());
        assertEquals("UNASSIGNED",result.path("placementStatus").asText());
    }
    @Test void sidebarBottomIsNotFooterAndRealBarsAreOptional() throws Exception {
        var evidence=mapper.createArrayNode();
        evidence.addObject().put("id","theme").put("kind","MENU").put("location","SIDEBAR").putArray("path").add("메뉴").add("테마 전환");
        var tree=WebMenuStructure.build(mapper,evidence,"NeoFlow Server");
        assertEquals("테마 전환",tree.path("nodes").get(0).path("path").get(0).asText());
        var footer=evidence.addObject().put("id","help").put("kind","MENU").put("location","FOOTER").put("barConfirmed",true);footer.putArray("path").add("메뉴").add("도움말");
        tree=WebMenuStructure.build(mapper,evidence,"NeoFlow Server");assertEquals("하단바",tree.path("nodes").get(1).path("path").get(0).asText());
    }
    @Test void menuReferenceIsAttachedButConnectionRemainsOnlyProposed() throws Exception {
        var result=validator.validate(mapper.createArrayNode().add(row()),snapshot(),null).get(0);
        assertEquals(2,result.path("evidence").size());assertEquals("VALID",result.path("evidenceStatus").asText());
        assertEquals("PROPOSED",result.path("connectionStatus").asText());assertEquals("NEEDS_REVIEW",result.path("reviewStatus").asText());
    }
    @Test void nonexistentMenuIsDisconnectedAndInventedQuoteStillRejected() throws Exception {
        var row=row();row.putArray("matchedMenuKeys").add("unknown-menu");
        var result=validator.validate(mapper.createArrayNode().add(row),snapshot(),null).get(0);
        assertTrue(result.path("matchedMenuKeys").isEmpty());assertEquals("DOCUMENT_ONLY",result.path("verificationStatus").asText());
        assertEquals("NOT_LINKED",result.path("connectionStatus").asText());
        ((ObjectNode)row.path("evidence").get(0)).put("quote","문서에 없는 기능");
        assertThrows(ProductDescriptionException.class,()->validator.validate(mapper.createArrayNode().add(row),snapshot(),null));
    }
    @Test void externalDraftKeepsBadQuotesAndGoodRowsAndCannotClearWarningByEditing() throws Exception {
        var good=row();var bad=row();bad.put("functionName","미확인 기능");
        ((ObjectNode)bad.path("evidence").get(0)).put("quote","존재하지 않는 인용");
        var result=validator.importDraft(mapper.createArrayNode().add(good).add(bad),snapshot());
        assertEquals(2,result.size());assertEquals("VALID",result.get(0).path("evidenceStatus").asText());
        assertEquals("UNVERIFIED",result.get(1).path("evidenceStatus").asText());
        assertEquals("MENU_QUOTE_MISMATCH",result.get(1).path("validationIssues").get(0).path("code").asText());
        var edited=result.deepCopy();((ObjectNode)edited.get(1)).put("evidenceStatus","VALID").putArray("evidence");
        var saved=validator.validate(edited,snapshot(),result);
        assertEquals("UNVERIFIED",saved.get(1).path("evidenceStatus").asText());
        assertEquals("존재하지 않는 인용",saved.get(1).path("evidence").get(0).path("quote").asText());
    }
    @Test void missingSourceIsVisibleAndStructuralLimitsStillApply() throws Exception {
        var bad=row();((ObjectNode)bad.path("evidence").get(0)).put("sourceId","unknown");
        var result=validator.importDraft(mapper.createArrayNode().add(bad),snapshot()).get(0);
        assertEquals("UNVERIFIED",result.path("evidenceStatus").asText());
        assertEquals("MENU_SOURCE_NOT_FOUND",result.path("validationIssues").get(0).path("code").asText());
        bad.putArray("menuPath").add("1").add("2").add("3").add("4");
        assertThrows(ProductDescriptionException.class,()->validator.importDraft(mapper.createArrayNode().add(bad),snapshot()));
    }
}
