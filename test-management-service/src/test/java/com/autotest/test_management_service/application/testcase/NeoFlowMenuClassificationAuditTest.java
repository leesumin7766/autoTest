package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Diagnostic replay only: does not approve a menu or create a server TC set. */
class NeoFlowMenuClassificationAuditTest {
    @Test void replayCurrentClassificationOnRegeneratedNeoflowDraft() throws Exception {
        var mapper=new ObjectMapper();var generator=new MenuTcGenerator(mapper);
        Path dir=Path.of("../outputs/neoflow-menu-classification");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(dir.resolve("import-result.json")),"Opt-in: regenerated NeoFlow draft fixture required");
        var imported=mapper.readTree(Files.readString(dir.resolve("import-result.json")));
        var input=mapper.readTree(Files.readString(dir.resolve("input.json")));
        var rows=imported.path("job").path("output").path("rows");
        var features=mapper.createArrayNode();var audit=mapper.createObjectNode();var details=audit.putArray("features");
        int functionCount=0,missingClassification=0;
        for(var row:rows){
            if(!"FUNCTION".equals(row.path("rowType").asText()))continue;
            functionCount++;if(!row.hasNonNull("featureType"))missingClassification++;
            if(!row.path("included").asBoolean())continue;
            var feature=features.addObject().put("featureId",row.path("rowId").asText()).put("functionName",row.path("functionName").asText())
                .put("description",row.path("menuDescription").asText());
            var path=feature.putArray("featurePath").add("NeoFlow Server");row.path("menuPath").forEach(path::add);path.add(row.path("functionName").asText());
            feature.set("evidence",row.path("evidence"));
            var detail=details.addObject().put("functionName",row.path("functionName").asText()).put("menuClassification","MISSING")
                .put("defaultTcClassification",generator.classify(feature));detail.set("menuPath",row.path("menuPath"));
        }
        assertEquals(152,functionCount);assertEquals(152,missingClassification);assertEquals(133,features.size());
        var prepared=AuthenticationTcContext.prepare(mapper,"NeoFlow Server",features,input.path("authentication"));
        var source=mapper.createObjectNode().put("kind","DRAFT_CLASSIFICATION_REPLAY_ONLY");source.set("authentication",input.path("authentication"));
        var generated=generator.generate("NeoFlow Server",prepared,source);
        assertEquals(0,generated.path("summary").path("CRUD").asInt());assertEquals(0,generated.path("summary").path("부가기능").asInt());
        assertEquals(133,generated.path("summary").path("주요기능").asInt());assertEquals(1,generated.path("summary").path("로그인").asInt());
        var override=features.get(0).deepCopy();((ObjectNode)override).put("featureType","CRUD");assertEquals("CRUD",generator.classify(override));
        audit.put("method","Actual MenuTcGenerator.classify + AuthenticationTcContext.prepare; offline draft replay; no approval or server TC creation")
            .put("menuJobId",imported.path("job").path("jobId").asText()).put("functionCount",functionCount)
            .put("missingClassification",missingClassification).put("includedFunctions",features.size()).put("placementReviewFunctions",functionCount-features.size())
            .put("injectedLoginFeatures",prepared.size()-features.size()).put("projectedTcCount",generated.path("caseCount").asInt());
        audit.set("summary",generated.path("summary"));
        Files.writeString(dir.resolve("classification-audit.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(audit));
        var csv=new StringBuilder("기능명,메뉴 분류,TC 기본 분류\n");
        for(var detail:details)csv.append('"').append(detail.path("functionName").asText().replace("\"","\"\"")).append("\",미지정,").append(detail.path("defaultTcClassification").asText()).append('\n');
        Files.writeString(dir.resolve("분류확인.csv"),"\uFEFF"+csv);
    }
}
