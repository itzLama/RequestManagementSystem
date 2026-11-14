package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKey;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKeyGenerator;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectStorage;
import com.requestmanagement.backend.attachmentstorage.AttachmentStorageException;
import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class InternalAttachmentService {
    private final AttachmentUploadProperties uploadProperties;
    private final ObjectProvider<AttachmentObjectStorage> storageProvider;
    private final AttachmentObjectKeyGenerator keyGenerator;
    private final AttachmentFileValidator fileValidator;
    private final AttachmentLifecycleService lifecycle;
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;

    public AttachmentMetadataResponse adminUpload(Long projectId, Long taskId, Long adminId, MultipartFile file) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        requireTask(projectId, taskId);
        return upload(projectId, taskId, admin, file);
    }

    public AttachmentMetadataResponse employeeUpload(Long projectId, Long taskId, Long employeeId,
                                                     MultipartFile file) {
        User employee = projectAccessService.requireActiveEmployee(employeeId);
        projectAccessService.requireMutableProject(projectId, employeeId);
        requireTask(projectId, taskId);
        return upload(projectId, taskId, employee, file);
    }

    public List<AttachmentMetadataResponse> adminList(Long projectId, Long taskId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        requireTask(projectId, taskId);
        return lifecycle.activeMetadata(projectId, taskId).stream()
                .map(AttachmentMetadataResponse::from).toList();
    }

    public List<AttachmentMetadataResponse> employeeList(Long projectId, Long taskId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        requireTask(projectId, taskId);
        return lifecycle.activeMetadata(projectId, taskId).stream()
                .map(AttachmentMetadataResponse::from).toList();
    }

    public ResponseEntity<StreamingResponseBody> adminDownload(Long projectId, Long taskId,
                                                               Long attachmentId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        requireTask(projectId, taskId);
        return download(projectId, taskId, attachmentId);
    }

    public ResponseEntity<StreamingResponseBody> employeeDownload(Long projectId, Long taskId,
                                                                  Long attachmentId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        requireTask(projectId, taskId);
        return download(projectId, taskId, attachmentId);
    }

    ResponseEntity<StreamingResponseBody> guestDownload(Long projectId, Long taskId, Long attachmentId) {
        TaskAttachment attachment = lifecycle.scopedMetadata(projectId, taskId, attachmentId);
        if (attachment.getVisibility() != TaskAttachment.Visibility.GUEST_VISIBLE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found.");
        }
        return download(projectId, taskId, attachmentId);
    }

    private ResponseEntity<StreamingResponseBody> download(Long projectId, Long taskId, Long attachmentId) {
        AttachmentObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Attachment storage is not configured.");
        TaskAttachment attachment;
        try {
            attachment = lifecycle.scopedMetadata(projectId, taskId, attachmentId);
        } catch (AttachmentLifecycleException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found.");
        }
        if (!attachment.isDownloadable() || attachment.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attachment is not available for download.");
        }
        AttachmentObjectKey key;
        try { key = new AttachmentObjectKey(attachment.getObjectKey()); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Attachment storage record is invalid.");
        }
        if (!key.isAvailable()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Attachment is not available for download.");
        try {
            var stored = storage.open(key);
            var metadata = stored.metadata();
            if (metadata.contentLength() != attachment.getSizeBytes()
                    || !attachment.getSha256Digest().equals(metadata.customMetadata().get("sha256"))) {
                stored.close();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Attachment storage verification failed.");
            }
            StreamingResponseBody body = output -> {
                try (stored) { stored.stream().transferTo(output); }
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(attachment.getDetectedMimeType()));
            headers.setContentLength(attachment.getSizeBytes());
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8).build());
            headers.setCacheControl(CacheControl.noStore());
            headers.set("Pragma", "no-cache");
            headers.set("X-Content-Type-Options", "nosniff");
            return ResponseEntity.ok().headers(headers).body(body);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Attachment storage is temporarily unavailable.");
        }
    }

    private AttachmentMetadataResponse upload(Long projectId, Long taskId, User uploader, MultipartFile multipart) {
        if (!uploadProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Attachment uploads are not enabled.");
        }
        AttachmentObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Attachment storage is not configured.");
        }

        try (ValidatedAttachmentFile file = fileValidator.validate(multipart)) {
            AttachmentObjectKey key = keyGenerator.generate().quarantine();
            TaskAttachment reservation = reserve(projectId, taskId, uploader, file, key);
            try (var input = file.openStream()) {
                storage.uploadQuarantine(key, input, file.size(), file.detectedMimeType(), Map.of(
                        "sha256", file.sha256(), "attachment-id", reservation.getId().toString()));
                TaskAttachment quarantined = lifecycle.markQuarantined(projectId, taskId, reservation.getId(),
                        file.detectedMimeType(), file.size(), file.sha256());
                return AttachmentMetadataResponse.from(quarantined);
            } catch (IOException exception) {
                compensate(projectId, taskId, reservation.getId(), key, storage);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Attachment upload stream was interrupted.");
            } catch (RuntimeException exception) {
                compensate(projectId, taskId, reservation.getId(), key, storage);
                throw storageFailure(exception);
            }
        } catch (AttachmentFileValidationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (AttachmentLifecycleException exception) {
            throw lifecycleFailure(exception);
        }
    }

    private TaskAttachment reserve(Long projectId, Long taskId, User uploader,
                                   ValidatedAttachmentFile file, AttachmentObjectKey key) {
        try {
            return lifecycle.reserveInternal(projectId, taskId, uploader.getId(), file.filename(),
                    file.declaredMimeType(), key);
        } catch (AttachmentLifecycleException exception) {
            throw lifecycleFailure(exception);
        }
    }

    private void compensate(Long projectId, Long taskId, Long attachmentId, AttachmentObjectKey key,
                            AttachmentObjectStorage storage) {
        try {
            storage.delete(key);
            lifecycle.recordUploadFailure(projectId, taskId, attachmentId);
        } catch (RuntimeException cleanupFailure) {
            lifecycle.markDeletionPending(projectId, taskId, attachmentId);
        }
    }

    private ResponseStatusException storageFailure(RuntimeException exception) {
        if (exception instanceof ResponseStatusException response) return response;
        if (exception instanceof AttachmentStorageException storageException) {
            HttpStatus status = switch (storageException.failure()) {
                case UNAVAILABLE, UNAUTHORIZED, MISCONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
                case NOT_FOUND, UNEXPECTED -> HttpStatus.BAD_GATEWAY;
            };
            return new ResponseStatusException(status, "Attachment storage is temporarily unavailable. Please try again.");
        }
        if (exception instanceof AttachmentLifecycleException lifecycleException) {
            return lifecycleFailure(lifecycleException);
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Attachment storage is temporarily unavailable. Please try again.");
    }

    private ResponseStatusException lifecycleFailure(AttachmentLifecycleException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case NOT_FOUND, INVALID_OWNER -> HttpStatus.NOT_FOUND;
            case CAPACITY_EXCEEDED, INVALID_METADATA, INVALID_TRANSITION -> HttpStatus.CONFLICT;
        };
        return new ResponseStatusException(status, exception.getMessage());
    }

    private User requireActiveAdmin(Long adminId) {
        return userRepository.findById(adminId).filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Administrator access is required."));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private void requireTask(Long projectId, Long taskId) {
        if (!requestRepository.existsByIdAndProject_Id(taskId, projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found.");
        }
    }
}
