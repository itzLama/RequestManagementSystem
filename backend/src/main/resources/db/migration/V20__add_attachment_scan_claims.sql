ALTER TABLE task_attachments
    ADD COLUMN scan_claim_token UUID,
    ADD COLUMN scan_claimed_at TIMESTAMP,
    ADD COLUMN scan_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_scan_at TIMESTAMP;

ALTER TABLE task_attachments
    ADD CONSTRAINT task_attachments_scan_attempts_check CHECK (scan_attempts BETWEEN 0 AND 20),
    ADD CONSTRAINT task_attachments_scan_claim_check
        CHECK ((scan_claim_token IS NULL AND scan_claimed_at IS NULL)
            OR (scan_claim_token IS NOT NULL AND scan_claimed_at IS NOT NULL));

CREATE INDEX idx_task_attachments_scan_queue
    ON task_attachments(scan_status, storage_status, next_scan_at, attachment_id)
    WHERE storage_status = 'QUARANTINED' AND scan_status IN ('PENDING', 'ERROR');
