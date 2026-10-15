CREATE TABLE project_guest_form_settings (
    project_id BIGINT PRIMARY KEY,
    work_type_visible BOOLEAN NOT NULL DEFAULT TRUE,
    default_work_type_id BIGINT,
    priority_visible BOOLEAN NOT NULL DEFAULT TRUE,
    priority_required BOOLEAN NOT NULL DEFAULT TRUE,
    description_visible BOOLEAN NOT NULL DEFAULT TRUE,
    description_required BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guest_form_settings_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_guest_form_settings_default_work_type FOREIGN KEY (default_work_type_id) REFERENCES project_work_types(work_type_id) ON DELETE RESTRICT,
    CONSTRAINT guest_form_settings_work_type_default_check CHECK (
        (work_type_visible = TRUE AND default_work_type_id IS NULL)
        OR (work_type_visible = FALSE AND default_work_type_id IS NOT NULL)
    ),
    CONSTRAINT guest_form_settings_priority_required_check CHECK (priority_visible OR NOT priority_required),
    CONSTRAINT guest_form_settings_description_required_check CHECK (description_visible OR NOT description_required)
);

INSERT INTO project_guest_form_settings(project_id)
SELECT project_id FROM projects;

CREATE TABLE project_guest_fields (
    guest_field_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    field_label VARCHAR(100) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_guest_fields_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE RESTRICT,
    CONSTRAINT project_guest_fields_label_check CHECK (NULLIF(BTRIM(field_label), '') IS NOT NULL),
    CONSTRAINT project_guest_fields_type_check CHECK (field_type IN ('TEXT','TEXTAREA','NUMBER','DROPDOWN','DATE','CHECKBOX')),
    CONSTRAINT project_guest_fields_order_check CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_project_guest_fields_project_label_ci
    ON project_guest_fields(project_id, LOWER(BTRIM(field_label)));
CREATE INDEX idx_project_guest_fields_project_active_order
    ON project_guest_fields(project_id, is_active, display_order, guest_field_id);

CREATE TABLE project_guest_field_options (
    option_id BIGSERIAL PRIMARY KEY,
    guest_field_id BIGINT NOT NULL,
    option_label VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guest_field_options_field FOREIGN KEY (guest_field_id) REFERENCES project_guest_fields(guest_field_id) ON DELETE RESTRICT,
    CONSTRAINT guest_field_options_label_check CHECK (NULLIF(BTRIM(option_label), '') IS NOT NULL),
    CONSTRAINT guest_field_options_order_check CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_guest_field_options_field_label_ci
    ON project_guest_field_options(guest_field_id, LOWER(BTRIM(option_label)));
CREATE INDEX idx_guest_field_options_field_active_order
    ON project_guest_field_options(guest_field_id, is_active, display_order, option_id);

CREATE TABLE request_guest_field_answers (
    answer_id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    guest_field_id BIGINT NOT NULL,
    field_label_snapshot VARCHAR(100) NOT NULL,
    field_type_snapshot VARCHAR(20) NOT NULL,
    display_order_snapshot INTEGER NOT NULL,
    answer_value TEXT NOT NULL,
    option_label_snapshot VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guest_field_answers_request FOREIGN KEY (request_id) REFERENCES requests(request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_guest_field_answers_field FOREIGN KEY (guest_field_id) REFERENCES project_guest_fields(guest_field_id) ON DELETE RESTRICT,
    CONSTRAINT uq_guest_field_answers_request_field UNIQUE (request_id, guest_field_id),
    CONSTRAINT guest_field_answers_type_check CHECK (field_type_snapshot IN ('TEXT','TEXTAREA','NUMBER','DROPDOWN','DATE','CHECKBOX')),
    CONSTRAINT guest_field_answers_order_check CHECK (display_order_snapshot >= 0),
    CONSTRAINT guest_field_answers_value_check CHECK (CHAR_LENGTH(answer_value) <= 5000)
);

CREATE INDEX idx_guest_field_answers_request_order
    ON request_guest_field_answers(request_id, display_order_snapshot, answer_id);
