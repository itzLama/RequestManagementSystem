package com.requestmanagement.backend.attachment;

public final class AttachmentLifecycleException extends RuntimeException {
    public enum Reason {
        NOT_FOUND,
        CAPACITY_EXCEEDED,
        INVALID_OWNER,
        INVALID_METADATA,
        INVALID_TRANSITION
    }

    private final Reason reason;

    AttachmentLifecycleException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
