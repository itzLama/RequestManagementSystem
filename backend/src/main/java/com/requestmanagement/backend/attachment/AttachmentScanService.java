package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKey;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectMetadata;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectStorage;
import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentScanService {
    private final AttachmentScanningProperties properties;
    private final ObjectProvider<AttachmentMalwareScanner> scannerProvider;
    private final ObjectProvider<AttachmentObjectStorage> storageProvider;
    private final AttachmentLifecycleService lifecycle;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;

    public AttachmentMetadataResponse adminProcess(Long projectId, Long taskId, Long attachmentId, Long adminId) {
        requireActiveAdmin(adminId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        projectAccessService.requireActive(project);
        requireTask(projectId, taskId);
        return processEligible(projectId, taskId, attachmentId);
    }

    AttachmentMetadataResponse processEligible(Long projectId, Long taskId, Long attachmentId) {
        if (requestRepository.isAttachmentCleanupStarted(taskId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task permanent deletion is in progress.");
        }
        if (!properties.isEnabled()) throw unavailable("Attachment malware scanning is not enabled.");
        AttachmentMalwareScanner scanner = scannerProvider.getIfAvailable();
        AttachmentObjectStorage storage = storageProvider.getIfAvailable();
        if (scanner == null || storage == null) throw unavailable("Attachment processing is unavailable.");

        UUID claim = UUID.randomUUID();
        TaskAttachment attachment;
        try {
            attachment = lifecycle.claimForScan(projectId, taskId, attachmentId, claim,
                    properties.getMaxAttempts(), properties.getClaimTimeout());
        } catch (AttachmentLifecycleException exception) {
            throw lifecycleError(exception);
        }
        return process(attachment, claim, scanner, storage);
    }

    private AttachmentMetadataResponse process(TaskAttachment attachment, UUID claim,
                                               AttachmentMalwareScanner scanner,
                                               AttachmentObjectStorage storage) {
        Long projectId = attachment.getProjectId();
        Long taskId = attachment.getRequestId();
        AttachmentObjectKey quarantine = new AttachmentObjectKey(attachment.getObjectKey());
        AttachmentObjectKey available = new AttachmentObjectKey(
                "available/" + quarantine.value().substring("quarantine/".length()));
        try {
            verifyMetadata(attachment, storage.metadata(quarantine));
            MalwareScanResult result;
            String digest;
            try (var stored = storage.open(quarantine)) {
                verifyMetadata(attachment, stored.metadata());
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                try (DigestInputStream input = new DigestInputStream(stored.stream(), sha256)) {
                    result = scanner.scan(input, attachment.getSizeBytes());
                }
                digest = HexFormat.of().formatHex(sha256.digest());
            }
            if (result.bytesScanned() != attachment.getSizeBytes()
                    || !digest.equals(attachment.getSha256Digest())) {
                result = MalwareScanResult.error("Stored attachment integrity verification failed.",
                        result.bytesScanned());
            }
            if (result.verdict() == MalwareScanResult.Verdict.ERROR) {
                return AttachmentMetadataResponse.from(recordError(attachment, claim, result));
            }
            if (result.verdict() == MalwareScanResult.Verdict.INFECTED) {
                TaskAttachment infected = lifecycle.finalizeFailedScan(projectId, taskId, attachment.getId(), claim,
                        TaskAttachment.ScanStatus.INFECTED, result.scannerName(), result.details(), null);
                deleteInfectedBestEffort(infected, quarantine, storage);
                return AttachmentMetadataResponse.from(infected);
            }

            storage.promote(quarantine, available);
            verifyMetadata(attachment, storage.metadata(available));
            TaskAttachment clean = lifecycle.finalizeCleanScan(projectId, taskId, attachment.getId(), claim,
                    result.scannerName(), result.details(), available);
            try { storage.delete(quarantine); } catch (RuntimeException ignored) {
                // The verified available object is authoritative. Step 4B.7 reconciliation removes stale copies.
            }
            return AttachmentMetadataResponse.from(clean);
        } catch (Exception exception) {
            try { recordError(attachment, claim,
                    MalwareScanResult.error("Attachment processing failed safely.", 0)); }
            catch (RuntimeException ignored) { tryRelease(attachment, claim); }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Attachment processing failed safely.");
        }
    }

    private TaskAttachment recordError(TaskAttachment attachment, UUID claim, MalwareScanResult result) {
        long multiplier = 1L << Math.min(Math.max(attachment.getScanAttempts() - 1, 0), 10);
        LocalDateTime retry = LocalDateTime.now().plus(properties.getRetryBackoff().multipliedBy(multiplier));
        return lifecycle.finalizeFailedScan(attachment.getProjectId(), attachment.getRequestId(), attachment.getId(),
                claim, TaskAttachment.ScanStatus.ERROR, result.scannerName(), result.details(), retry);
    }

    private void deleteInfectedBestEffort(TaskAttachment attachment, AttachmentObjectKey key,
                                          AttachmentObjectStorage storage) {
        try {
            storage.delete(key);
            lifecycle.markDeletionPending(attachment.getProjectId(), attachment.getRequestId(), attachment.getId());
            lifecycle.finalizeDeletion(attachment.getProjectId(), attachment.getRequestId(), attachment.getId());
        } catch (RuntimeException ignored) {
            // Remains INFECTED and unavailable; Step 4B.7 may reconcile deletion.
        }
    }

    private void tryRelease(TaskAttachment attachment, UUID claim) {
        try { lifecycle.releaseScanClaim(attachment.getProjectId(), attachment.getRequestId(), attachment.getId(), claim); }
        catch (RuntimeException ignored) { }
    }

    private void verifyMetadata(TaskAttachment attachment, AttachmentObjectMetadata metadata) {
        String digest = metadata.customMetadata().get("sha256");
        if (metadata.contentLength() != attachment.getSizeBytes()
                || digest == null || !digest.equals(attachment.getSha256Digest())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Attachment storage metadata does not match the trusted record.");
        }
    }

    private void requireTask(Long projectId, Long taskId) {
        if (!requestRepository.existsByIdAndProject_Id(taskId, projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found.");
        }
    }

    private void requireActiveAdmin(Long id) {
        userRepository.findById(id).filter(User::isActive).filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Administrator access is required."));
    }

    private ResponseStatusException lifecycleError(AttachmentLifecycleException exception) {
        HttpStatus status = exception.getReason() == AttachmentLifecycleException.Reason.NOT_FOUND
                || exception.getReason() == AttachmentLifecycleException.Reason.INVALID_OWNER
                ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return new ResponseStatusException(status, exception.getMessage());
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
