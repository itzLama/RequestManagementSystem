package com.requestmanagement.backend.attachmentstorage;

import java.time.Instant;

public record AttachmentStoredObjectSummary(AttachmentObjectKey key, long sizeBytes, Instant lastModified) { }
