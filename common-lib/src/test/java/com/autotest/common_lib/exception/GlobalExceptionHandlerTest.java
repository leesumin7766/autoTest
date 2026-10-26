package com.autotest.common_lib.exception;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class GlobalExceptionHandlerTest {
    @RestController static class Controller {
        @GetMapping("/unexpected") String unexpected(){throw new IllegalStateException("jdbc:postgresql://private password=secret /app/runtime");}
        @GetMapping("/business") String business(){throw new CommonException(CommonErrorCode.STATE_CONFLICT);}
        @GetMapping("/invalid") String invalid(){throw new IllegalArgumentException("internalField=secret");}
        @GetMapping("/size") String size(){throw new MaxUploadSizeExceededException(1);}
        @GetMapping("/typed") String typed(@RequestParam int count){return "ok";}
    }
    @Test void safeResponseHasStableCodeAndTraceWithoutInternalDetail() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new Controller()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/unexpected")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.traceId",matchesPattern("[0-9a-f-]{36}"))).andExpect(header().exists("X-Trace-Id"))
            .andExpect(content().string(not(containsString("secret")))).andExpect(content().string(not(containsString("jdbc:"))));
        mvc.perform(get("/business")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mvc.perform(get("/invalid")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(CommonErrorCode.INVALID_INPUT_VALUE.getMessage()));
        mvc.perform(get("/size")).andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        mvc.perform(get("/typed").param("count","secret")).andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("secret"))));
    }
}
