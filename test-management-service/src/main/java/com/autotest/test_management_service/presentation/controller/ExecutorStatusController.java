package com.autotest.test_management_service.presentation.controller;

import com.autotest.test_management_service.application.testcase.ExecutionReadinessService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

/** Lists only the requesting member's jobs. Cancellation uses the existing ownership-checked APIs. */
@RestController
@RequiredArgsConstructor
public class ExecutorStatusController {
    private final JdbcTemplate jdbc;
    private final ExecutionReadinessService readiness;

    @GetMapping("/api/crawl-agents/local/status")
    public ResponseEntity<?> status(@RequestHeader("X-Member-Id") long member) {
        var jobs=jdbc.query("""
            SELECT job_id AS id,submission_id,'CRAWL' AS kind,purpose,status,created_at,
                   page_count AS count FROM playwright_crawl_jobs
            WHERE member_id=? AND execution_mode='LOCAL' AND status IN ('QUEUED','RUNNING')
            UNION ALL
            SELECT run_id,submission_id,'EXECUTION','TEST',status,created_at,0
            FROM test_execution_runs WHERE member_id=? AND status IN ('WAITING_EXECUTOR','RUNNING')
            ORDER BY created_at
            """,(rs,n)-> {
                var job=new LinkedHashMap<String,Object>();
                job.put("id",rs.getString("id")); job.put("submissionId",rs.getString("submission_id"));
                job.put("kind",rs.getString("kind"));job.put("purpose",rs.getString("purpose"));
                job.put("status",rs.getString("status"));job.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());
                job.put("pageCount",rs.getInt("count"));return job;
            },member,member);
        var state=readiness.status();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
            "connected",state.path("browserConnected").asBoolean(),"busy",state.path("executorBusy").asBoolean(),"jobs",jobs));
    }
}
