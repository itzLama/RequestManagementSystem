package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKey;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Internal metadata state machine. It deliberately performs no authorization and no object-store I/O.
 * Future user-facing commands must authorize first, then invoke this boundary and compensate storage
 * failures explicitly; a PostgreSQL transaction cannot be atomic with RustFS.
 */
@Service
@RequiredArgsConstructor
public class AttachmentLifecycleService {
    private static final int MAX_ATTACHMENTS = 10;
    private static final long MAX_SIZE_BYTES = 25L * 1024 * 1024;

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final TaskAttachmentVisibilityEventRepository visibilityEventRepository;

    @Transactional
    TaskAttachment reserveInternal(Long projectId, Long requestId, Long uploaderId,
                                   String sanitizedFilename, String declaredMimeType,
                                   AttachmentObjectKey quarantineKey) {
        Request task = lockedTask(projectId, requestId);
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> invalidOwner("Internal uploader was not found."));
        TaskAttachment attachment = TaskAttachment.reserveInternal(task, nextSlot(requestId),
                filename(sanitizedFilename), mime(declaredMimeType), quarantineKey(quarantineKey), uploader);
        return saveReservation(attachment);
    }

    @Transactional
    TaskAttachment reserveGuest(Long projectId, Long requestId, UUID guestSessionId,
                                String sanitizedFilename, String declaredMimeType,
                                AttachmentObjectKey quarantineKey) {
        Request task = lockedTask(projectId, requestId);
        if (!task.isGuest() || task.isSubtask()) {
            throw invalidOwner("Guest provenance requires a Guest-created root Project Task.");
        }
        if (guestSessionId == null) throw invalidMetadata("Guest session provenance is required.");
        TaskAttachment attachment = TaskAttachment.reserveGuest(task, nextSlot(requestId),
                filename(sanitizedFilename), mime(declaredMimeType), quarantineKey(quarantineKey), guestSessionId);
        return saveReservation(attachment);
    }

    @Transactional(readOnly = true)
    List<TaskAttachment> activeMetadata(Long projectId, Long requestId) {
        requireTask(projectId, requestId);
        return attachmentRepository.findActiveScoped(projectId, requestId);
    }

    @Transactional
    TaskAttachment markQuarantined(Long projectId, Long requestId, Long attachmentId,
                                   String detectedMimeType, long sizeBytes, String sha256Digest) {
        if (sizeBytes > MAX_SIZE_BYTES) throw invalidMetadata("Attachment exceeds the 25 MB limit.");
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        attachment.markQuarantined(mimeRequired(detectedMimeType), sizeBytes,
                digest(sha256Digest));
        return attachment;
    }

    @Transactional
    TaskAttachment recordScanOutcome(Long projectId, Long requestId, Long attachmentId,
                                     TaskAttachment.ScanStatus result, String scannerName, String details) {
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        attachment.recordScan(result, scannerName, details);
        return attachment;
    }

    @Transactional
    TaskAttachment markAvailable(Long projectId, Long requestId, Long attachmentId,
                                 AttachmentObjectKey availableKey) {
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        if (availableKey == null || !availableKey.isAvailable()) {
            throw invalidMetadata("An available-namespace object key is required.");
        }
        attachment.markAvailable(availableKey.value());
        return attachment;
    }

    @Transactional
    TaskAttachment claimForScan(Long projectId, Long requestId, Long attachmentId, UUID token,
                                int maxAttempts, Duration staleAfter) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = attachmentRepository.claimForScan(projectId, requestId, attachmentId, token, now,
                now.minus(staleAfter), maxAttempts);
        if (claimed != 1) {
            throw new AttachmentLifecycleException(AttachmentLifecycleException.Reason.INVALID_TRANSITION,
                    "Attachment is not currently eligible for scanning.");
        }
        return attachmentRepository.findByIdAndProjectIdAndRequestId(attachmentId, projectId, requestId)
                .orElseThrow(() -> new AttachmentLifecycleException(AttachmentLifecycleException.Reason.NOT_FOUND,
                        "Attachment metadata was not found for this Task."));
    }

    @Transactional
    TaskAttachment finalizeCleanScan(Long projectId, Long requestId, Long attachmentId, UUID token,
                                     String scannerName, String details, AttachmentObjectKey availableKey) {
        if (availableKey == null || !availableKey.isAvailable()) {
            throw invalidMetadata("An available-namespace object key is required.");
        }
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        attachment.finishCleanScan(token, scannerName, details, availableKey.value());
        return attachment;
    }

    @Transactional
    TaskAttachment finalizeFailedScan(Long projectId, Long requestId, Long attachmentId, UUID token,
                                      TaskAttachment.ScanStatus status, String scannerName, String details,
                                      LocalDateTime retryAt) {
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        attachment.finishFailedScan(token, status, scannerName, details, retryAt);
        return attachment;
    }

    @Transactional
    void releaseScanClaim(Long projectId, Long requestId, Long attachmentId, UUID token) {
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        attachment.releaseScanClaim(token);
    }

    @Transactional(readOnly = true)
    TaskAttachment scopedMetadata(Long projectId, Long requestId, Long attachmentId) {
        requireTask(projectId, requestId);
        return attachmentRepository.findByIdAndProjectIdAndRequestId(attachmentId, projectId, requestId)
                .orElseThrow(() -> new AttachmentLifecycleException(AttachmentLifecycleException.Reason.NOT_FOUND,
                        "Attachment metadata was not found for this Task."));
    }

    @Transactional
    TaskAttachment recordUploadFailure(Long projectId, Long requestId, Long attachmentId) {
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        attachment.recordUploadFailure();
        return attachment;
    }

    @Transactional
    TaskAttachment markDeletionPending(Long projectId, Long requestId, Long attachmentId) {
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        attachment.markDeletionPending();
        return attachment;
    }

    @Transactional
    TaskAttachment finalizeDeletion(Long projectId, Long requestId, Long attachmentId) {
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        attachment.finalizeDeletion();
        return attachment;
    }

    @Transactional
    TaskAttachment claimCleanup(Long attachmentId, UUID token) {
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentLifecycleException(AttachmentLifecycleException.Reason.NOT_FOUND,
                        "Attachment metadata was not found."));
        attachment.claimCleanup(token);
        return attachment;
    }

    @Transactional
    TaskAttachment recordCleanupFailure(Long attachmentId, UUID token, String details, LocalDateTime retryAt) {
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentLifecycleException(AttachmentLifecycleException.Reason.NOT_FOUND,
                        "Attachment metadata was not found."));
        attachment.recordCleanupFailure(token, details, retryAt);
        return attachment;
    }

    @Transactional
    TaskAttachment finalizeClaimedDeletion(Long attachmentId, UUID token) {
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentLifecycleException(AttachmentLifecycleException.Reason.NOT_FOUND,
                        "Attachment metadata was not found."));
        if (!token.equals(attachment.getCleanupClaimToken())) {
            throw new AttachmentLifecycleException(AttachmentLifecycleException.Reason.INVALID_TRANSITION,
                    "Attachment cleanup claim is no longer valid.");
        }
        attachment.finalizeDeletion();
        return attachment;
    }

    @Transactional
    TaskAttachment finalizeFailedReservation(Long attachmentId) {
        TaskAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentLifecycleException(AttachmentLifecycleException.Reason.NOT_FOUND,
                        "Attachment metadata was not found."));
        attachment.finalizeFailedReservation();
        return attachment;
    }

    @Transactional
    TaskAttachment changeGuestVisibility(Long projectId, Long requestId, Long attachmentId,
                                         Long actorId, TaskAttachment.Visibility target) {
        Request task = lockedTask(projectId, requestId);
        if (!task.isGuest() || task.isSubtask()) {
            throw invalidOwner("Guest visibility is limited to the exact Guest-created root Task.");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> invalidOwner("Visibility actor was not found."));
        TaskAttachment attachment = lockedAttachment(projectId, requestId, attachmentId);
        TaskAttachment.Visibility previous = attachment.getVisibility();
        if (previous == target) return attachment;
        attachment.changeVisibility(target);
        visibilityEventRepository.save(TaskAttachmentVisibilityEvent.create(attachment, previous, target, actor));
        return attachment;
    }

    private TaskAttachment saveReservation(TaskAttachment attachment) {
        try {
            return attachmentRepository.saveAndFlush(attachment);
        } catch (DataIntegrityViolationException exception) {
            if (isSlotConflict(exception)) {
                throw new AttachmentLifecycleException(AttachmentLifecycleException.Reason.CAPACITY_EXCEEDED,
                        "Attachment capacity is no longer available for this Task.");
            }
            throw exception;
        }
    }

    private boolean isSlotConflict(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().contains("uq_task_attachments_reserved_slot")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Request lockedTask(Long projectId, Long requestId) {
        Request task = requestRepository.findForAttachmentReservation(projectId, requestId)
                .orElseThrow(() -> invalidOwner("Attachment owner must be a Project Task in the specified Project."));
        if (task.getAttachmentCleanupStartedAt() != null) {
            throw invalidTransition("Task permanent deletion is already in progress.");
        }
        return task;
    }

    @Transactional
    public void beginPermanentDeletion(List<Long> requestIds) {
        requestRepository.markAttachmentCleanupStarted(requestIds);
    }

    private Request requireTask(Long projectId, Long requestId) {
        return requestRepository.findByIdAndProject_Id(requestId, projectId)
                .orElseThrow(() -> invalidOwner("Attachment owner must be a Project Task in the specified Project."));
    }

    private TaskAttachment lockedAttachment(Long projectId, Long requestId, Long attachmentId) {
        return attachmentRepository.findScopedForUpdate(projectId, requestId, attachmentId)
                .orElseThrow(() -> new AttachmentLifecycleException(AttachmentLifecycleException.Reason.NOT_FOUND,
                        "Attachment metadata was not found for this Task."));
    }

    private short nextSlot(Long requestId) {
        Set<Short> reserved = new HashSet<>(attachmentRepository.findReservedSlots(requestId));
        for (short slot = 1; slot <= MAX_ATTACHMENTS; slot++) {
            if (!reserved.contains(slot)) return slot;
        }
        throw new AttachmentLifecycleException(AttachmentLifecycleException.Reason.CAPACITY_EXCEEDED,
                "A Task can reserve at most 10 attachments.");
    }

    private static String filename(String value) {
        if (value == null) throw invalidMetadata("A sanitized filename is required.");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 255 || normalized.contains("/")
                || normalized.contains("\\") || normalized.contains("..")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw invalidMetadata("The sanitized filename is invalid.");
        }
        return normalized;
    }

    private static String mime(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > 255) throw invalidMetadata("MIME type is too long.");
        return normalized;
    }

    private static String mimeRequired(String value) {
        String normalized = mime(value);
        if (normalized == null) throw invalidMetadata("Detected MIME type is required.");
        return normalized;
    }

    private static String digest(String value) {
        if (value == null) throw invalidMetadata("SHA-256 digest is required.");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) throw invalidMetadata("SHA-256 digest is invalid.");
        return normalized;
    }

    private static String quarantineKey(AttachmentObjectKey key) {
        if (key == null || !key.isQuarantine()) {
            throw invalidMetadata("A quarantine-namespace object key is required.");
        }
        return key.value();
    }

    private static AttachmentLifecycleException invalidOwner(String message) {
        return new AttachmentLifecycleException(AttachmentLifecycleException.Reason.INVALID_OWNER, message);
    }

    private static AttachmentLifecycleException invalidMetadata(String message) {
        return new AttachmentLifecycleException(AttachmentLifecycleException.Reason.INVALID_METADATA, message);
    }

    private static AttachmentLifecycleException invalidTransition(String message) {
        return new AttachmentLifecycleException(AttachmentLifecycleException.Reason.INVALID_TRANSITION, message);
    }
}
