package com.autotest.test_management_service.presentation.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autotest.common_lib.exception.CommonErrorCode;
import com.autotest.common_lib.exception.CommonException;
import com.autotest.test_management_service.application.service.SubmissionWorkspaceService;
import com.autotest.test_management_service.application.service.SubmissionWorkspaceService.Workspace;
import com.autotest.test_management_service.application.testcase.ExecutionReviewService;
import com.autotest.test_management_service.application.testcase.TestExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HubV1Controller.class)
@TestPropertySource(properties = "HUB_MIN_VERSION=1.2.0")
class HubV1ControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SubmissionWorkspaceService workspaces;
    @MockitoBean TestExecutionService executions;
    @MockitoBean ExecutionReviewService reviews;
    final ObjectMapper mapper = new ObjectMapper();
    static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID RUN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    Workspace workspace() {
        return new Workspace(WS, 5, "결제 시험", "reports", Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z"),
                3, 3, "DELIVERED", 1, 2, 1, 4);
    }

    ObjectNode runStatus() throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"history":[{"runId":"%s","status":"COMPLETED","createdAt":"2026-11-03T00:00:00Z"}],
             "run":{"runId":"%s","tcSetId":"x","status":"RUNNING","createdAt":"c","updatedAt":"u","stale":true,"elapsedMs":1500,
                    "snapshot":{"caseCount":10,"items":[],"targetUrl":"https://secret.internal"},
                    "completedCount":6,"passCount":3,"failCount":1,"naCount":1,"unjudgedCount":5,"pendingCount":4,"errorCount":1,
                    "notExecutedCount":0,"reviewCount":1,"excludedCount":0}}""".formatted(RUN, RUN));
    }

    @Test void healthNeedsNoVersion() throws Exception {
        mvc.perform(get("/api/v1/hub/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.apiVersion").value("1")).andExpect(jsonPath("$.minHubVersion").value("1.2.0"));
    }

    @Test void versionIsRequiredAndOldHubsMustUpdate() throws Exception {
        mvc.perform(get("/api/v1/hub/workspaces").header("X-Member-Id", "7"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HUB_VERSION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("X-Hub-Version 헤더(x.y.z)가 필요합니다."));
        mvc.perform(get("/api/v1/hub/workspaces").header("X-Member-Id", "7").header("X-Hub-Version", "banana"))
                .andExpect(status().isBadRequest());
        for (String old : List.of("1.1.9", "0.9.0", "1.1.99"))
            mvc.perform(get("/api/v1/hub/workspaces").header("X-Member-Id", "7").header("X-Hub-Version", old))
                    .andExpect(status().is(426)).andExpect(jsonPath("$.code").value("HUB_UPDATE_REQUIRED"));
        when(workspaces.list(7, 0, 21)).thenReturn(List.of());
        for (String fine : List.of("1.2.0", "1.10.0", "2.0.0-beta"))
            mvc.perform(get("/api/v1/hub/workspaces").header("X-Member-Id", "7").header("X-Hub-Version", fine)).andExpect(status().isOk());
    }

    @Test void listUsesCallerMemberAndStableShape() throws Exception {
        when(workspaces.list(7, 0, 2)).thenReturn(List.of(workspace(), workspace()));
        mvc.perform(get("/api/v1/hub/workspaces?limit=1").header("X-Member-Id", "7").header("X-Hub-Version", "1.2.0"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.hasMore").value(true)).andExpect(jsonPath("$.workspaces.length()").value(1))
                .andExpect(jsonPath("$.workspaces[0].name").value("결제 시험"))
                .andExpect(jsonPath("$.workspaces[0].counts.runs").value(4))
                .andExpect(jsonPath("$.workspaces[0].webPath").value("/?member=7&workspace=" + WS + "&page=reports"));
        mvc.perform(get("/api/v1/hub/workspaces?limit=51").header("X-Member-Id", "7").header("X-Hub-Version", "1.2.0"))
                .andExpect(status().isBadRequest());
    }

    @Test void summaryMapsRunWithoutLeakingSnapshot() throws Exception {
        when(workspaces.get(WS, 7)).thenReturn(workspace());
        when(executions.status(WS, 7, null)).thenReturn(runStatus());
        mvc.perform(get("/api/v1/hub/workspaces/" + WS + "/summary").header("X-Member-Id", "7").header("X-Hub-Version", "1.2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestRun.active").value(true)).andExpect(jsonPath("$.latestRun.tcSetChanged").value(true))
                .andExpect(jsonPath("$.latestRun.counts.total").value(10)).andExpect(jsonPath("$.latestRun.counts.fail").value(1))
                .andExpect(jsonPath("$.latestRun.snapshot").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret.internal"))));
    }

    @Test void summaryWithoutRunsHasNullLatestRun() throws Exception {
        when(workspaces.get(WS, 7)).thenReturn(workspace());
        var empty = mapper.createObjectNode(); empty.putArray("history"); empty.putNull("run");
        when(executions.status(WS, 7, null)).thenReturn(empty);
        mvc.perform(get("/api/v1/hub/workspaces/" + WS + "/summary").header("X-Member-Id", "7").header("X-Hub-Version", "1.2.0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latestRun").isEmpty());
    }

    @Test void otherMembersWorkspaceIsNotFound() throws Exception {
        when(workspaces.get(WS, 8)).thenThrow(new CommonException(CommonErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get("/api/v1/hub/workspaces/" + WS + "/summary").header("X-Member-Id", "8").header("X-Hub-Version", "1.2.0"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(executions);
    }

    @Test void otherMembersRunsAndReportsAreNotFoundBeforeTouchingExecutions() throws Exception {
        when(workspaces.get(WS, 8)).thenThrow(new CommonException(CommonErrorCode.RESOURCE_NOT_FOUND));
        for (String path : List.of("/runs", "/runs/" + RUN, "/runs/" + RUN + "/report"))
            mvc.perform(get("/api/v1/hub/workspaces/" + WS + path).header("X-Member-Id", "8").header("X-Hub-Version", "1.2.0"))
                    .andExpect(status().isNotFound());
        verifyNoInteractions(executions, reviews);
    }

    @Test void runsRunAndReport() throws Exception {
        when(workspaces.get(WS, 7)).thenReturn(workspace());
        when(executions.status(WS, 7, null)).thenReturn(runStatus());
        when(executions.status(WS, 7, RUN)).thenReturn(runStatus());
        when(reviews.report(WS, 7, RUN)).thenReturn("# 보고서");
        mvc.perform(get("/api/v1/hub/workspaces/" + WS + "/runs").header("X-Member-Id", "7").header("X-Hub-Version", "1.2.0"))
                .andExpect(jsonPath("$.runs[0].runId").value(RUN.toString())).andExpect(jsonPath("$.runs[0].status").value("COMPLETED"));
        mvc.perform(get("/api/v1/hub/workspaces/" + WS + "/runs/" + RUN).header("X-Member-Id", "7").header("X-Hub-Version", "1.2.0"))
                .andExpect(jsonPath("$.counts.pass").value(3)).andExpect(jsonPath("$.webPath").value("/?member=7&workspace=" + WS + "&page=execution"));
        mvc.perform(get("/api/v1/hub/workspaces/" + WS + "/runs/" + RUN + "/report").header("X-Member-Id", "7").header("X-Hub-Version", "1.2.0"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(content().string("# 보고서"));
    }

    @Test void missingMemberHeaderIsRejected() throws Exception {
        mvc.perform(get("/api/v1/hub/workspaces").header("X-Hub-Version", "1.2.0")).andExpect(status().is4xxClientError());
        verify(workspaces, never()).list(anyLong(), anyInt(), anyInt());
    }
}
