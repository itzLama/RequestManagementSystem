-- Stage 5: persistent in-app notifications for internal users.
-- Only the event type and references are stored; the text is rendered by the client in the
-- reader's language. Notifications are personal data of the recipient and follow the lifecycle
-- of the referenced Task and Project.
CREATE TABLE user_notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    project_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    actor_id BIGINT,
    project_status_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,

    CONSTRAINT fk_user_notifications_recipient
        FOREIGN KEY (recipient_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_notifications_actor
        FOREIGN KEY (actor_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_user_notifications_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_notifications_task
        FOREIGN KEY (project_id, request_id) REFERENCES requests(project_id, request_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_notifications_status
        FOREIGN KEY (project_status_id) REFERENCES project_status_columns(status_id) ON DELETE SET NULL,
    CONSTRAINT user_notifications_type_check
        CHECK (notification_type IN ('TASK_ASSIGNED', 'TASK_STATUS_CHANGED', 'TASK_COMMENTED')),
    CONSTRAINT user_notifications_not_self_check
        CHECK (actor_id IS NULL OR actor_id <> recipient_id)
);

CREATE INDEX idx_user_notifications_recipient_recent
    ON user_notifications(recipient_id, created_at DESC, notification_id DESC);
CREATE INDEX idx_user_notifications_recipient_unread
    ON user_notifications(recipient_id) WHERE read_at IS NULL;
CREATE INDEX idx_user_notifications_task ON user_notifications(request_id);
