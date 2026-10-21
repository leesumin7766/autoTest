ALTER TABLE playwright_crawl_setups
    DROP CONSTRAINT playwright_crawl_setups_login_method_check;
ALTER TABLE playwright_crawl_setups
    ADD CONSTRAINT playwright_crawl_setups_login_method_check
        CHECK (login_method IN ('NONE', 'FORM_AUTO', 'BASIC_AUTH', 'MANUAL', 'SSO', 'OTHER')),
    ADD COLUMN automation_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN password_ciphertext TEXT;
