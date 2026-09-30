CREATE TABLE submissions (
    submission_id UUID PRIMARY KEY,
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    submission_type VARCHAR(20) NOT NULL,
    stored_path TEXT NOT NULL,
    extracted_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_submissions_member_id ON submissions (member_id);
CREATE INDEX idx_submissions_product_id ON submissions (product_id);