CREATE TABLE project_task_links (
    link_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_task_id BIGINT NOT NULL,
    target_task_id BIGINT NOT NULL,
    relationship_type VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_task_links_project FOREIGN KEY (project_id)
        REFERENCES projects(project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_task_links_source_project FOREIGN KEY (project_id, source_task_id)
        REFERENCES requests(project_id, request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_task_links_target_project FOREIGN KEY (project_id, target_task_id)
        REFERENCES requests(project_id, request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_task_links_created_by FOREIGN KEY (created_by)
        REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT project_task_links_distinct_tasks CHECK (source_task_id <> target_task_id),
    CONSTRAINT project_task_links_type_check CHECK (relationship_type IN ('BLOCKS', 'DUPLICATES', 'RELATES_TO')),
    CONSTRAINT project_task_links_relates_order_check CHECK (
        relationship_type <> 'RELATES_TO' OR source_task_id < target_task_id
    ),
    CONSTRAINT uq_project_task_links_logical UNIQUE
        (project_id, source_task_id, target_task_id, relationship_type)
);

CREATE INDEX idx_project_task_links_source
    ON project_task_links(project_id, source_task_id, link_id);

CREATE INDEX idx_project_task_links_target
    ON project_task_links(project_id, target_task_id, link_id);
