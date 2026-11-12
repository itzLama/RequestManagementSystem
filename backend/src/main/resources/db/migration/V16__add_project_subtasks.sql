ALTER TABLE requests
    ADD COLUMN parent_request_id BIGINT;

ALTER TABLE requests
    ADD CONSTRAINT uq_requests_project_request UNIQUE (project_id, request_id),
    ADD CONSTRAINT fk_requests_parent
        FOREIGN KEY (parent_request_id) REFERENCES requests(request_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_requests_parent_project
        FOREIGN KEY (project_id, parent_request_id)
        REFERENCES requests(project_id, request_id) ON DELETE RESTRICT,
    ADD CONSTRAINT requests_parent_scope_check
        CHECK (parent_request_id IS NULL OR project_id IS NOT NULL),
    ADD CONSTRAINT requests_not_own_parent_check
        CHECK (parent_request_id IS NULL OR parent_request_id <> request_id);

CREATE INDEX idx_requests_parent_request_id
    ON requests(parent_request_id, created_at, request_id);

CREATE FUNCTION validate_request_subtask_hierarchy() RETURNS TRIGGER AS $$
DECLARE
    parent_parent_id BIGINT;
BEGIN
    IF NEW.parent_request_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT parent_request_id INTO parent_parent_id
    FROM requests
    WHERE request_id = NEW.parent_request_id AND project_id = NEW.project_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Subtask parent must be a Project Task in the same Project.';
    END IF;
    IF parent_parent_id IS NOT NULL THEN
        RAISE EXCEPTION 'Nested subtasks are not allowed.';
    END IF;
    IF EXISTS (SELECT 1 FROM requests WHERE parent_request_id = NEW.request_id) THEN
        RAISE EXCEPTION 'A parent Task cannot become a subtask.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_request_subtask_hierarchy
BEFORE INSERT OR UPDATE OF parent_request_id, project_id ON requests
FOR EACH ROW EXECUTE FUNCTION validate_request_subtask_hierarchy();
