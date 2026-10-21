CREATE TABLE tc_generation_jobs (
    job_id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES submissions(submission_id),
    member_id BIGINT NOT NULL,
    input_digest CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('GENERATING', 'COMPLETED', 'FAILED', 'CANCELED')),
    result JSONB,
    error_code VARCHAR(64),
    completed_chunks INTEGER NOT NULL DEFAULT 0,
    total_chunks INTEGER NOT NULL DEFAULT 0,
    approved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX tc_generation_active_submission ON tc_generation_jobs(submission_id) WHERE status = 'GENERATING';
CREATE UNIQUE INDEX tc_generation_active_member ON tc_generation_jobs(member_id) WHERE status = 'GENERATING';
CREATE INDEX tc_generation_latest ON tc_generation_jobs(submission_id, approved_at DESC);
