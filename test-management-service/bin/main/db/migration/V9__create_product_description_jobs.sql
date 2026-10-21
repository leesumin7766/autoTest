ALTER TABLE submission_ai_deliveries
    ADD COLUMN verified_document_digest CHAR(64);

-- Delivered sets cannot have their documents replaced, so the current documents are the verified version.
UPDATE submission_ai_deliveries d
SET verified_document_digest = (
    SELECT encode(sha256(convert_to(string_agg(
        f.role || ':' || f.file_id::text || ':' || encode(sha256(convert_to(f.extracted_text, 'UTF8')), 'hex'),
        E'\n' ORDER BY f.role), 'UTF8')), 'hex')
    FROM submission_files f
    WHERE f.submission_id = d.submission_id)
WHERE d.status = 'DELIVERED';

CREATE TABLE product_description_jobs (
    job_id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES submissions (submission_id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 1,
    error_code VARCHAR(60),
    error_message TEXT,
    input_digest CHAR(64) NOT NULL,
    preflight_decision VARCHAR(30) NOT NULL,
    generation_mode VARCHAR(10),
    generation_label TEXT,
    template_id VARCHAR(80),
    template_version VARCHAR(40),
    template_snapshot JSONB,
    content JSONB,
    output_format VARCHAR(10) NOT NULL DEFAULT 'PDF',
    output_path TEXT,
    output_filename TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_pdj_status CHECK (status IN
        ('GENERATING_CONTENT', 'RENDERING', 'COMPLETED', 'CONTENT_FAILED', 'RENDER_FAILED', 'CANCELED'))
);

-- At most one active job per submission, enforced by the database for concurrent requests.
CREATE UNIQUE INDEX uq_pdj_active_per_submission ON product_description_jobs (submission_id)
    WHERE status IN ('GENERATING_CONTENT', 'RENDERING');

CREATE INDEX idx_pdj_submission_created ON product_description_jobs (submission_id, created_at DESC);
