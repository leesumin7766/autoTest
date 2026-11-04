package com.autotest.test_management_service.application.crawler;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Menu identity and screen identity are separate. URL equality is never click evidence. */
public final class CapturedMenuTree {
    private CapturedMenuTree() { }
    public record Page(int index, String url, String title, JsonNode navigation, JsonNode screen, JsonNode transition) {
        public Page(int index,String url,String title,JsonNode navigation) {
            this(index,url,title,navigation,com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        }
    }
    public static ObjectNode build(ObjectMapper mapper, List<Page> pages) {
        ObjectNode result = mapper.createObjectNode().put("schemaVersion", "captured-menu-tree-v2")
                .put("reviewRequired", true).put("source", "PLAYWRIGHT_DOM");
        var roots = result.putArray("nodes");
        Map<String, ObjectNode> nodes = new LinkedHashMap<>();
        int observed = 0;
        boolean truncated = false;
        for (Page page : pages) {
            if (!page.navigation().isEmpty()) observed++;
            for (JsonNode item : page.navigation()) {
                var path = item.path("path");
                if (!path.isArray() || path.isEmpty() || path.size() > 12) continue;
                ObjectNode parent = null;
                String prefix = "";
                for (int depth = 0; depth < path.size(); depth++) {
                    boolean leaf = depth == path.size() - 1;
                    String title = path.get(depth).asText();
                    prefix += title.length() + ":" + title;
                    String kind = leaf ? item.path("kind").asText("GROUP") : "GROUP";
                    String menuKey = leaf ? item.path("menuKey").asText() : "";
                    String key = prefix;
                    ObjectNode branch = nodes.get(prefix);
                    if (leaf && !menuKey.isBlank() && branch != null && !branch.path("menuKey").asText().isBlank()
                            && !menuKey.equals(branch.path("menuKey").asText())) key += "|" + menuKey;
                    ObjectNode node = nodes.get(key);
                    if (node == null) {
                        if (nodes.size() >= 2000) { truncated = true; break; }
                        node = mapper.createObjectNode().put("nodeId", UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString())
                                .put("menuKey",menuKey).put("title", title).put("kind", kind).put("url",leaf ? item.path("url").asText() : "")
                                .put("status", "GROUP".equals(kind) ? "GROUP" : menuKey.isBlank() ? "UNVERIFIED" : "UNVISITED");
                        node.putArray("children"); node.putArray("sources"); node.putArray("screens");
                        nodes.put(key, node);
                        if (parent == null) roots.add(node); else parent.withArray("children").add(node);
                    }
                    if (leaf && !"GROUP".equals(kind) && "GROUP".equals(node.path("kind").asText())) {
                        node.put("kind",kind).put("menuKey",menuKey).put("url",item.path("url").asText())
                                .put("status",menuKey.isBlank()?"UNVERIFIED":"UNVISITED");
                    }
                    if (leaf && node.withArray("sources").size() < 10) {
                        ObjectNode source = mapper.createObjectNode().put("pageIndex", page.index()).put("frameUrl", item.path("frameUrl").asText());
                        if (!java.util.stream.StreamSupport.stream(node.withArray("sources").spliterator(), false).anyMatch(source::equals)) node.withArray("sources").add(source);
                    }
                    parent = node;
                }
            }
        }
        int linked = 0, incomplete = 0;
        for (ObjectNode node : nodes.values()) {
            String menuKey=node.path("menuKey").asText();
            if(menuKey.isBlank()) continue;
            for(Page page:pages) {
                if(!menuKey.equals(page.transition().path("menuKey").asText())) continue;
                String outcome=page.transition().path("outcome").asText();
                boolean ready="READY".equals(page.screen().path("readiness").asText()) && "CONFIRMED".equals(outcome);
                node.withArray("screens").add(mapper.createObjectNode().put("pageIndex",page.index()).put("screenKey",page.screen().path("screenKey").asText())
                        .put("url",page.url()).put("status",ready?"CAPTURED":outcome));
                if(ready) { node.put("status","CAPTURED").put("pageIndex",page.index()); }
                else if(!"CAPTURED".equals(node.path("status").asText())) node.put("status",outcome);
            }
            if("CAPTURED".equals(node.path("status").asText())) linked++;
            else if("LOAD_TIMEOUT".equals(node.path("status").asText())) incomplete++;
        }
        result.put("nodeCount", nodes.size()).put("pagesWithNavigation", observed).put("truncated", truncated)
                .put("pagesWithoutNavigation", pages.size()-observed).put("confirmedMenuCount",linked).put("incompleteMenuCount",incomplete);
        return result;
    }
}
