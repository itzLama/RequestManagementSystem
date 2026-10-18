CREATE TABLE project_status_columns (
    status_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    status_name VARCHAR(50) NOT NULL,
    semantic_key VARCHAR(30),
    display_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_status_columns_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE RESTRICT,
    CONSTRAINT project_status_name_not_blank CHECK (NULLIF(BTRIM(status_name), '') IS NOT NULL),
    CONSTRAINT project_status_semantic_key CHECK (semantic_key IS NULL OR semantic_key IN ('NEW','IN_PROGRESS','WAITING_USER','COMPLETED','REJECTED')),
    CONSTRAINT project_status_order_nonnegative CHECK (display_order >= 0),
    CONSTRAINT uq_project_status_project_id UNIQUE (project_id, status_id)
);

CREATE UNIQUE INDEX uq_project_status_active_name_ci
    ON project_status_columns(project_id, LOWER(BTRIM(status_name))) WHERE is_active;
CREATE INDEX idx_project_status_active_order
    ON project_status_columns(project_id, display_order) WHERE is_active;
CREATE UNIQUE INDEX uq_project_status_semantic
    ON project_status_columns(project_id, semantic_key) WHERE semantic_key IS NOT NULL;
CREATE INDEX idx_project_status_project_active_order
    ON project_status_columns(project_id, is_active, display_order, status_id);

INSERT INTO project_status_columns(project_id,status_name,semantic_key,display_order)
SELECT p.project_id, defaults.status_name, defaults.semantic_key, defaults.display_order
FROM projects p
CROSS JOIN (VALUES
    ('New','NEW',0),('In Progress','IN_PROGRESS',1),('Waiting User','WAITING_USER',2),
    ('Completed','COMPLETED',3),('Rejected','REJECTED',4)
) AS defaults(status_name,semantic_key,display_order);

CREATE FUNCTION initialize_project_status_columns() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO project_status_columns(project_id,status_name,semantic_key,display_order)
    VALUES (NEW.project_id,'New','NEW',0), (NEW.project_id,'In Progress','IN_PROGRESS',1),
           (NEW.project_id,'Waiting User','WAITING_USER',2), (NEW.project_id,'Completed','COMPLETED',3),
           (NEW.project_id,'Rejected','REJECTED',4);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_initialize_project_status_columns
AFTER INSERT ON projects FOR EACH ROW EXECUTE FUNCTION initialize_project_status_columns();

ALTER TABLE requests ADD COLUMN project_status_id BIGINT;
UPDATE requests r SET project_status_id=psc.status_id
FROM project_status_columns psc
WHERE r.project_id=psc.project_id AND r.status=psc.semantic_key;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM requests WHERE project_id IS NOT NULL AND project_status_id IS NULL) THEN
        RAISE EXCEPTION 'Project status migration incomplete: a Project Task was not mapped.';
    END IF;
END $$;

ALTER TABLE requests ALTER COLUMN status DROP NOT NULL;
ALTER TABLE requests
    ADD CONSTRAINT fk_requests_project_status
        FOREIGN KEY(project_id,project_status_id) REFERENCES project_status_columns(project_id,status_id) ON DELETE RESTRICT,
    ADD CONSTRAINT requests_status_scope_check CHECK (
        (project_id IS NULL AND status IS NOT NULL AND project_status_id IS NULL)
        OR (project_id IS NOT NULL AND project_status_id IS NOT NULL)
    );
CREATE INDEX idx_requests_project_status_id ON requests(project_status_id);

ALTER TABLE status_history
    ADD COLUMN old_project_status_id BIGINT,
    ADD COLUMN new_project_status_id BIGINT;

UPDATE status_history sh
SET old_project_status_id=(
        SELECT old_column.status_id FROM requests r
        JOIN project_status_columns old_column ON old_column.project_id=r.project_id
        WHERE r.request_id=sh.request_id AND old_column.semantic_key=sh.old_status
    ),
    new_project_status_id=(
        SELECT new_column.status_id FROM requests r
        JOIN project_status_columns new_column ON new_column.project_id=r.project_id
        WHERE r.request_id=sh.request_id AND new_column.semantic_key=sh.new_status
    )
WHERE EXISTS (SELECT 1 FROM requests r WHERE r.request_id=sh.request_id AND r.project_id IS NOT NULL);

ALTER TABLE status_history ALTER COLUMN new_status DROP NOT NULL;
ALTER TABLE status_history
    ADD CONSTRAINT fk_status_history_old_project_status FOREIGN KEY(old_project_status_id) REFERENCES project_status_columns(status_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_status_history_new_project_status FOREIGN KEY(new_project_status_id) REFERENCES project_status_columns(status_id) ON DELETE RESTRICT,
    ADD CONSTRAINT status_history_model_check CHECK (
        (new_status IS NOT NULL AND new_project_status_id IS NULL)
        OR (new_project_status_id IS NOT NULL)
    );
CREATE INDEX idx_status_history_old_project_status ON status_history(old_project_status_id);
CREATE INDEX idx_status_history_new_project_status ON status_history(new_project_status_id);

-- Compatibility for database-level fixtures and integrations that insert a Project Task
-- using the former semantic status column. Application writes use project_status_id directly.
CREATE FUNCTION resolve_project_task_status() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.project_id IS NOT NULL AND NEW.project_status_id IS NULL THEN
        SELECT status_id INTO NEW.project_status_id
        FROM project_status_columns
        WHERE project_id=NEW.project_id
          AND semantic_key=COALESCE(NEW.status, 'NEW')
          AND is_active;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resolve_project_task_status
BEFORE INSERT OR UPDATE OF project_id,status,project_status_id ON requests
FOR EACH ROW EXECUTE FUNCTION resolve_project_task_status();

CREATE FUNCTION resolve_project_status_history() RETURNS TRIGGER AS $$
DECLARE
    task_project_id BIGINT;
BEGIN
    SELECT project_id INTO task_project_id FROM requests WHERE request_id=NEW.request_id;
    IF task_project_id IS NOT NULL THEN
        IF NEW.old_project_status_id IS NULL AND NEW.old_status IS NOT NULL THEN
            SELECT status_id INTO NEW.old_project_status_id FROM project_status_columns
            WHERE project_id=task_project_id AND semantic_key=NEW.old_status;
        END IF;
        IF NEW.new_project_status_id IS NULL AND NEW.new_status IS NOT NULL THEN
            SELECT status_id INTO NEW.new_project_status_id FROM project_status_columns
            WHERE project_id=task_project_id AND semantic_key=NEW.new_status;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resolve_project_status_history
BEFORE INSERT OR UPDATE OF request_id,old_status,new_status,old_project_status_id,new_project_status_id ON status_history
FOR EACH ROW EXECUTE FUNCTION resolve_project_status_history();
