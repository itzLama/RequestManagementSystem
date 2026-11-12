package com.requestmanagement.backend.attachmentstorage;

import java.util.Map;

public record AttachmentObjectMetadata(
        long contentLength,
        String contentType,
        String entityTag,
        Map<String, String> customMetadata
) {
    public AttachmentObjectMetadata {
        customMetadata = customMetadata == null ? Map.of() : Map.copyOf(customMetadata);
    }
}
