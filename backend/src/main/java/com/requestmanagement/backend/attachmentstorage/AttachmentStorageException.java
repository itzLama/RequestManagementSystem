package com.requestmanagement.backend.attachmentstorage;

public class AttachmentStorageException extends RuntimeException {
    public enum Failure {
        NOT_FOUND,
        UNAVAILABLE,
        UNAUTHORIZED,
        MISCONFIGURED,
        UNEXPECTED
    }

    private final Failure failure;

    AttachmentStorageException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
