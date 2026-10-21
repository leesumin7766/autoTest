CREATE TABLE playwright_crawl_setups (
    submission_id UUID PRIMARY KEY REFERENCES submissions(submission_id),
    member_id BIGINT NOT NULL,
    target_url TEXT NOT NULL,
    login_method VARCHAR(24) NOT NULL CHECK (login_method IN ('NONE', 'MANUAL', 'SSO', 'OTHER')),
    login_instructions TEXT NOT NULL DEFAULT '',
    test_account_label VARCHAR(160) NOT NULL DEFAULT '',
    crawl_scope_notes TEXT NOT NULL DEFAULT '',
    test_data_notes TEXT NOT NULL DEFAULT '',
    test_environment_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    document_digest CHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
