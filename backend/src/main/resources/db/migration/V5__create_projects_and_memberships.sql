CREATE TABLE projects (
    project_id BIGSERIAL PRIMARY KEY,
    project_name VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT projects_status_check
        CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE project_memberships (
    membership_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_memberships_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE RESTRICT,

    CONSTRAINT fk_project_memberships_employee
        FOREIGN KEY (employee_id) REFERENCES users(user_id) ON DELETE RESTRICT,

    CONSTRAINT uq_project_memberships_project_employee
        UNIQUE (project_id, employee_id)
);

CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_project_memberships_employee ON project_memberships(employee_id);
