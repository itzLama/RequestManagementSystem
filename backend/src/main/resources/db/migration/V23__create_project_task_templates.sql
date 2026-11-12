-- Admin-managed, per-Project Task Templates (e.g. Bug Report, Feature Request).
-- A template only pre-fills the normal Task creation flow; it never bypasses it.
CREATE TABLE project_task_templates (
    template_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    template_name_ar VARCHAR(100) NOT NULL,
    work_type_id BIGINT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    default_title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    description_document JSONB NOT NULL,
    due_offset_days INTEGER,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_task_templates_project FOREIGN KEY (project_id)
        REFERENCES projects(project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_task_templates_work_type FOREIGN KEY (work_type_id)
        REFERENCES project_work_types(work_type_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_task_templates_created_by FOREIGN KEY (created_by)
        REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT project_task_templates_name_check CHECK (NULLIF(BTRIM(template_name), '') IS NOT NULL),
    CONSTRAINT project_task_templates_name_ar_check CHECK (NULLIF(BTRIM(template_name_ar), '') IS NOT NULL),
    CONSTRAINT project_task_templates_title_check CHECK (NULLIF(BTRIM(default_title), '') IS NOT NULL),
    CONSTRAINT project_task_templates_priority_check CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT project_task_templates_due_offset_check
        CHECK (due_offset_days IS NULL OR due_offset_days BETWEEN 0 AND 365),
    CONSTRAINT project_task_templates_document_check CHECK (jsonb_typeof(description_document) = 'object')
);

CREATE UNIQUE INDEX uq_project_task_templates_name_ci
    ON project_task_templates(project_id, LOWER(BTRIM(template_name)));
CREATE UNIQUE INDEX uq_project_task_templates_name_ar_ci
    ON project_task_templates(project_id, LOWER(BTRIM(template_name_ar)));
CREATE INDEX idx_project_task_templates_work_type
    ON project_task_templates(work_type_id);
