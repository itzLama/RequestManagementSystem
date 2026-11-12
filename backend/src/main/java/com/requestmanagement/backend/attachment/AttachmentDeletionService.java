package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKey;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentDeletionService {
    private final ObjectProvider<AttachmentObjectStorage> storageProvider;
    private final AttachmentLifecycleService lifecycle;
    private final TaskAttachmentRepository attachments;
    private final TaskAttachmentVisibilityEventRepository visibilityEvents;
    private final ProjectRepository projects;
    private final RequestRepository requests;
    private final UserRepository users;
    private final ProjectAccessService access;
    private final AttachmentProcessingProperties processing;
    private final AttachmentMetadataPurgeService metadataPurge;

    public void adminDelete(Long projectId, Long taskId, Long attachmentId, Long adminId) {
        requireAdmin(adminId);
        Project project = requireProject(projectId);
        access.requireActive(project);
        requireTask(projectId, taskId);
        delete(projectId, taskId, attachmentId);
    }

    public void employeeDelete(Long projectId, Long taskId, Long attachmentId, Long employeeId) {
        access.requireActiveEmployee(employeeId);
        access.requireMutableProject(projectId, employeeId);
        requireTask(projectId, taskId);
        delete(projectId, taskId, attachmentId);
    }

    public void delete(Long projectId, Long taskId, Long attachmentId) {
        TaskAttachment attachment = lifecycle.scopedMetadata(projectId, taskId, attachmentId);
        if (attachment.getStorageStatus() == TaskAttachment.StorageStatus.DELETED) return;
        if (attachment.getStorageStatus() == TaskAttachment.StorageStatus.UPLOAD_FAILED) {
            lifecycle.finalizeFailedReservation(attachmentId);
            return;
        }
        if (attachment.getStorageStatus() != TaskAttachment.StorageStatus.DELETION_PENDING) {
            attachment = lifecycle.markDeletionPending(projectId, taskId, attachmentId);
        }
        cleanup(attachment.getId());
    }

    public void cleanup(Long attachmentId) {
        AttachmentObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) throw unavailable();
        UUID claim = UUID.randomUUID();
        TaskAttachment attachment;
        try {
            attachment = lifecycle.claimCleanup(attachmentId, claim);
        } catch (AttachmentLifecycleException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        try {
            deleteOwnedCopies(storage, attachment.getObjectKey());
            lifecycle.finalizeClaimedDeletion(attachmentId, claim);
        } catch (RuntimeException exception) {
            LocalDateTime retry = LocalDateTime.now().plus(processing.getCleanupRetryBackoff());
            try { lifecycle.recordCleanupFailure(attachmentId, claim,
                    "Private object cleanup could not be confirmed.", retry); }
            catch (RuntimeException ignored) { }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Attachment deletion is pending private storage cleanup.");
        }
    }

    public void cleanupForPermanentDeletion(List<Long> requestIds) {
        for (TaskAttachment attachment : attachments.findByRequestIdIn(requestIds)) {
            if (attachment.getStorageStatus() != TaskAttachment.StorageStatus.DELETED) {
                delete(attachment.getProjectId(), attachment.getRequestId(), attachment.getId());
            }
        }
        metadataPurge.purgeDeleted(requestIds);
    }

    public void beginPermanentTaskDeletion(List<Long> requestIds) {
        lifecycle.beginPermanentDeletion(requestIds);
    }

    static void deleteOwnedCopies(AttachmentObjectStorage storage, String storedKey) {
        AttachmentObjectKey key = new AttachmentObjectKey(storedKey);
        String suffix = key.value().substring(key.value().indexOf('/') + 1);
        AttachmentObjectKey quarantine = new AttachmentObjectKey("quarantine/" + suffix);
        AttachmentObjectKey available = new AttachmentObjectKey("available/" + suffix);
        storage.delete(quarantine);
        storage.delete(available);
        if (storage.exists(quarantine) || storage.exists(available)) {
            throw new IllegalStateException("Private object cleanup was not confirmed.");
        }
    }

    private Project requireProject(Long id) {
        return projects.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private void requireTask(Long projectId, Long taskId) {
        if (!requests.existsByIdAndProject_Id(taskId, projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found.");
        }
    }

    private void requireAdmin(Long id) {
        users.findById(id).filter(User::isActive).filter(u -> u.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Administrator access is required."));
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Attachment storage is unavailable; deletion remains safely pending.");
    }
}
