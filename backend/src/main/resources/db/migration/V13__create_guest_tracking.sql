CREATE TABLE guest_tracking_challenges (
    challenge_id UUID PRIMARY KEY,
    request_id BIGINT,
    lookup_digest CHAR(64) NOT NULL,
    otp_digest CHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    sent_count INTEGER NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMP,
    consumed_at TIMESTAMP,
    superseded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guest_tracking_challenge_request FOREIGN KEY (request_id) REFERENCES requests(request_id) ON DELETE RESTRICT,
    CONSTRAINT guest_tracking_attempts_check CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT guest_tracking_sent_count_check CHECK (sent_count >= 0)
);

CREATE INDEX idx_guest_tracking_challenge_lookup_created
    ON guest_tracking_challenges(lookup_digest, created_at DESC);
CREATE INDEX idx_guest_tracking_challenge_request_active
    ON guest_tracking_challenges(request_id, created_at DESC) WHERE consumed_at IS NULL AND superseded_at IS NULL;

CREATE TABLE guest_tracking_sessions (
    session_id UUID PRIMARY KEY,
    token_digest CHAR(64) NOT NULL UNIQUE,
    request_id BIGINT NOT NULL,
    last_activity_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guest_tracking_session_request FOREIGN KEY (request_id) REFERENCES requests(request_id) ON DELETE RESTRICT
);

CREATE INDEX idx_guest_tracking_session_request_active
    ON guest_tracking_sessions(request_id, expires_at) WHERE revoked_at IS NULL;

ALTER TABLE guest_communications ALTER COLUMN sent_by DROP NOT NULL;
ALTER TABLE guest_communications
    ADD COLUMN sender_type VARCHAR(20) NOT NULL DEFAULT 'TEAM',
    ADD COLUMN sender_name_snapshot VARCHAR(150),
    ADD COLUMN sender_email_snapshot VARCHAR(255),
    ADD CONSTRAINT guest_communications_sender_type_check CHECK (sender_type IN ('TEAM', 'GUEST')),
    ADD CONSTRAINT guest_communications_sender_scope_check CHECK (
        (sender_type = 'TEAM' AND sent_by IS NOT NULL)
        OR (sender_type = 'GUEST' AND sent_by IS NULL AND sender_name_snapshot IS NOT NULL)
    );

UPDATE guest_communications gc
SET sender_name_snapshot = u.full_name,
    sender_email_snapshot = u.email
FROM users u
WHERE gc.sent_by = u.user_id;

CREATE INDEX idx_guest_communications_request_sender
    ON guest_communications(request_id, sender_type, sent_at, communication_id);
