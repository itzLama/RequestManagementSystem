package com.requestmanagement.backend.usernotification;

/** Stored as a type code; the client renders the message in the reader's language. */
public enum UserNotificationType {
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    TASK_COMMENTED,
    /** Admin-only: an internal note was added. Never delivered to Employees. */
    TASK_INTERNAL_NOTE,
    /** A guest sent a message on a guest-submitted Project Task (no internal actor). */
    GUEST_MESSAGE
}
