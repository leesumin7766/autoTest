CREATE TABLE functional_menu_jobs (
    job_id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES submissions(submission_id),
    member_id BIGINT NOT NULL,
    crawl_job_id UUID NOT NULL REFERENCES playwright_crawl_jobs(job_id),
    document_digest CHAR(64) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    rules_version VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK(status IN ('GENERATING','DRAFT','APPROVED','FAILED','CANCELED')),
    generation_mode VARCHAR(20) NOT NULL,
    revision INT NOT NULL DEFAULT 0,
    snapshot JSONB NOT NULL,
    output JSONB,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX functional_menu_one_active ON functional_menu_jobs(submission_id) WHERE status='GENERATING';
CREATE INDEX functional_menu_history ON functional_menu_jobs(submission_id,created_at DESC);
