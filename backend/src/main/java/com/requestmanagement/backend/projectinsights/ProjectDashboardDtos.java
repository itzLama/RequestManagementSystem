package com.requestmanagement.backend.projectinsights;

import com.requestmanagement.backend.request.RequestPriority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectDashboardDtos {
    private ProjectDashboardDtos() { }

    public enum ActivityKind { TASK_CREATED, STATUS_CHANGED, COMMENT_ADDED, ATTACHMENT_UPLOADED }

    /**
     * Root-task completion using each status's Counts-as-Completed setting.
     * totalTasks = completedTasks + openTasks + rejectedTasks;
     * percentage = completedTasks / (totalTasks - rejectedTasks), or 0 when that is 0.
     */
    public record Completion(long totalTasks, long completedTasks, long openTasks, long rejectedTasks, int percentage) { }

    public record StatusCount(Long statusId, String statusName, String semanticKey, boolean countsAsCompleted, boolean active,
                              int displayOrder, long count) { }

    public record PriorityCount(RequestPriority priority, long count) { }

    /** Open (not completed) root tasks per assignee; {@code assigneeId == null} means Unassigned. */
    public record Workload(Long assigneeId, String assigneeName, long openTasks, long overdueTasks) { }

    public record TaskRef(Long taskId, String title, RequestPriority priority, Long statusId, String statusName,
                          String statusSemantic,
                          Long assigneeId, String assigneeName, LocalDate startDate, LocalDate dueDate) { }

    /** Built from existing records (tasks, status history, comments, attachments); never synthesized. */
    public record Activity(ActivityKind kind, Long taskId, String taskTitle, Long parentTaskId, String actorName,
                           boolean actorIsGuest, String statusName, String statusSemantic, String fileName, LocalDateTime occurredAt,
                           String automationRuleName, String automationRuleNameAr) { }

    public record Response(Long projectId, LocalDate today, int upcomingWindowDays, Completion completion,
                           List<StatusCount> statuses, List<PriorityCount> priorities, List<Workload> workload,
                           long overdueCount, List<TaskRef> overdueTasks, long upcomingCount,
                           List<TaskRef> upcomingTasks, List<Activity> recentActivity) { }
}
