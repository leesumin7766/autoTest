ALTER TABLE playwright_crawl_pages ADD COLUMN navigation JSONB NOT NULL DEFAULT '[]'::jsonb;
