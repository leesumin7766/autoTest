CREATE TABLE manual_review_decisions (
    job_id uuid NOT NULL REFERENCES manual_review_jobs(job_id) ON DELETE CASCADE,
    candidate_id varchar(64) NOT NULL,
    decision varchar(16) NOT NULL CHECK(decision IN ('CONFIRMED','DISMISSED')),
    note varchar(500),
    decided_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(job_id,candidate_id)
);
