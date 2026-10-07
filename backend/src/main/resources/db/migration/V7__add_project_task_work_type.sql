ALTER TABLE requests
    ADD COLUMN work_type VARCHAR(20);

UPDATE requests
SET work_type = 'TASK'
WHERE project_id IS NOT NULL;

ALTER TABLE requests
    ALTER COLUMN type_id DROP NOT NULL;

UPDATE requests
SET type_id = NULL
WHERE project_id IS NOT NULL;

ALTER TABLE requests
    ADD CONSTRAINT requests_scope_work_type_check
        CHECK (
            (project_id IS NULL AND work_type IS NULL AND type_id IS NOT NULL)
            OR
            (project_id IS NOT NULL AND work_type IN ('TASK', 'BUG', 'IMPROVEMENT') AND type_id IS NULL)
        );
