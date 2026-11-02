ALTER TABLE manual_review_jobs DROP CONSTRAINT manual_review_jobs_status_check;
ALTER TABLE manual_review_jobs ADD CONSTRAINT manual_review_jobs_status_check
    CHECK(status IN ('RUNNING','PAUSING','PAUSED','COMPLETED','PARTIAL','FAILED','INTERRUPTED'));
ALTER TABLE manual_review_jobs
    ADD COLUMN attempt integer NOT NULL DEFAULT 1,
    ADD COLUMN total_pages integer CHECK(total_pages BETWEEN 1 AND 300),
    ADD COLUMN current_page integer NOT NULL DEFAULT 0,
    ADD COLUMN completed_pages integer NOT NULL DEFAULT 0,
    ADD COLUMN stage varchar(24) NOT NULL DEFAULT 'COUNTING',
    ADD COLUMN processor_version varchar(64),
    ADD COLUMN elapsed_ms bigint NOT NULL DEFAULT 0,
    ADD COLUMN attempt_started_at timestamptz,
    ADD COLUMN finished_at timestamptz;
UPDATE manual_review_jobs SET
    attempt_started_at=CASE WHEN status='RUNNING' THEN created_at ELSE NULL END,
    finished_at=CASE WHEN status='RUNNING' THEN NULL ELSE updated_at END,
    elapsed_ms=CASE WHEN status='RUNNING' THEN 0 ELSE GREATEST(0,EXTRACT(EPOCH FROM(updated_at-created_at))*1000)::bigint END,
    total_pages=CASE WHEN output->>'pageCount' IS NOT NULL THEN (output->>'pageCount')::integer ELSE NULL END,
    completed_pages=CASE WHEN status='COMPLETED' THEN COALESCE((output->>'pageCount')::integer,0) ELSE 0 END,
    stage=CASE WHEN status='RUNNING' THEN 'COUNTING' ELSE status END;
DROP INDEX manual_review_one_active;
CREATE UNIQUE INDEX manual_review_one_active ON manual_review_jobs ((true)) WHERE status IN ('RUNNING','PAUSING');

CREATE TABLE manual_review_pages (
    job_id uuid NOT NULL REFERENCES manual_review_jobs(job_id) ON DELETE CASCADE,
    page_no integer NOT NULL CHECK(page_no BETWEEN 1 AND 300),
    complete boolean NOT NULL,
    output jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(job_id,page_no)
);
