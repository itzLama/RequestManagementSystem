package com.requestmanagement.backend.usernotification;

import java.time.LocalDateTime;
import java.util.List;

public final class UserNotificationDtos {
    private UserNotificationDtos() { }

    /** References plus display names; the message text itself is composed on the client. */
    public record Item(
            Long id,
            UserNotificationType type,
            Long projectId,
            String projectName,
            Long taskId,
            String taskTitle,
            Long parentTaskId,
            String guestName,
            Long actorId,
            String actorName,
            Long statusId,
            String statusName,
            String statusSemantic,
            /** Set when the change was made by a Workflow Automation rule (snapshot of its names). */
            String automationRuleName,
            String automationRuleNameAr,
            LocalDateTime createdAt,
            LocalDateTime readAt
    ) { }

    public record Page(List<Item> items, int page, int size, boolean hasMore, long unreadCount) { }

    public record UnreadCount(long unreadCount) { }
}
