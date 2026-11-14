-- Stage 5 extension: internal-note notifications for Admin participants and guest-message
-- notifications. Guest messages have no internal actor (actor_id stays NULL).
ALTER TABLE user_notifications DROP CONSTRAINT user_notifications_type_check;
ALTER TABLE user_notifications ADD CONSTRAINT user_notifications_type_check
    CHECK (notification_type IN ('TASK_ASSIGNED', 'TASK_STATUS_CHANGED', 'TASK_COMMENTED',
                                 'TASK_INTERNAL_NOTE', 'GUEST_MESSAGE'));
