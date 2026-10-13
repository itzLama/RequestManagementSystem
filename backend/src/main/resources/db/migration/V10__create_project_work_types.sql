CREATE TABLE project_work_types (
    work_type_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    work_type_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_work_types_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE RESTRICT,
    CONSTRAINT project_work_types_name_not_blank
        CHECK (NULLIF(BTRIM(work_type_name), '') IS NOT NULL)
);

CREATE UNIQUE INDEX uq_project_work_types_project_name_ci
    ON project_work_types(project_id, LOWER(BTRIM(work_type_name)));

CREATE INDEX idx_project_work_types_project_active_name
    ON project_work_types(project_id, is_active, work_type_name, work_type_id);

INSERT INTO project_work_types(project_id, work_type_name)
SELECT p.project_id, defaults.work_type_name
FROM projects p
CROSS JOIN (VALUES ('Task'), ('Bug'), ('Improvement')) AS defaults(work_type_name);

ALTER TABLE requests
    ADD COLUMN project_work_type_id BIGINT;

UPDATE requests r
SET project_work_type_id = pwt.work_type_id
FROM project_work_types pwt
WHERE r.project_id = pwt.project_id
  AND LOWER(pwt.work_type_name) = LOWER(
      CASE r.work_type
          WHEN 'TASK' THEN 'Task'
          WHEN 'BUG' THEN 'Bug'
          WHEN 'IMPROVEMENT' THEN 'Improvement'
      END
  );

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM requests
        WHERE project_id IS NOT NULL AND project_work_type_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Project Work Type migration incomplete: a Project Request was not mapped.';
    END IF;
END
$$;

ALTER TABLE requests
    DROP CONSTRAINT requests_scope_work_type_check,
    ADD CONSTRAINT fk_requests_project_work_type
        FOREIGN KEY (project_work_type_id)
        REFERENCES project_work_types(work_type_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT requests_scope_work_type_check
        CHECK (
            (
                project_id IS NULL
                AND project_work_type_id IS NULL
                AND type_id IS NOT NULL
            )
            OR
            (
                project_id IS NOT NULL
                AND project_work_type_id IS NOT NULL
                AND type_id IS NULL
            )
        );

CREATE INDEX idx_requests_project_work_type_id
    ON requests(project_work_type_id);

ALTER TABLE requests DROP COLUMN work_type;
