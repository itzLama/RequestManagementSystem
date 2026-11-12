package com.requestmanagement.backend.attachmentstorage;

public record AttachmentStorageDiagnostic(boolean reachable, String status) {
    public static AttachmentStorageDiagnostic healthy() {
        return new AttachmentStorageDiagnostic(true, "REACHABLE");
    }

    public static AttachmentStorageDiagnostic unavailable() {
        return new AttachmentStorageDiagnostic(false, "UNAVAILABLE");
    }
}
