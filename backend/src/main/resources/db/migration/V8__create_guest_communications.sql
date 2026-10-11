CREATE TABLE guest_communications (
    communication_id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    sent_by BIGINT NOT NULL,
    message TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT guest_communications_message_check
        CHECK (CHAR_LENGTH(BTRIM(message)) BETWEEN 1 AND 10000),

    CONSTRAINT fk_guest_communications_request
        FOREIGN KEY (request_id)
        REFERENCES requests(request_id),

    CONSTRAINT fk_guest_communications_sender
        FOREIGN KEY (sent_by)
        REFERENCES users(user_id)
);

CREATE INDEX idx_guest_communications_request_chronology
    ON guest_communications(request_id, sent_at, communication_id);
