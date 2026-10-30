ALTER TABLE submission_workspaces DROP CONSTRAINT workspace_page_valid;
ALTER TABLE submission_workspaces ADD CONSTRAINT workspace_page_valid
    CHECK (last_page IN ('documents','crawl','menu','tc-generate','execution','manual-review','defects','reports'));
