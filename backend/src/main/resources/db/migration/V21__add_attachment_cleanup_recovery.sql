ALTER TABLE task_attachments
    ADD COLUMN cleanup_claim_token UUID,
    ADD COLUMN cleanup_claimed_at TIMESTAMP,
    ADD COLUMN cleanup_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_cleanup_at TIMESTAMP,
    ADD COLUMN lifecycle_error VARCHAR(1000);

ALTER TABLE task_attachments
    ADD CONSTRAINT task_attachments_cleanup_attempts_check
        CHECK (cleanup_attempts BETWEEN 0 AND 20),
    ADD CONSTRAINT task_attachments_cleanup_claim_check
        CHECK ((cleanup_claim_token IS NULL AND cleanup_claimed_at IS NULL)
            OR (cleanup_claim_token IS NOT NULL AND cleanup_claimed_at IS NOT NULL));

CREATE INDEX idx_task_attachments_cleanup_queue
    ON task_attachments(storage_status, next_cleanup_at, attachment_id)
    WHERE storage_status IN ('PENDING', 'UPLOAD_FAILED', 'DELETION_PENDING');

CREATE INDEX idx_task_attachments_reconciliation_created
    ON task_attachments(created_at, attachment_id)
    WHERE storage_status IN ('PENDING', 'QUARANTINED', 'AVAILABLE', 'DELETION_PENDING');

ALTER TABLE requests ADD COLUMN attachment_cleanup_started_at TIMESTAMP;

CREATE INDEX idx_requests_attachment_cleanup
    ON requests(attachment_cleanup_started_at, request_id)
    WHERE attachment_cleanup_started_at IS NOT NULL;
