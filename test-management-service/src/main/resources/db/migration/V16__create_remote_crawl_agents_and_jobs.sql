CREATE TABLE crawl_agents (
    agent_id UUID PRIMARY KEY,
    member_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    secret_ciphertext TEXT NOT NULL,
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX crawl_agents_member ON crawl_agents(member_id);
CREATE TABLE crawl_agent_nonces (
    agent_id UUID NOT NULL REFERENCES crawl_agents(agent_id),
    nonce UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(agent_id, nonce)
);
CREATE TABLE playwright_crawl_jobs (
    job_id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES submissions(submission_id),
    member_id BIGINT NOT NULL,
    agent_id UUID NOT NULL REFERENCES crawl_agents(agent_id),
    status VARCHAR(16) NOT NULL CHECK(status IN ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELED')),
    setup_snapshot JSONB NOT NULL,
    password_ciphertext TEXT,
    document_digest CHAR(64) NOT NULL,
    claim_token UUID,
    lease_expires_at TIMESTAMPTZ,
    page_count INT NOT NULL DEFAULT 0,
    screenshot_bytes BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(80),
    stop_reason VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX crawl_one_active_submission ON playwright_crawl_jobs(submission_id) WHERE status IN ('QUEUED','RUNNING');
CREATE UNIQUE INDEX crawl_one_active_agent ON playwright_crawl_jobs(agent_id) WHERE status IN ('QUEUED','RUNNING');
CREATE TABLE playwright_crawl_pages (
    job_id UUID NOT NULL REFERENCES playwright_crawl_jobs(job_id) ON DELETE CASCADE,
    page_index INT NOT NULL,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    body_text TEXT NOT NULL,
    elements JSONB NOT NULL,
    links JSONB NOT NULL,
    screenshot BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(job_id,page_index)
);
