ALTER TABLE playwright_crawl_pages ADD COLUMN screen_state JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE playwright_crawl_pages ADD COLUMN nav_transition JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE playwright_crawl_jobs ADD COLUMN crawl_result JSONB;
