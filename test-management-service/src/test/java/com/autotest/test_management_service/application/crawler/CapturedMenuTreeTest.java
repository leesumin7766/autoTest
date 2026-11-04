package com.autotest.test_management_service.application.crawler;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

class CapturedMenuTreeTest {
    final ObjectMapper mapper = new ObjectMapper();
    @Test void sameUrlMenusLinkOnlyToTheirActualClickResultsAndKeepIncompleteEvidence() {
        ArrayNode menus=mapper.createArrayNode();
        for(String title:List.of("Services","Targets","Timers")) {
            var item=menus.addObject().put("kind","TAB").put("url","https://example.test/").put("frameUrl","https://example.test/frame")
                    .put("menuKey",title.toLowerCase());
            item.putArray("path").add("System").add(title);
        }
        var ready=mapper.createObjectNode().put("screenKey","state1").put("readiness","READY");
        var timeout=mapper.createObjectNode().put("screenKey","state2").put("readiness","LOAD_TIMEOUT");
        var tree=CapturedMenuTree.build(mapper,List.of(
                new CapturedMenuTree.Page(0,"https://example.test/","Services",menus,ready,mapper.createObjectNode().put("menuKey","services").put("outcome","CONFIRMED")),
                new CapturedMenuTree.Page(1,"https://example.test/","Targets",menus,ready,mapper.createObjectNode().put("menuKey","targets").put("outcome","CONFIRMED")),
                new CapturedMenuTree.Page(2,"https://example.test/","Timers",menus,timeout,mapper.createObjectNode().put("menuKey","timers").put("outcome","LOAD_TIMEOUT"))));
        assertEquals("CAPTURED",tree.at("/nodes/0/children/0/status").asText());
        assertEquals(0,tree.at("/nodes/0/children/0/pageIndex").asInt());
        assertEquals(1,tree.at("/nodes/0/children/1/pageIndex").asInt());
        assertEquals("LOAD_TIMEOUT",tree.at("/nodes/0/children/2/status").asText());
        assertFalse(tree.at("/nodes/0/children/2").has("pageIndex"));
        assertEquals(2,tree.path("confirmedMenuCount").asInt());
        assertEquals(1,tree.path("incompleteMenuCount").asInt());
    }
    @Test void legacyUrlEqualityAndSelectedTabAreNotNavigationEvidence() {
        var menus=mapper.createArrayNode();
        menus.addObject().put("kind","TAB").put("url","https://example.test/").put("selected",true).putArray("path").add("Tabs").add("History");
        var tree=CapturedMenuTree.build(mapper,List.of(new CapturedMenuTree.Page(0,"https://example.test/","Home",menus)));
        assertEquals("UNVERIFIED",tree.at("/nodes/0/children/0/status").asText());
        assertFalse(tree.at("/nodes/0/children/0").has("pageIndex"));
    }
}
