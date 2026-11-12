-- Date-only planning fields for Project Tasks and subtasks (Stage 4 foundation).
-- Both are optional. General Requests never carry planning dates.
ALTER TABLE requests ADD COLUMN start_date DATE;
ALTER TABLE requests ADD COLUMN due_date DATE;

ALTER TABLE requests
    ADD CONSTRAINT requests_task_dates_order_check
    CHECK (start_date IS NULL OR due_date IS NULL OR start_date <= due_date);

ALTER TABLE requests
    ADD CONSTRAINT requests_task_dates_project_only_check
    CHECK ((start_date IS NULL AND due_date IS NULL) OR project_id IS NOT NULL);

CREATE INDEX idx_requests_project_due_date
    ON requests(project_id, due_date)
    WHERE due_date IS NOT NULL;
