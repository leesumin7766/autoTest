CREATE TABLE menu_tree_approvals (
    submission_id UUID PRIMARY KEY REFERENCES submissions(submission_id),
    member_id BIGINT NOT NULL,
    manual_file_id UUID NOT NULL,
    document_digest CHAR(64) NOT NULL,
    tree_status VARCHAR(24) NOT NULL CHECK (tree_status IN ('DRAFT_NEEDS_REVIEW', 'APPROVED')),
    tree_json JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
