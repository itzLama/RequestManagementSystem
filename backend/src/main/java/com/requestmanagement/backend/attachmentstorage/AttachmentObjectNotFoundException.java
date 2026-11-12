package com.requestmanagement.backend.attachmentstorage;

public final class AttachmentObjectNotFoundException extends AttachmentStorageException {
    AttachmentObjectNotFoundException(Throwable cause) {
        super(Failure.NOT_FOUND, "The stored attachment was not found.", cause);
    }
}
