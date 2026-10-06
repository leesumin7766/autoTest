ALTER TABLE product_description_jobs
    ADD COLUMN completed_chunks INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_chunks INTEGER NOT NULL DEFAULT 0;

ALTER TABLE product_description_jobs
    DROP CONSTRAINT ck_pdj_status;

ALTER TABLE product_description_jobs
    ADD CONSTRAINT ck_pdj_status CHECK (status IN
        ('GENERATING_CONTENT', 'RENDERING', 'COMPLETED', 'CONTENT_FAILED', 'CONTENT_PAUSED',
         'RENDER_FAILED', 'CANCELED'));
