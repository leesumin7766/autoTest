package com.autotest.test_management_service.presentation.controller;

import com.autotest.test_management_service.application.service.SubmissionService;
import com.autotest.test_management_service.domain.submission.ProductId;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.vo.MemberId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubmissionService submissionService;

    @Test
    void submitReturns200AndSubmissionId() throws Exception {
        SubmissionId mockSubmissionId = SubmissionId.generate();
        when(submissionService.submit(any(), eq(new MemberId(1L)), eq(new ProductId(100L))))
                .thenReturn(mockSubmissionId);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "dummy pdf content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/submissions")
                        .file(file)
                        .param("productId", "100")
                        .header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(mockSubmissionId.value().toString()));

        verify(submissionService).submit(any(), eq(new MemberId(1L)), eq(new ProductId(100L)));
    }
}
