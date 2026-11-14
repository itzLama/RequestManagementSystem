package com.requestmanagement.backend.usernotification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs only after the business transaction has committed, so a notification failure can never
 * roll back the Task change. Events published outside a transaction are ignored.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskNotificationListener {
    private final UserNotificationService notificationService;
    private final NotificationStreamRegistry streams;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskNotification(TaskNotificationEvent event) {
        try {
            notificationService.record(event).ifPresent(created -> streams.sendNotification(
                    event.recipientId(), created, notificationService.unreadCount(event.recipientId())));
        } catch (RuntimeException exception) {
            log.error("Unable to record {} notification for task {}.", event.type(), event.taskId(), exception);
        }
    }
}
