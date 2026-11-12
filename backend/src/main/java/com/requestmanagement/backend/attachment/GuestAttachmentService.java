package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKey;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKeyGenerator;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectStorage;
import com.requestmanagement.backend.guesttracking.GuestTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GuestAttachmentService {
    private final GuestTrackingService tracking;
    private final AttachmentProcessingProperties processing;
    private final AttachmentUploadProperties uploadProperties;
    private final ObjectProvider<AttachmentObjectStorage> storageProvider;
    private final AttachmentObjectKeyGenerator keys;
    private final AttachmentFileValidator validator;
    private final AttachmentLifecycleService lifecycle;
    private final TaskAttachmentRepository attachments;
    private final InternalAttachmentService internalDownloads;
    private final GuestAttachmentRateLimiter rateLimiter;

    public AttachmentMetadataResponse upload(String token, MultipartFile multipart) {
        if (!processing.isGuestUploadEnabled() || !uploadProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Guest attachment uploads are not enabled.");
        }
        var scope = tracking.attachmentScope(token, false);
        if (!scope.projectActive()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Archived projects are read-only.");
        rateLimiter.acquire(scope.sessionId(), processing.getGuestUploadsPerHour());
        AttachmentObjectStorage storage = requireStorage();
        try (ValidatedAttachmentFile file = validator.validate(multipart)) {
            AttachmentObjectKey key = keys.generate().quarantine();
            TaskAttachment reservation = lifecycle.reserveGuest(scope.projectId(), scope.requestId(),
                    scope.sessionId(), file.filename(), file.declaredMimeType(), key);
            try (var input = file.openStream()) {
                storage.uploadQuarantine(key, input, file.size(), file.detectedMimeType(), Map.of(
                        "sha256", file.sha256(), "attachment-id", reservation.getId().toString()));
                AttachmentMetadataResponse response = AttachmentMetadataResponse.from(
                        lifecycle.markQuarantined(scope.projectId(), scope.requestId(), reservation.getId(),
                                file.detectedMimeType(), file.size(), file.sha256()));
                tracking.touchSession(token);
                return response;
            } catch (Exception failure) {
                compensate(reservation, key, storage);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Guest attachment upload failed safely.");
            }
        } catch (AttachmentFileValidationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    public List<AttachmentMetadataResponse> list(String token) {
        var scope = tracking.attachmentScope(token, false);
        return attachments.findGuestVisible(scope.requestId()).stream()
                .map(AttachmentMetadataResponse::from).toList();
    }

    public ResponseEntity<StreamingResponseBody> download(String token, Long attachmentId) {
        var scope = tracking.attachmentScope(token, false);
        return internalDownloads.guestDownload(scope.projectId(), scope.requestId(), attachmentId);
    }

    private AttachmentObjectStorage requireStorage() {
        AttachmentObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Attachment storage is not configured.");
        return storage;
    }

    private void compensate(TaskAttachment attachment, AttachmentObjectKey key, AttachmentObjectStorage storage) {
        try {
            storage.delete(key);
            lifecycle.recordUploadFailure(attachment.getProjectId(), attachment.getRequestId(), attachment.getId());
        } catch (RuntimeException cleanupFailure) {
            lifecycle.markDeletionPending(attachment.getProjectId(), attachment.getRequestId(), attachment.getId());
        }
    }
}
