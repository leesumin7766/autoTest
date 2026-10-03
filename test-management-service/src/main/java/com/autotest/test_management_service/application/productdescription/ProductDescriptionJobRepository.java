package com.autotest.test_management_service.application.productdescription;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Every transition is a conditional UPDATE on (job, attempt, expected status), so whichever of
 * cancel, completion, failure, or recovery commits first wins and later writers affect zero rows.
 */
@Repository
public class ProductDescriptionJobRepository {
    private static final String ACTIVE = "('GENERATING_CONTENT', 'RENDERING')";
    private static final String COLUMNS = """
            job_id, submission_id, member_id, status, attempt, error_code, error_message, input_digest,
            preflight_decision, generation_mode, generation_label, template_id, template_version, output_format,
            output_path, output_filename, (content IS NOT NULL) AS has_content, created_at, updated_at, completed_at
            """;
    private static final RowMapper<ProductDescriptionJob> MAPPER = (rs, row) -> new ProductDescriptionJob(
            rs.getObject("job_id", UUID.class), rs.getObject("submission_id", UUID.class), rs.getLong("member_id"),
            rs.getString("status"), rs.getInt("attempt"), rs.getString("error_code"), rs.getString("error_message"),
            rs.getString("input_digest").trim(), rs.getString("preflight_decision"), rs.getString("generation_mode"),
            rs.getString("generation_label"), rs.getString("template_id"), rs.getString("template_version"),
            rs.getString("output_format"), rs.getString("output_path"), rs.getString("output_filename"),
            rs.getBoolean("has_content"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public ProductDescriptionJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID jobId, UUID submissionId, long memberId, String inputDigest, String decision) {
        jdbcTemplate.update("""
                INSERT INTO product_description_jobs
                    (job_id, submission_id, member_id, status, input_digest, preflight_decision)
                VALUES (?, ?, ?, 'GENERATING_CONTENT', ?, ?)
                """, jobId, submissionId, memberId, inputDigest, decision);
    }

    public Optional<ProductDescriptionJob> find(UUID jobId) {
        return first("SELECT " + COLUMNS + " FROM product_description_jobs WHERE job_id = ?", jobId);
    }

    public Optional<ProductDescriptionJob> findActive(UUID submissionId) {
        return first("SELECT " + COLUMNS + " FROM product_description_jobs WHERE submission_id = ? AND status IN "
                + ACTIVE, submissionId);
    }

    public Optional<ProductDescriptionJob> findLatest(UUID submissionId) {
        return first("SELECT " + COLUMNS + " FROM product_description_jobs WHERE submission_id = ? "
                + "ORDER BY created_at DESC LIMIT 1", submissionId);
    }

    public Optional<ProductDescriptionJob> findLatestCompleted(UUID submissionId) {
        return first("SELECT " + COLUMNS + " FROM product_description_jobs WHERE submission_id = ? "
                + "AND status = 'COMPLETED' ORDER BY created_at DESC LIMIT 1", submissionId);
    }

    public boolean markContentReady(UUID jobId, int attempt, String snapshotJson, String contentJson, String mode,
                                    String label, String templateId, String templateVersion) {
        return jdbcTemplate.update("""
                UPDATE product_description_jobs
                SET status = 'RENDERING', template_snapshot = ?::jsonb, content = ?::jsonb, generation_mode = ?,
                    generation_label = ?, template_id = ?, template_version = ?, updated_at = now()
                WHERE job_id = ? AND attempt = ? AND status = 'GENERATING_CONTENT'
                """, snapshotJson, contentJson, mode, label, templateId, templateVersion, jobId, attempt) == 1;
    }

    public boolean markCompleted(UUID jobId, int attempt, String outputPath, String outputFilename) {
        return jdbcTemplate.update("""
                UPDATE product_description_jobs
                SET status = 'COMPLETED', output_path = ?, output_filename = ?, error_code = NULL,
                    error_message = NULL, updated_at = now(), completed_at = now()
                WHERE job_id = ? AND attempt = ? AND status = 'RENDERING'
                """, outputPath, outputFilename, jobId, attempt) == 1;
    }

    public boolean markFailed(UUID jobId, int attempt, String fromStatus, String toStatus, String code,
                              String message) {
        return jdbcTemplate.update("""
                UPDATE product_description_jobs
                SET status = ?, error_code = ?, error_message = ?, updated_at = now(), completed_at = now()
                WHERE job_id = ? AND attempt = ? AND status = ?
                """, toStatus, code, message, jobId, attempt, fromStatus) == 1;
    }

    public boolean cancel(UUID jobId) {
        return jdbcTemplate.update("""
                UPDATE product_description_jobs
                SET status = 'CANCELED', error_code = 'CANCELED', error_message = '사용자가 생성을 중단했습니다.',
                    updated_at = now(), completed_at = now()
                WHERE job_id = ? AND status IN """ + ACTIVE, jobId) == 1;
    }

    public void touch(UUID jobId, int attempt) {
        jdbcTemplate.update("""
                UPDATE product_description_jobs SET updated_at = now()
                WHERE job_id = ? AND attempt = ? AND status IN """ + ACTIVE, jobId, attempt);
    }

    /** Re-opens only the PDF stage of a job whose content was already generated; returns the new attempt. */
    public Optional<Integer> beginRerender(UUID jobId) {
        List<Integer> attempts = jdbcTemplate.query("""
                UPDATE product_description_jobs
                SET status = 'RENDERING', attempt = attempt + 1, error_code = NULL, error_message = NULL,
                    completed_at = NULL, updated_at = now()
                WHERE job_id = ? AND status = 'RENDER_FAILED' AND content IS NOT NULL
                RETURNING attempt
                """, (rs, row) -> rs.getInt(1), jobId);
        return attempts.stream().findFirst();
    }

    public record DeliveryVerdict(String status, String preflightJson, String documentDigest) {
    }

    public Optional<DeliveryVerdict> findDeliveryVerdict(UUID submissionId) {
        return jdbcTemplate.query("""
                SELECT status, preflight_result::text, verified_document_digest
                FROM submission_ai_deliveries WHERE submission_id = ?
                """, (rs, row) -> new DeliveryVerdict(rs.getString(1), rs.getString(2),
                rs.getString(3) == null ? null : rs.getString(3).trim()), submissionId).stream().findFirst();
    }

    public record StoredContent(String snapshotJson, String contentJson) {
    }

    public Optional<StoredContent> loadContent(UUID jobId) {
        return jdbcTemplate.query("""
                SELECT template_snapshot::text, content::text FROM product_description_jobs
                WHERE job_id = ? AND content IS NOT NULL
                """, (rs, row) -> new StoredContent(rs.getString(1), rs.getString(2)), jobId).stream().findFirst();
    }

    /** Jobs whose worker stopped heartbeating can never finish; content-ready ones may be re-rendered. */
    public int recoverStale(Duration lease) {
        return recover("AND updated_at < now() - (?::double precision * interval '1 second')",
                (double) lease.toSeconds());
    }

    public int recoverAllActive() {
        return recover("");
    }

    private int recover(String condition, Object... arguments) {
        return jdbcTemplate.update("""
                UPDATE product_description_jobs
                SET status = CASE WHEN status = 'RENDERING' AND content IS NOT NULL
                                  THEN 'RENDER_FAILED' ELSE 'CONTENT_FAILED' END,
                    error_code = 'INTERRUPTED',
                    error_message = '서버 재시작 또는 작업 중단으로 생성이 완료되지 못했습니다.',
                    updated_at = now(), completed_at = now()
                WHERE status IN """ + ACTIVE + " " + condition, arguments);
    }

    private Optional<ProductDescriptionJob> first(String sql, Object... arguments) {
        return jdbcTemplate.query(sql, MAPPER, arguments).stream().findFirst();
    }
}
