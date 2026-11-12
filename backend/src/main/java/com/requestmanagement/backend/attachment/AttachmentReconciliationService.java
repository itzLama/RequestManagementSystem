package com.requestmanagement.backend.attachment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectStorage;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectNotFoundException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AttachmentReconciliationService {
    private final AttachmentProcessingProperties properties;
    private final TaskAttachmentRepository attachments;
    private final AttachmentLifecycleService lifecycle;
    private final AttachmentDeletionService deletion;
    private final ObjectProvider<AttachmentObjectStorage> storageProvider;

    @Scheduled(fixedDelayString = "${masar.attachments.processing.interval:30s}")
    public void reconcile() {
        if (!properties.isReconciliationEnabled()) return;
        LocalDateTime now = LocalDateTime.now();
        if (properties.isDestructiveReconciliationEnabled()) {
            attachments.findCleanupCandidates(now, now.minus(properties.getCleanupClaimTimeout()),
                    properties.getMaxCleanupAttempts(), properties.getBatchSize())
                    .forEach(id -> attempt(() -> deletion.cleanup(id)));
            attachments.findStaleUploadCandidates(now.minus(properties.getCleanupGracePeriod()),
                    properties.getBatchSize()).forEach(id -> attempt(() -> reconcileStaleUpload(id)));
            reconcileOrphans(now);
        }
    }

    private void reconcileOrphans(LocalDateTime now) {
        AttachmentObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) return;
        java.time.Instant cutoff = now.minus(properties.getCleanupGracePeriod())
                .atZone(java.time.ZoneId.systemDefault()).toInstant();
        for (String namespace : java.util.List.of("quarantine", "available")) {
            for (var object : storage.listOwned(namespace, properties.getBatchSize())) {
                if (object.lastModified() == null || !object.lastModified().isBefore(cutoff)) continue;
                if (attachments.existsByObjectKey(object.key().value())) continue;
                try {
                    var metadata = storage.metadata(object.key());
                    String owner = metadata.customMetadata().get("attachment-id");
                    if (owner == null) continue;
                    Long attachmentId;
                    try { attachmentId = Long.valueOf(owner); } catch (NumberFormatException invalid) { continue; }
                    TaskAttachment record = attachments.findMetadata(attachmentId).orElse(null);
                    if (record == null || !record.getObjectKey().equals(object.key().value())) storage.delete(object.key());
                } catch (AttachmentObjectNotFoundException ignored) {
                    // Concurrent cleanup already completed.
                }
            }
        }
    }

    private void reconcileStaleUpload(Long id) {
        TaskAttachment attachment = attachments.findMetadata(id).orElse(null);
        if (attachment == null) return;
        if (attachment.getStorageStatus() == TaskAttachment.StorageStatus.UPLOAD_FAILED) {
            lifecycle.finalizeFailedReservation(id);
        } else if (attachment.getStorageStatus() == TaskAttachment.StorageStatus.PENDING) {
            lifecycle.markDeletionPending(attachment.getProjectId(), attachment.getRequestId(), id);
            deletion.cleanup(id);
        }
    }

    private void attempt(Runnable action) {
        try { action.run(); } catch (RuntimeException ignored) {
            // State remains retryable; diagnostics are intentionally secret-free.
        }
    }
}
