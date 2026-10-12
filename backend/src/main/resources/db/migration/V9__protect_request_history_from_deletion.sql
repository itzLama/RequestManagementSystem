ALTER TABLE comments
    DROP CONSTRAINT fk_comments_request,
    ADD CONSTRAINT fk_comments_request
        FOREIGN KEY (request_id)
        REFERENCES requests(request_id)
        ON DELETE RESTRICT;

ALTER TABLE status_history
    DROP CONSTRAINT fk_status_history_request,
    ADD CONSTRAINT fk_status_history_request
        FOREIGN KEY (request_id)
        REFERENCES requests(request_id)
        ON DELETE RESTRICT;
