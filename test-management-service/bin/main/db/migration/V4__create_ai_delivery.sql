CREATE TABLE submission_ai_deliveries (
    submission_id UUID PRIMARY KEY REFERENCES submissions (submission_id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    delivered_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);