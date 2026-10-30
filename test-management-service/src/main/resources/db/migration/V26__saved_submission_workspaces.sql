-- A logical workspace reuses the existing submission UUID; artifacts stay in their current stores.
CREATE TABLE submission_workspaces (
    submission_id uuid PRIMARY KEY REFERENCES submissions(submission_id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    last_page varchar(24) NOT NULL DEFAULT 'documents',
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT workspace_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT workspace_page_valid CHECK (last_page IN ('documents','crawl','menu','tc-generate','execution','defects','reports'))
);

-- Existing submissions remain recoverable without re-uploading or regenerating anything.
INSERT INTO submission_workspaces(submission_id,name)
SELECT submission_id,'시험 작업 ' || left(submission_id::text,8) FROM submissions;
