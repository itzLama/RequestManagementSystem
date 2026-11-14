package com.requestmanagement.backend.usernotification;

/**
 * Lightweight "something changed in this Project" signal for live board refresh. Carries
 * references and the change type only, never Task content; clients refetch through the normal,
 * authorized endpoints. Separate from persistent notifications.
 */
public record ProjectChangeEvent(Long projectId, Long taskId, ChangeType change) {
    public enum ChangeType { TASK_CREATED, TASK_DELETED, STATUS_CHANGED, ASSIGNEE_CHANGED, DATES_CHANGED, TASK_UPDATED }
}
