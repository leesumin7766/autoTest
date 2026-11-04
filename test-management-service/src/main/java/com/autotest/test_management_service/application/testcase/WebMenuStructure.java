package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** The observed web hierarchy owns classification; documents may describe, not invent it. */
public final class WebMenuStructure {
    private static final Set<String> CONTAINERS=Set.of("메뉴","탭","Global","Local","Breadcrumb","Excel 1depth");
    public static ArrayNode path(ObjectMapper mapper,JsonNode source,String product){
        var path=mapper.createArrayNode();
        for(JsonNode part:source.path("path")){
            String label=part.asText().trim();
            if(label.isEmpty()||CONTAINERS.contains(label)||label.equals(product))continue;
            if(path.isEmpty()||!path.get(path.size()-1).asText().equals(label))path.add(label);
        }
        if(source.path("barConfirmed").asBoolean() && Set.of("HEADER","FOOTER").contains(source.path("location").asText())){
            var prefixed=mapper.createArrayNode().add("HEADER".equals(source.path("location").asText())?"상단바":"하단바");path.forEach(prefixed::add);return prefixed;
        }
        return path;
    }
    public static ObjectNode build(ObjectMapper mapper,ArrayNode evidence,String product){
        var structure=mapper.createObjectNode().put("version","web-first-v1").put("productName",product);
        var nodes=structure.putArray("nodes");Set<String> seen=new HashSet<>();
        for(JsonNode raw:evidence)if(Set.of("MENU","MENU_GROUP").contains(raw.path("kind").asText())){
            var source=(ObjectNode)raw;ArrayNode path=path(mapper,source,product);source.set("webPath",path);
            if(path.isEmpty())continue;
            if(!seen.add(path.toString()))continue;
            var node=nodes.addObject().put("sourceId",source.path("id").asText()).put("menuKey",source.path("menuKey").asText())
                .put("kind",source.path("kind").asText()).put("location",source.path("location").asText()).put("order",nodes.size()-1);
            node.set("path",path.deepCopy());node.put("depthReviewRequired",path.size()>3);
        }
        for(JsonNode raw:evidence)if("CONTROL".equals(raw.path("kind").asText())){
            var anchor=path(mapper,raw,product);if(!anchor.isEmpty())anchor.remove(anchor.size()-1);
            boolean found=false;for(JsonNode node:nodes)if(node.path("path").equals(anchor)){found=true;break;}
            ((ObjectNode)raw).set("webPath",found?anchor:mapper.createArrayNode());
        }
        return structure;
    }
}
