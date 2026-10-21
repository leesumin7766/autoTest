CREATE TABLE menu_tc_sets (
    set_id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES submissions(submission_id),
    member_id BIGINT NOT NULL,
    menu_job_id UUID NOT NULL REFERENCES functional_menu_jobs(job_id),
    menu_revision INT NOT NULL,
    input_digest CHAR(64) NOT NULL,
    template_version VARCHAR(80) NOT NULL,
    output JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (submission_id, input_digest)
);
CREATE INDEX menu_tc_sets_history ON menu_tc_sets(submission_id, created_at DESC);
