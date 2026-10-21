package com.autotest.test_management_service.application.testcase;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.crawler.PlaywrightSetupService;
import com.autotest.test_management_service.domain.submission.*;
import com.autotest.test_management_service.domain.vo.MemberId;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuTcServiceTest {
    final ObjectMapper mapper=new ObjectMapper();
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final SubmissionRepository submissions=mock(SubmissionRepository.class);
    final FunctionalMenuService menus=mock(FunctionalMenuService.class);
    final PlaywrightSetupService setups=mock(PlaywrightSetupService.class);
    final MenuTcService service=new MenuTcService(jdbc,mapper,submissions,menus,setups,new MenuTcGenerator(mapper),new MenuTcWorkbook(mapper));
    @Test void nonOwnerCannotReadOrGenerateOrDownloadAnySet() {
        UUID id=UUID.randomUUID();var submission=Submission.reconstitute(SubmissionId.of(id),new MemberId(1L),new ProductId(1L),SubmissionStatus.PARSED,Instant.now());
        when(submissions.findById(SubmissionId.of(id))).thenReturn(Optional.of(submission));
        when(submissions.findByIdForUpdate(SubmissionId.of(id))).thenReturn(Optional.of(submission));
        assertEquals("FORBIDDEN",assertThrows(ProductDescriptionException.class,()->service.status(id,2,null)).code());
        assertEquals("FORBIDDEN",assertThrows(ProductDescriptionException.class,()->service.create(id,2,null,Map.of())).code());
        assertEquals("FORBIDDEN",assertThrows(ProductDescriptionException.class,()->service.download(id,2,UUID.randomUUID())).code());
        verifyNoInteractions(jdbc,menus,setups);
    }
    @Test void unapprovedOrStaleSourceDoesNotWriteAnyTcSet() {
        UUID id=UUID.randomUUID(),job=UUID.randomUUID();var submission=Submission.reconstitute(SubmissionId.of(id),new MemberId(1L),new ProductId(1L),SubmissionStatus.PARSED,Instant.now());
        when(submissions.findByIdForUpdate(SubmissionId.of(id))).thenReturn(Optional.of(submission));
        var response=mapper.createObjectNode();response.putObject("job").put("status","DRAFT");
        when(menus.status(id,1,job)).thenReturn(response);
        assertEquals("TC_MENU_APPROVAL_REQUIRED",assertThrows(ProductDescriptionException.class,()->service.create(id,1,job,Map.of())).code());
        ((ObjectNode)response.path("job")).put("status","APPROVED").put("stale",true);
        assertEquals("TC_MENU_NOT_CURRENT",assertThrows(ProductDescriptionException.class,()->service.create(id,1,job,Map.of())).code());
        verifyNoInteractions(jdbc);
    }
    @Test void onlyIncludedFunctionRowsAreUsedAndOverridesCannotInjectFunctions() throws Exception {
        var menu=mapper.readTree("""
            {"productName":"제품","targetUrl":"https://example.com","output":{"rows":[
            {"rowId":"feature","included":true,"functionName":"조회","menuDescription":"목록 조회 기능","menuPath":["관리"],"evidence":[],"matchedMenuKeys":["key"]},
            {"rowId":"excluded","included":false,"functionName":"제외"},
            {"rowId":"structure","included":true,"rowType":"MENU_OBSERVATION","functionName":"메뉴"}]}}
            """);
        var rows=service.features(menu,Map.of("feature","부가기능"));assertEquals(1,rows.size());
        assertEquals(List.of("제품","관리","조회"),mapper.convertValue(rows.get(0).path("featurePath"),List.class));
        assertEquals("부가기능",rows.get(0).path("featureType").asText());
        assertThrows(ProductDescriptionException.class,()->service.features(menu,Map.of("excluded","주요기능")));
        assertThrows(ProductDescriptionException.class,()->service.features(menu,Map.of("injected","로그인")));
    }
}
