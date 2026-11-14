package com.requestmanagement.backend.recurring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RecurringTaskRuleDtos {
    private RecurringTaskRuleDtos() { }

    public record Input(
            @NotNull(message = "Task Template is required.")
            @Positive(message = "Task Template ID must be positive.") Long templateId,
            @NotNull(message = "Frequency is required.") RecurrenceSchedule.Frequency frequency,
            @Min(value = 1, message = "Weekday must be between 1 (Monday) and 7 (Sunday).")
            @Max(value = 7, message = "Weekday must be between 1 (Monday) and 7 (Sunday).") Integer weekday,
            @Min(value = 1, message = "Day of month must be between 1 and 31.")
            @Max(value = 31, message = "Day of month must be between 1 and 31.") Integer monthDay,
            @NotNull(message = "Start date is required.") LocalDate startDate,
            LocalDate endDate,
            @Positive(message = "Assignee ID must be positive.") Long assigneeId,
            Boolean active
    ) { }

    /**
     * Warnings explain why the rule is currently skipped: ARCHIVED_PROJECT, RETIRED_WORK_TYPE,
     * ASSIGNEE_NOT_MEMBER, OWNER_INACTIVE. Last run results: CREATED, ALREADY_EXISTS, FAILED,
     * or SKIPPED_ followed by a warning code.
     */
    public record Response(
            Long id, Long templateId, String templateName, String templateNameAr,
            RecurrenceSchedule.Frequency frequency, Integer weekday, Integer monthDay,
            LocalDate startDate, LocalDate endDate,
            Long assigneeId, String assigneeName, boolean active,
            Long ownerId, String ownerName,
            LocalDate nextRunDate, LocalDateTime lastRunAt, String lastRunResult,
            LocalDate lastRunOccurrenceDate, Long lastRunTaskId,
            List<String> warnings, LocalDateTime createdAt, LocalDateTime updatedAt
    ) { }

    public record RunResult(String result, LocalDate occurrenceDate, Long taskId, Response rule) { }
}
