package com.requestmanagement.backend.automation;

/**
 * Published inside the business transaction; automation runs only after it commits (see
 * {@link AutomationTriggerListener}). References only. {@code statusId} is the new status for
 * {@link AutomationTrigger#STATUS_CHANGED}. {@code keepAssignee} is set for a Task created through the
 * form whose user overrode the suggested assignee: assign actions are skipped, other actions run.
 */
public record AutomationTriggerEvent(Long projectId, Long taskId, AutomationTrigger trigger, Long statusId,
                                     boolean keepAssignee) {
    public AutomationTriggerEvent(Long projectId, Long taskId, AutomationTrigger trigger, Long statusId) {
        this(projectId, taskId, trigger, statusId, false);
    }
}
