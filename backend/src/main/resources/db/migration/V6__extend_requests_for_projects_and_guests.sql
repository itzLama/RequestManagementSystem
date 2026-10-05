ALTER TABLE requests
    ADD COLUMN project_id BIGINT,
    ADD COLUMN guest_name VARCHAR(150),
    ADD COLUMN guest_email VARCHAR(255);

ALTER TABLE requests
    ALTER COLUMN created_by DROP NOT NULL;

ALTER TABLE requests
    ADD CONSTRAINT fk_requests_project
        FOREIGN KEY (project_id)
        REFERENCES projects(project_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT requests_creator_identity_check
        CHECK (
            (
                created_by IS NOT NULL
                AND guest_name IS NULL
                AND guest_email IS NULL
            )
            OR
            (
                created_by IS NULL
                AND NULLIF(BTRIM(guest_name), '') IS NOT NULL
                AND NULLIF(BTRIM(guest_email), '') IS NOT NULL
            )
        );

CREATE INDEX idx_requests_project_id ON requests(project_id);
