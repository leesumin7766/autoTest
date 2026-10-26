package com.autotest.test_management_service.exception;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.presentation.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class TestManagementExceptionTest {
    @Test void legacyExceptionsKeepCodeAndStatusButCannotExposeArbitraryDetails() {
        var ex=new ProductDescriptionException(HttpStatus.SERVICE_UNAVAILABLE,"NEW_SERVICE_ERROR","password=secret jdbc:private");
        var response=new GlobalExceptionHandler().handleCommon(ex);
        assertEquals(503,response.getStatusCode().value());assertEquals("NEW_SERVICE_ERROR",response.getBody().code());
        assertFalse(response.getBody().message().contains("secret"));assertNotNull(response.getBody().traceId());
    }
    @Test void allCodesAreUniqueAndSafeAndDoNotEmbedHttpNumbersInMessages() {
        var codes=new java.util.HashSet<String>();for(var code:TestManagementErrorCode.values()) {
            assertTrue(codes.add(code.getCode()));assertFalse(code.getMessage().isBlank());assertFalse(code.getMessage().matches("^[0-9]{3}:.*"));
        }
    }
}
