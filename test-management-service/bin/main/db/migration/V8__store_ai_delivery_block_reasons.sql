ALTER TABLE submission_ai_deliveries
    ADD COLUMN block_reasons JSONB,
    ADD COLUMN blocked_at TIMESTAMPTZ;
