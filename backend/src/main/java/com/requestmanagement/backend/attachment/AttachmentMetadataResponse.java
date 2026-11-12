package com.requestmanagement.backend.attachment;

import java.time.LocalDateTime;

public record AttachmentMetadataResponse(
        Long id, String filename, String declaredMimeType, String detectedMimeType, Long sizeBytes,
        TaskAttachment.UploadSource uploadSource, TaskAttachment.Visibility visibility,
        TaskAttachment.ScanStatus scanStatus, TaskAttachment.StorageStatus storageStatus,
        boolean downloadable, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    static AttachmentMetadataResponse from(TaskAttachment attachment) {
        return new AttachmentMetadataResponse(attachment.getId(), attachment.getOriginalFilename(),
                attachment.getDeclaredMimeType(), attachment.getDetectedMimeType(), attachment.getSizeBytes(),
                attachment.getUploadSource(), attachment.getVisibility(), attachment.getScanStatus(),
                attachment.getStorageStatus(), attachment.isDownloadable(),
                attachment.getCreatedAt(), attachment.getUpdatedAt());
    }
}
