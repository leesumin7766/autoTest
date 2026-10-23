CREATE TABLE test_execution_evidence (
    run_id uuid NOT NULL REFERENCES test_execution_runs(run_id) ON DELETE CASCADE,
    case_id varchar(128) NOT NULL,
    attempt uuid NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(run_id,case_id)
);
