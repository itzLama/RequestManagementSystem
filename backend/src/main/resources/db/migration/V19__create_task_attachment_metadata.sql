CREATE TABLE task_attachments (
    attachment_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    slot_number SMALLINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    declared_mime_type VARCHAR(255),
    detected_mime_type VARCHAR(255),
    size_bytes BIGINT,
    sha256_digest CHAR(64),
    object_key VARCHAR(255) NOT NULL UNIQUE,
    upload_source VARCHAR(20) NOT NULL,
    uploaded_by_user_id BIGINT,
    guest_session_id_snapshot UUID,
    visibility VARCHAR(30) NOT NULL,
    scan_status VARCHAR(20) NOT NULL,
    storage_status VARCHAR(30) NOT NULL,
    scanned_at TIMESTAMP,
    scanner_name VARCHAR(100),
    scan_result_details VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_task_attachments_project_task
        FOREIGN KEY (project_id, request_id)
        REFERENCES requests(project_id, request_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_task_attachments_internal_uploader
        FOREIGN KEY (uploaded_by_user_id)
        REFERENCES users(user_id)
        ON DELETE RESTRICT,
    CONSTRAINT task_attachments_slot_check
        CHECK (slot_number BETWEEN 1 AND 10),
    CONSTRAINT task_attachments_filename_check
        CHECK (length(btrim(original_filename)) BETWEEN 1 AND 255),
    CONSTRAINT task_attachments_size_check
        CHECK (size_bytes IS NULL OR size_bytes BETWEEN 0 AND 26214400),
    CONSTRAINT task_attachments_sha256_check
        CHECK (sha256_digest IS NULL OR sha256_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT task_attachments_upload_source_check
        CHECK (upload_source IN ('INTERNAL', 'GUEST')),
    CONSTRAINT task_attachments_provenance_check
        CHECK ((upload_source = 'INTERNAL' AND uploaded_by_user_id IS NOT NULL
                    AND guest_session_id_snapshot IS NULL)
            OR (upload_source = 'GUEST' AND uploaded_by_user_id IS NULL
                    AND guest_session_id_snapshot IS NOT NULL)),
    CONSTRAINT task_attachments_visibility_check
        CHECK (visibility IN ('INTERNAL_ONLY', 'GUEST_VISIBLE')),
    CONSTRAINT task_attachments_scan_status_check
        CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'ERROR')),
    CONSTRAINT task_attachments_storage_status_check
        CHECK (storage_status IN ('PENDING', 'QUARANTINED', 'AVAILABLE',
                                  'UPLOAD_FAILED', 'DELETION_PENDING', 'DELETED')),
    CONSTRAINT task_attachments_scan_metadata_check
        CHECK ((scan_status = 'PENDING' AND scanned_at IS NULL)
            OR (scan_status <> 'PENDING' AND scanned_at IS NOT NULL)),
    CONSTRAINT task_attachments_available_check
        CHECK (storage_status <> 'AVAILABLE'
            OR (scan_status = 'CLEAN'
                AND detected_mime_type IS NOT NULL
                AND size_bytes IS NOT NULL
                AND sha256_digest IS NOT NULL)),
    CONSTRAINT task_attachments_deleted_check
        CHECK ((storage_status = 'DELETED' AND deleted_at IS NOT NULL)
            OR (storage_status <> 'DELETED' AND deleted_at IS NULL))
);

CREATE UNIQUE INDEX uq_task_attachments_reserved_slot
    ON task_attachments(request_id, slot_number)
    WHERE storage_status IN ('PENDING', 'QUARANTINED', 'AVAILABLE', 'DELETION_PENDING');

CREATE INDEX idx_task_attachments_task_created
    ON task_attachments(request_id, created_at, attachment_id);

CREATE INDEX idx_task_attachments_project_status
    ON task_attachments(project_id, storage_status, attachment_id);

CREATE TABLE task_attachment_visibility_events (
    visibility_event_id BIGSERIAL PRIMARY KEY,
    attachment_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    previous_visibility VARCHAR(30) NOT NULL,
    new_visibility VARCHAR(30) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attachment_visibility_event_attachment
        FOREIGN KEY (attachment_id)
        REFERENCES task_attachments(attachment_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_attachment_visibility_event_task
        FOREIGN KEY (project_id, request_id)
        REFERENCES requests(project_id, request_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_attachment_visibility_event_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES users(user_id)
        ON DELETE RESTRICT,
    CONSTRAINT attachment_visibility_event_action_check
        CHECK (action IN ('SHARED', 'UNSHARED')),
    CONSTRAINT attachment_visibility_event_previous_check
        CHECK (previous_visibility IN ('INTERNAL_ONLY', 'GUEST_VISIBLE')),
    CONSTRAINT attachment_visibility_event_new_check
        CHECK (new_visibility IN ('INTERNAL_ONLY', 'GUEST_VISIBLE')),
    CONSTRAINT attachment_visibility_event_change_check
        CHECK (previous_visibility <> new_visibility),
    CONSTRAINT attachment_visibility_event_action_consistency_check
        CHECK ((action = 'SHARED' AND previous_visibility = 'INTERNAL_ONLY'
                    AND new_visibility = 'GUEST_VISIBLE')
            OR (action = 'UNSHARED' AND previous_visibility = 'GUEST_VISIBLE'
                    AND new_visibility = 'INTERNAL_ONLY'))
);

CREATE INDEX idx_attachment_visibility_events_attachment
    ON task_attachment_visibility_events(attachment_id, occurred_at, visibility_event_id);

CREATE FUNCTION validate_task_attachment_owner() RETURNS TRIGGER AS $$
DECLARE
    owner_parent_id BIGINT;
    owner_created_by BIGINT;
    owner_guest_email VARCHAR(255);
BEGIN
    SELECT parent_request_id, created_by, guest_email
      INTO owner_parent_id, owner_created_by, owner_guest_email
      FROM requests
     WHERE request_id = NEW.request_id AND project_id = NEW.project_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Attachment owner must be a Project Task in the specified Project.';
    END IF;

    IF NEW.upload_source = 'GUEST'
       AND (owner_parent_id IS NOT NULL OR owner_created_by IS NOT NULL OR owner_guest_email IS NULL) THEN
        RAISE EXCEPTION 'Guest attachment provenance requires a Guest-created root Project Task.';
    END IF;

    IF NEW.visibility = 'GUEST_VISIBLE'
       AND (owner_parent_id IS NOT NULL OR owner_created_by IS NOT NULL OR owner_guest_email IS NULL) THEN
        RAISE EXCEPTION 'Guest-visible attachments require a Guest-created root Project Task.';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_task_attachment_owner
BEFORE INSERT OR UPDATE OF project_id, request_id, upload_source, visibility
ON task_attachments
FOR EACH ROW EXECUTE FUNCTION validate_task_attachment_owner();
