package com.requestmanagement.backend.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

record ValidatedAttachmentFile(
        Path path,
        String filename,
        String declaredMimeType,
        String detectedMimeType,
        long size,
        String sha256
) implements AutoCloseable {
    InputStream openStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort. The OS temporary directory remains private and is cleaned independently.
        }
    }
}
