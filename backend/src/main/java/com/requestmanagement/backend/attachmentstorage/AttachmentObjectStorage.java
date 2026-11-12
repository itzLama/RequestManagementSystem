package com.requestmanagement.backend.attachmentstorage;

import java.io.InputStream;
import java.util.Map;
import java.util.List;

public interface AttachmentObjectStorage {
    void uploadQuarantine(
            AttachmentObjectKey key,
            InputStream content,
            long contentLength,
            String contentType,
            Map<String, String> metadata);

    StoredAttachmentObject open(AttachmentObjectKey key);

    AttachmentObjectMetadata metadata(AttachmentObjectKey key);

    void promote(AttachmentObjectKey quarantineKey, AttachmentObjectKey availableKey);

    void delete(AttachmentObjectKey key);

    boolean exists(AttachmentObjectKey key);

    default List<AttachmentStoredObjectSummary> listOwned(String namespace, int limit) { return List.of(); }

    AttachmentStorageDiagnostic diagnostic();
}
