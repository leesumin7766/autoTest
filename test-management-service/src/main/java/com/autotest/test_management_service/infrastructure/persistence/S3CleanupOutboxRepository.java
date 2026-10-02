package com.autotest.test_management_service.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.autotest.test_management_service.domain.submission.StoredPath;

@Repository
public class S3CleanupOutboxRepository {
    private final JdbcTemplate jdbcTemplate;

    public S3CleanupOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void schedule(StoredPath storedPath) {
        jdbcTemplate.update("""
                INSERT INTO s3_cleanup_outbox (cleanup_id, stored_path)
                VALUES (?, ?)
                ON CONFLICT (stored_path) DO NOTHING
                """, UUID.randomUUID(), storedPath.value());
    }

    @Transactional
    public List<CleanupJob> claimDue(int limit) {
        List<CleanupJob> due = jdbcTemplate.query("""
                SELECT cleanup_id, stored_path, attempts
                FROM s3_cleanup_outbox
                WHERE completed_at IS NULL AND next_attempt_at <= now()
                ORDER BY requested_at, cleanup_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """, (resultSet, rowNumber) -> new CleanupJob(
                resultSet.getObject("cleanup_id", UUID.class),
                new StoredPath(resultSet.getString("stored_path")),
                resultSet.getInt("attempts") + 1), limit);

        for (CleanupJob job : due) {
            long retrySeconds = Math.min(3600L, 1L << Math.min(job.attempts(), 11));
            jdbcTemplate.update("""
                    UPDATE s3_cleanup_outbox
                    SET attempts = ?, next_attempt_at = ?
                    WHERE cleanup_id = ? AND completed_at IS NULL
                    """, job.attempts(), Instant.now().plusSeconds(retrySeconds), job.cleanupId());
        }
        return due;
    }

    @Transactional(readOnly = true)
    public boolean isReferenced(StoredPath storedPath) {
        Boolean referenced = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM submission_files WHERE stored_path = ?)",
                Boolean.class, storedPath.value());
        return Boolean.TRUE.equals(referenced);
    }

    @Transactional
    public void markCompleted(UUID cleanupId, String reason) {
        jdbcTemplate.update("""
                UPDATE s3_cleanup_outbox
                SET completed_at = now(), completion_reason = ?, last_error_code = NULL
                WHERE cleanup_id = ? AND completed_at IS NULL
                """, reason, cleanupId);
    }

    @Transactional
    public void markFailed(UUID cleanupId, String errorCode) {
        jdbcTemplate.update("""
                UPDATE s3_cleanup_outbox
                SET last_error_code = ?
                WHERE cleanup_id = ? AND completed_at IS NULL
                """, errorCode, cleanupId);
    }

    public record CleanupJob(UUID cleanupId, StoredPath storedPath, int attempts) {
    }
}