ALTER TABLE playwright_crawl_jobs
    ALTER COLUMN agent_id DROP NOT NULL,
    ADD COLUMN execution_mode VARCHAR(10) NOT NULL DEFAULT 'REMOTE'
        CHECK (execution_mode IN ('LOCAL','REMOTE')),
    ADD COLUMN purpose VARCHAR(12) NOT NULL DEFAULT 'CRAWL'
        CHECK (purpose IN ('CONNECTION','LOGIN','CRAWL')),
    ADD COLUMN check_result JSONB;
CREATE UNIQUE INDEX crawl_one_active_local ON playwright_crawl_jobs((1))
    WHERE execution_mode='LOCAL' AND status IN ('QUEUED','RUNNING');
