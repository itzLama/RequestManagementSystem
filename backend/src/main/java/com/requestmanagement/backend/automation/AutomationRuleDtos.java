package com.requestmanagement.backend.automation;

import com.requestmanagement.backend.request.RequestPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AutomationRuleDtos {
    private AutomationRuleDtos() { }

    /** One trigger, optional conditions, and at least one action (assign / move / notify). */
    public record Input(
            @NotBlank(message = "Rule name is required.")
            @Size(max = 100, message = "Rule name must be at most 100 characters.") String name,
            @NotBlank(message = "Arabic rule name is required.")
            @Size(max = 100, message = "Arabic rule name must be at most 100 characters.") String nameAr,
            @NotNull(message = "Trigger is required.") AutomationTrigger trigger,
            @Positive(message = "Trigger status ID must be positive.") Long triggerStatusId,
            @Positive(message = "Work Type ID must be positive.") Long conditionWorkTypeId,
            RequestPriority conditionPriority,
            @Positive(message = "Assignee ID must be positive.") Long actionAssigneeId,
            @Positive(message = "Status ID must be positive.") Long actionStatusId,
            @Size(max = 20, message = "Choose at most 20 members to notify.") List<@NotNull @Positive Long> notifyUserIds,
            Boolean active
    ) { }

    /**
     * Warnings explain why the rule is skipped: ARCHIVED_PROJECT, TRIGGER_STATUS_RETIRED,
     * ACTION_STATUS_RETIRED, ASSIGNEE_NOT_MEMBER, RECIPIENT_NOT_MEMBER, NO_ACTION. Last run
     * results: APPLIED, NO_CHANGE, FAILED, or SKIPPED_INVALID_TARGET.
     */
    /**
     * Read-only preview of an enabled, valid "task created" rule for the Create Task form (Admins
     * and Project members). Only what the form needs: names, conditions, and the assign / move
     * target. Recipients and run history are never included. Listed in the order the rules run.
     */
    public record Preview(
            Long ruleId, String name, String nameAr, Long conditionWorkTypeId, RequestPriority conditionPriority,
            Long assigneeId, String assigneeName, Long statusId, String statusName, String statusSemantic
    ) { }

    public record Response(
            Long id, String name, String nameAr, AutomationTrigger trigger, Long triggerStatusId,
            Long conditionWorkTypeId, RequestPriority conditionPriority,
            Long actionAssigneeId, String actionAssigneeName, Long actionStatusId, List<Long> notifyUserIds,
            boolean active, LocalDateTime lastRunAt, String lastRunResult, Long lastRunTaskId,
            List<String> warnings, LocalDateTime createdAt, LocalDateTime updatedAt
    ) { }
}
