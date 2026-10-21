CREATE TABLE submission_files (
    file_id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES submissions (submission_id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_format VARCHAR(20) NOT NULL,
    original_filename TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    stored_path TEXT NOT NULL,
    extracted_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_submission_files_role UNIQUE (submission_id, role)
);

CREATE INDEX idx_submission_files_submission_id ON submission_files (submission_id);