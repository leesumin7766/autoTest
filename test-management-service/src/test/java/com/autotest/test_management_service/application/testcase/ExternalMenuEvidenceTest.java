package com.autotest.test_management_service.application.testcase;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExternalMenuEvidenceTest {
    final ObjectMapper mapper=new ObjectMapper();
    final UUID job=UUID.randomUUID();
    @Test void shortReferencesRestoreExactQuotesAndMenuKeysWithoutTrustingModelQuotes() throws Exception {
        var snapshot=mapper.readTree("""
            {"evidence":[{"id":"doc:uuid:0:1100","kind":"DOCUMENT","text":"원문 줄바꿈\\nCSV 로 내려받기"},
            {"id":"menu:longhash","kind":"MENU","text":"운영 로그","menuKey":"longhash","webPath":["운영","로그"]}]}
            """);
        var catalogue=ExternalMenuEvidence.catalogue(mapper,snapshot);
        assertEquals("E0001",catalogue.get(0).path("refId").asText());assertFalse(catalogue.toString().contains("longhash"));
        var input=mapper.readTree("""
            [{"menuPath":["운영","로그"],"functionName":"로그 조회","menuDescription":"로그를 조회하는 기능","location":"SIDEBAR",
            "evidenceRefIds":["E0001","E0002"],"evidence":[{"sourceId":"forged","quote":"forged"}],"reviewNotes":[]}]
            """);
        var rows=ExternalMenuEvidence.resolve(mapper,snapshot,job,job.toString(),input);
        assertEquals("원문 줄바꿈\nCSV 로 내려받기",rows.get(0).path("evidence").get(0).path("quote").asText());
        assertEquals("longhash",rows.get(0).path("matchedMenuKeys").get(0).asText());
        assertEquals("VALID",new FunctionalMenuValidator(mapper).importDraft(rows,snapshot).get(0).path("evidenceStatus").asText());
        assertThrows(ProductDescriptionException.class,()->ExternalMenuEvidence.resolve(mapper,snapshot,job,"other-job",input));
        assertThrows(ProductDescriptionException.class,()->ExternalMenuEvidence.resolve(mapper,snapshot,job,null,input));
    }
    @Test void unknownReferenceRemainsAWarningAndLegacyResponsesStillWork() throws Exception {
        var snapshot=mapper.readTree("{\"evidence\":[]}");
        var input=mapper.readTree("""
            [{"menuPath":[],"functionName":"미확인 기능","menuDescription":"정보를 조회하는 기능","location":"UNKNOWN","evidenceRefIds":["E9999"],"reviewNotes":[]}]
            """);
        var rows=ExternalMenuEvidence.resolve(mapper,snapshot,job,job.toString(),input);
        assertEquals("UNVERIFIED",new FunctionalMenuValidator(mapper).importDraft(rows,snapshot).get(0).path("evidenceStatus").asText());
        var legacy=mapper.readTree("[{\"evidence\":[],\"matchedMenuKeys\":[]}]");
        assertSame(legacy,ExternalMenuEvidence.resolve(mapper,snapshot,job,null,legacy));
    }
    @Test void segmentationPreservesEveryCharacterIncludingSurrogatePairs() {
        String text="가".repeat(239)+"😀"+"나".repeat(500);
        var snapshot=mapper.createObjectNode();snapshot.putArray("evidence").addObject().put("id","doc").put("kind","DOCUMENT").put("text",text);
        var parts=ExternalMenuEvidence.catalogue(mapper,snapshot);int offset=0;
        for(var part:parts){String quote=part.path("text").asText();assertEquals(quote,text.substring(offset,offset+quote.length()));assertFalse(Character.isHighSurrogate(quote.charAt(quote.length()-1)));if(offset+quote.length()==text.length()){offset=text.length();break;}offset+=quote.length()-40;if(Character.isLowSurrogate(text.charAt(offset)))offset--;}
        assertEquals(text.length(),offset);
    }
}
