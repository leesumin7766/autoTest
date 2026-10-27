package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;

/** Recover an omitted menu reference, never infer an unobserved path or translate labels. */
final class MenuPlacementRecovery {
    record Match(JsonNode menu,String documentSourceId) {}
    static Optional<Match> find(JsonNode path,JsonNode verified,JsonNode snapshot,Map<String,JsonNode> sources) {
        if(path.isEmpty()||path.size()>3||verified.size()>=8)return Optional.empty();
        for(var reference:verified) {
            var source=sources.get(reference.path("sourceId").asText());
            if(source!=null&&!source.path("menuKey").asText().isBlank()&&!source.path("webPath").equals(path))return Optional.empty();
        }
        var nodes=new ArrayList<JsonNode>();
        for(var node:snapshot.path("webStructure").path("nodes"))
            if("MENU".equals(node.path("kind").asText())&&node.path("path").equals(path))nodes.add(node);
        if(nodes.size()!=1)return Optional.empty();
        String leaf=path.get(path.size()-1).asText();
        // A repeated label in different locations does not establish a unique document link.
        for(var node:snapshot.path("webStructure").path("nodes")) {
            var other=node.path("path");
            if(!other.isEmpty()&&!other.equals(path)&&leaf.equals(other.get(other.size()-1).asText()))return Optional.empty();
        }
        var menus=new ArrayList<JsonNode>();
        for(var source:sources.values())if("MENU".equals(source.path("kind").asText())&&source.path("webPath").equals(path)
                &&!source.path("menuKey").asText().isBlank()&&!source.path("text").asText().isBlank())menus.add(source);
        if(menus.size()!=1)return Optional.empty();
        var pattern=Pattern.compile("(?<![\\p{L}\\p{N}_])"+Pattern.quote(leaf)+"(?![\\p{L}\\p{N}_])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
        for(var reference:verified) {
            var source=sources.get(reference.path("sourceId").asText());
            if(source!=null&&"DOCUMENT".equals(source.path("kind").asText())&&pattern.matcher(reference.path("quote").asText()).find())
                return Optional.of(new Match(menus.getFirst(),source.path("id").asText()));
        }
        return Optional.empty();
    }
}
