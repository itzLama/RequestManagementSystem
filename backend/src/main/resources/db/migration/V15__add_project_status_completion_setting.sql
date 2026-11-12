ALTER TABLE project_status_columns
    ADD COLUMN counts_as_completed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE project_status_columns
SET counts_as_completed = TRUE
WHERE semantic_key = 'COMPLETED';

ALTER TABLE project_status_columns
    ADD CONSTRAINT project_status_new_not_completed_check
    CHECK (semantic_key IS DISTINCT FROM 'NEW' OR counts_as_completed = FALSE);

CREATE OR REPLACE FUNCTION initialize_project_status_columns() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO project_status_columns(
        project_id, status_name, semantic_key, display_order, counts_as_completed
    )
    VALUES
        (NEW.project_id, 'New', 'NEW', 0, FALSE),
        (NEW.project_id, 'In Progress', 'IN_PROGRESS', 1, FALSE),
        (NEW.project_id, 'Waiting User', 'WAITING_USER', 2, FALSE),
        (NEW.project_id, 'Completed', 'COMPLETED', 3, TRUE),
        (NEW.project_id, 'Rejected', 'REJECTED', 4, FALSE);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
