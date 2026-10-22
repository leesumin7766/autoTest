ALTER TABLE test_execution_runs DROP CONSTRAINT test_execution_runs_status_check;
ALTER TABLE test_execution_runs ADD CONSTRAINT test_execution_runs_status_check CHECK
  (status IN ('PREPARED','WAITING_EXECUTOR','RUNNING','COMPLETED','PARTIAL','FAILED','INTERRUPTED','CANCELED'));
ALTER TABLE test_execution_runs ADD COLUMN attempt UUID;
DROP INDEX test_execution_one_waiting;
CREATE UNIQUE INDEX test_execution_one_active ON test_execution_runs(submission_id) WHERE status IN ('WAITING_EXECUTOR','RUNNING');
