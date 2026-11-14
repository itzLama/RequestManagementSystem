package com.requestmanagement.backend.usernotification;

/**
 * Published inside the business transaction; handled only after it commits
 * (see {@link TaskNotificationListener}). Carries references only.
 */
public record TaskNotificationEvent(
        UserNotificationType type,
        Long recipientId,
        Long actorId,
        Long projectId,
        Long taskId,
        Long statusId
) { }
