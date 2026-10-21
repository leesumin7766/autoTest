ALTER TABLE submission_legacy_file_archive
    ADD COLUMN legacy_failure_reason TEXT;

UPDATE submission_legacy_file_archive archive
SET legacy_failure_reason = submission.failure_reason
FROM submissions submission
WHERE archive.submission_id = submission.submission_id;

UPDATE submissions submission
SET status = CASE
    WHEN EXISTS (
        SELECT 1 FROM submission_files document
        WHERE document.submission_id = submission.submission_id
          AND document.status = 'FAILED'
    ) THEN 'FAILED'
    WHEN (
        SELECT count(DISTINCT document.role)
        FROM submission_files document
        WHERE document.submission_id = submission.submission_id
          AND document.role IN ('AGREEMENT', 'FUNCTION_LIST', 'MANUAL')
          AND document.status = 'PARSED'
          AND btrim(document.extracted_text) <> ''
    ) = 3 THEN 'PARSED'
    ELSE 'UPLOADED'
END;

ALTER TABLE submissions
    DROP COLUMN failure_reason;