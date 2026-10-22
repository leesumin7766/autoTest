CREATE TABLE test_execution_runs (
    run_id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES submissions(submission_id),
    member_id BIGINT NOT NULL,
    tc_set_id UUID NOT NULL REFERENCES menu_tc_sets(set_id),
    status VARCHAR(40) NOT NULL CHECK (status IN ('PREPARED','WAITING_EXECUTOR','CANCELED')),
    snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX test_execution_run_history ON test_execution_runs(submission_id, created_at DESC);
CREATE UNIQUE INDEX test_execution_one_waiting ON test_execution_runs(submission_id) WHERE status='WAITING_EXECUTOR';
