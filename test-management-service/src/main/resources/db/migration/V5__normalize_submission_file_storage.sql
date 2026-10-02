CREATE TABLE submission_legacy_file_archive (
    submission_id UUID PRIMARY KEY,
    legacy_submission_type VARCHAR(20) NOT NULL,
    legacy_stored_path TEXT NOT NULL,
    legacy_extracted_text TEXT NOT NULL,
    first_file_id UUID,
    first_file_role VARCHAR(30),
    preservation_reason VARCHAR(80) NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

WITH first_file AS (
    SELECT DISTINCT ON (submission_id)
        submission_id,
        file_id,
        role,
        file_type,
        stored_path,
        extracted_text
    FROM submission_files
    ORDER BY submission_id, uploaded_at, file_id
)
INSERT INTO submission_legacy_file_archive (
    submission_id,
    legacy_submission_type,
    legacy_stored_path,
    legacy_extracted_text,
    first_file_id,
    first_file_role,
    preservation_reason
)
SELECT
    s.submission_id,
    s.submission_type,
    s.stored_path,
    s.extracted_text,
    f.file_id,
    f.role,
    CASE
        WHEN f.submission_id IS NULL THEN 'NO_DOCUMENT_ROW_ROLE_FORMAT_UNKNOWN'
        WHEN s.submission_type::text IS DISTINCT FROM f.file_type
          OR s.stored_path IS DISTINCT FROM f.stored_path
          OR s.extracted_text IS DISTINCT FROM f.extracted_text
            THEN 'LEGACY_COPY_DIFFERS_FROM_FIRST_DOCUMENT'
        ELSE 'LEGACY_DUPLICATE_COPY'
    END
FROM submissions s
LEFT JOIN first_file f USING (submission_id);

UPDATE submissions s
SET status = CASE
        WHEN EXISTS (
            SELECT 1 FROM submission_files f
            WHERE f.submission_id = s.submission_id AND f.status = 'FAILED'
        ) THEN 'FAILED'
        WHEN (
            SELECT count(DISTINCT f.role)
            FROM submission_files f
            WHERE f.submission_id = s.submission_id
              AND f.role IN ('AGREEMENT', 'FUNCTION_LIST', 'MANUAL')
              AND f.status = 'PARSED'
              AND btrim(f.extracted_text) <> ''
        ) = 3 THEN 'PARSED'
        ELSE 'UPLOADED'
    END,
    failure_reason = (
        SELECT f.failure_reason
        FROM submission_files f
        WHERE f.submission_id = s.submission_id
          AND f.status = 'FAILED'
        ORDER BY f.uploaded_at, f.file_id
        LIMIT 1
    )
WHERE EXISTS (
    SELECT 1 FROM submission_files f WHERE f.submission_id = s.submission_id
);

CREATE TABLE s3_cleanup_outbox (
    cleanup_id UUID PRIMARY KEY,
    stored_path TEXT NOT NULL UNIQUE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error_code VARCHAR(100),
    completed_at TIMESTAMPTZ,
    completion_reason VARCHAR(40),
    CONSTRAINT ck_s3_cleanup_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_s3_cleanup_due
    ON s3_cleanup_outbox (next_attempt_at, requested_at)
    WHERE completed_at IS NULL;

ALTER TABLE submissions
    DROP COLUMN submission_type,
    DROP COLUMN stored_path,
    DROP COLUMN extracted_text;