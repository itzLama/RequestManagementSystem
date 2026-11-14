package com.requestmanagement.backend.usernotification;

/**
 * Published inside the business transaction; handled only after it commits
 * (see {@link TaskNotificationListener}). Carries references only. When the change was made by a
 * Workflow Automation rule, {@code actorId} is null and the rule's names are attached instead.
 */
public record TaskNotificationEvent(
        UserNotificationType type,
        Long recipientId,
        Long actorId,
        Long projectId,
        Long taskId,
        Long statusId,
        Long automationRuleId,
        String automationRuleName,
        String automationRuleNameAr
) {
    public TaskNotificationEvent(UserNotificationType type, Long recipientId, Long actorId, Long projectId,
                                 Long taskId, Long statusId) {
        this(type, recipientId, actorId, projectId, taskId, statusId, null, null, null);
    }
}
