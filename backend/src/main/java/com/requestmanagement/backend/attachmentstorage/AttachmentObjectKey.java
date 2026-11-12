package com.requestmanagement.backend.attachmentstorage;

import java.util.regex.Pattern;

public record AttachmentObjectKey(String value) {
    private static final Pattern FORMAT = Pattern.compile("(?:quarantine|available)/[A-Za-z0-9_-]{43}");

    public AttachmentObjectKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid attachment object key.");
        }
    }

    public boolean isQuarantine() {
        return value.startsWith("quarantine/");
    }

    public boolean isAvailable() {
        return value.startsWith("available/");
    }
}
