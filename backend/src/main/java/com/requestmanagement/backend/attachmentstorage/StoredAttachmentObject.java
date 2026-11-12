package com.requestmanagement.backend.attachmentstorage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class StoredAttachmentObject implements AutoCloseable {
    private final InputStream stream;
    private final AttachmentObjectMetadata metadata;

    public StoredAttachmentObject(InputStream stream, AttachmentObjectMetadata metadata) {
        this.stream = Objects.requireNonNull(stream);
        this.metadata = Objects.requireNonNull(metadata);
    }

    public InputStream stream() {
        return stream;
    }

    public AttachmentObjectMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
