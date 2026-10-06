-- Keep inference resource usage bounded per member across different submissions.
CREATE UNIQUE INDEX uq_pdj_active_per_member ON product_description_jobs (member_id)
    WHERE status IN ('GENERATING_CONTENT', 'RENDERING');
