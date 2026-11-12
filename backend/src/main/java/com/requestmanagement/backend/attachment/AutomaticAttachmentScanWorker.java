package com.requestmanagement.backend.attachment;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AutomaticAttachmentScanWorker {
    private final AttachmentProcessingProperties processing;
    private final AttachmentScanningProperties scanning;
    private final TaskAttachmentRepository attachments;
    private final AttachmentScanService scanService;

    @Scheduled(fixedDelayString = "${masar.attachments.processing.interval:30s}")
    public void processBatch() {
        if (!processing.isAutomaticScanningEnabled() || !scanning.isEnabled()) return;
        LocalDateTime now = LocalDateTime.now();
        attachments.findScanCandidates(now, now.minus(scanning.getClaimTimeout()),
                        scanning.getMaxAttempts(), processing.getBatchSize())
                .forEach(id -> {
                    TaskAttachment attachment = attachments.findMetadata(id).orElse(null);
                    if (attachment == null) return;
                    try {
                        scanService.processEligible(attachment.getProjectId(), attachment.getRequestId(), id);
                    } catch (RuntimeException ignored) {
                        // The scan service records a safe retry/error state. One failure never stops the batch.
                    }
                });
    }
}
