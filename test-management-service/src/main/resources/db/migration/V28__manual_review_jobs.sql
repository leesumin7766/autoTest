CREATE TABLE manual_review_jobs (
    job_id uuid PRIMARY KEY,
    submission_id uuid NOT NULL REFERENCES submissions(submission_id),
    member_id bigint NOT NULL,
    manual_file_id uuid NOT NULL,
    filename text NOT NULL,
    document_hash varchar(64) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('RUNNING','COMPLETED','PARTIAL','FAILED','INTERRUPTED')),
    as_of date NOT NULL,
    output jsonb,
    error_code varchar(60),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
-- One CPU-heavy analysis at a time across the single TMS instance.
CREATE UNIQUE INDEX manual_review_one_active ON manual_review_jobs ((true)) WHERE status='RUNNING';
CREATE INDEX manual_review_submission_history ON manual_review_jobs (submission_id, member_id, created_at DESC);
