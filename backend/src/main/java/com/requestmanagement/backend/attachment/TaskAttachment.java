package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAttachment {
    public enum UploadSource { INTERNAL, GUEST }
    public enum Visibility { INTERNAL_ONLY, GUEST_VISIBLE }
    public enum ScanStatus { PENDING, CLEAN, INFECTED, ERROR }
    public enum StorageStatus {
        PENDING, QUARANTINED, AVAILABLE, UPLOAD_FAILED, DELETION_PENDING, DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, insertable = false, updatable = false)
    private Project project;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, insertable = false, updatable = false)
    private Request request;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "slot_number", nullable = false)
    private short slotNumber;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "declared_mime_type", length = 255)
    private String declaredMimeType;

    @Column(name = "detected_mime_type", length = 255)
    private String detectedMimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sha256_digest", length = 64)
    private String sha256Digest;

    @Column(name = "object_key", nullable = false, length = 255, unique = true)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_source", nullable = false, length = 20)
    private UploadSource uploadSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;

    @Column(name = "guest_session_id_snapshot")
    private UUID guestSessionIdSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 20)
    private ScanStatus scanStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_status", nullable = false, length = 30)
    private StorageStatus storageStatus;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @Column(name = "scanner_name", length = 100)
    private String scannerName;

    @Column(name = "scan_result_details", length = 1000)
    private String scanResultDetails;

    @Column(name = "scan_claim_token")
    private UUID scanClaimToken;

    @Column(name = "scan_claimed_at")
    private LocalDateTime scanClaimedAt;

    @Column(name = "scan_attempts", nullable = false)
    private int scanAttempts;

    @Column(name = "next_scan_at")
    private LocalDateTime nextScanAt;

    @Column(name = "cleanup_claim_token")
    private UUID cleanupClaimToken;

    @Column(name = "cleanup_claimed_at")
    private LocalDateTime cleanupClaimedAt;

    @Column(name = "cleanup_attempts", nullable = false)
    private int cleanupAttempts;

    @Column(name = "next_cleanup_at")
    private LocalDateTime nextCleanupAt;

    @Column(name = "lifecycle_error", length = 1000)
    private String lifecycleError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    static TaskAttachment reserveInternal(Request task, short slot, String filename, String declaredMimeType,
                                          String objectKey, User uploader) {
        return reserve(task, slot, filename, declaredMimeType, objectKey, UploadSource.INTERNAL,
                uploader, null, Visibility.INTERNAL_ONLY);
    }

    static TaskAttachment reserveGuest(Request task, short slot, String filename, String declaredMimeType,
                                       String objectKey, UUID sessionId) {
        return reserve(task, slot, filename, declaredMimeType, objectKey, UploadSource.GUEST,
                null, sessionId, Visibility.GUEST_VISIBLE);
    }

    private static TaskAttachment reserve(Request task, short slot, String filename, String declaredMimeType,
                                          String objectKey, UploadSource source, User uploader, UUID sessionId,
                                          Visibility visibility) {
        TaskAttachment attachment = new TaskAttachment();
        attachment.projectId = task.getProject().getId();
        attachment.requestId = task.getId();
        attachment.slotNumber = slot;
        attachment.originalFilename = filename;
        attachment.declaredMimeType = declaredMimeType;
        attachment.objectKey = objectKey;
        attachment.uploadSource = source;
        attachment.uploadedBy = uploader;
        attachment.guestSessionIdSnapshot = sessionId;
        attachment.visibility = visibility;
        attachment.scanStatus = ScanStatus.PENDING;
        attachment.storageStatus = StorageStatus.PENDING;
        attachment.createdAt = LocalDateTime.now();
        attachment.updatedAt = attachment.createdAt;
        return attachment;
    }

    void markQuarantined(String detectedMimeType, long sizeBytes, String sha256Digest) {
        requireStorage(StorageStatus.PENDING);
        if (detectedMimeType == null || detectedMimeType.isBlank() || sizeBytes < 0
                || sha256Digest == null || !sha256Digest.matches("[0-9a-f]{64}")) {
            throw invalidMetadata("Complete verified metadata is required after quarantine upload.");
        }
        this.detectedMimeType = detectedMimeType.trim();
        this.sizeBytes = sizeBytes;
        this.sha256Digest = sha256Digest;
        storageStatus = StorageStatus.QUARANTINED;
        touch();
    }

    void recordScan(ScanStatus result, String scannerName, String details) {
        requireStorage(StorageStatus.QUARANTINED);
        if (result == null || result == ScanStatus.PENDING) {
            throw invalidTransition("A terminal scan result is required.");
        }
        this.scanStatus = result;
        this.scannerName = normalizedOptional(scannerName, 100);
        this.scanResultDetails = normalizedOptional(details, 1000);
        this.scannedAt = LocalDateTime.now();
        touch();
    }

    void finishCleanScan(UUID claimToken, String scannerName, String details, String availableObjectKey) {
        requireClaim(claimToken);
        recordScan(ScanStatus.CLEAN, scannerName, details);
        markAvailable(availableObjectKey);
        clearClaim();
    }

    void finishFailedScan(UUID claimToken, ScanStatus result, String scannerName, String details,
                          LocalDateTime retryAt) {
        requireClaim(claimToken);
        if (result != ScanStatus.ERROR && result != ScanStatus.INFECTED) {
            throw invalidTransition("Only scanner errors or infected verdicts may fail processing.");
        }
        recordScan(result, scannerName, details);
        nextScanAt = result == ScanStatus.ERROR ? retryAt : null;
        clearClaim();
    }

    void releaseScanClaim(UUID claimToken) {
        requireClaim(claimToken);
        clearClaim();
        touch();
    }

    void markAvailable(String availableObjectKey) {
        requireStorage(StorageStatus.QUARANTINED);
        if (scanStatus != ScanStatus.CLEAN) {
            throw invalidTransition("Only a clean quarantined attachment can become available.");
        }
        this.objectKey = availableObjectKey;
        storageStatus = StorageStatus.AVAILABLE;
        touch();
    }

    void recordUploadFailure() {
        if (storageStatus == StorageStatus.UPLOAD_FAILED) return;
        requireStorage(StorageStatus.PENDING);
        storageStatus = StorageStatus.UPLOAD_FAILED;
        touch();
    }

    void markDeletionPending() {
        if (storageStatus == StorageStatus.DELETION_PENDING || storageStatus == StorageStatus.DELETED) return;
        if (storageStatus == StorageStatus.UPLOAD_FAILED) {
            throw invalidTransition("Failed reservations have no confirmed object to delete.");
        }
        storageStatus = StorageStatus.DELETION_PENDING;
        scanClaimToken = null;
        scanClaimedAt = null;
        nextScanAt = null;
        touch();
    }

    void claimCleanup(UUID token) {
        if (token == null || cleanupClaimToken != null || storageStatus != StorageStatus.DELETION_PENDING) {
            throw invalidTransition("Attachment is not currently eligible for cleanup.");
        }
        cleanupClaimToken = token;
        cleanupClaimedAt = LocalDateTime.now();
        cleanupAttempts++;
        lifecycleError = null;
        touch();
    }

    void recordCleanupFailure(UUID token, String details, LocalDateTime retryAt) {
        requireCleanupClaim(token);
        lifecycleError = normalizedOptional(details, 1000);
        nextCleanupAt = retryAt;
        clearCleanupClaim();
        touch();
    }

    void finalizeDeletion() {
        if (storageStatus == StorageStatus.DELETED) return;
        requireStorage(StorageStatus.DELETION_PENDING);
        storageStatus = StorageStatus.DELETED;
        deletedAt = LocalDateTime.now();
        nextCleanupAt = null;
        lifecycleError = null;
        clearCleanupClaim();
        touch();
    }

    void finalizeFailedReservation() {
        if (storageStatus == StorageStatus.DELETED) return;
        requireStorage(StorageStatus.UPLOAD_FAILED);
        storageStatus = StorageStatus.DELETED;
        deletedAt = LocalDateTime.now();
        touch();
    }

    Visibility changeVisibility(Visibility target) {
        if (target == null || target == visibility) return visibility;
        if (!isDownloadable()) {
            throw invalidTransition("Only an available attachment can be shared with the Guest.");
        }
        Visibility previous = visibility;
        visibility = target;
        touch();
        return previous;
    }

    public boolean isDownloadable() {
        return storageStatus == StorageStatus.AVAILABLE && scanStatus == ScanStatus.CLEAN;
    }

    private void requireClaim(UUID claimToken) {
        if (claimToken == null || !claimToken.equals(scanClaimToken)) {
            throw invalidTransition("Attachment scan claim is no longer valid.");
        }
    }

    private void clearClaim() {
        scanClaimToken = null;
        scanClaimedAt = null;
    }

    private void requireCleanupClaim(UUID token) {
        if (token == null || !token.equals(cleanupClaimToken)) {
            throw invalidTransition("Attachment cleanup claim is no longer valid.");
        }
    }

    private void clearCleanupClaim() {
        cleanupClaimToken = null;
        cleanupClaimedAt = null;
    }

    private void requireStorage(StorageStatus expected) {
        if (storageStatus != expected) {
            throw invalidTransition("Attachment lifecycle transition is not allowed from " + storageStatus + ".");
        }
    }

    private void touch() {
        updatedAt = LocalDateTime.now();
    }

    private static String normalizedOptional(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static AttachmentLifecycleException invalidTransition(String message) {
        return new AttachmentLifecycleException(AttachmentLifecycleException.Reason.INVALID_TRANSITION, message);
    }

    private static AttachmentLifecycleException invalidMetadata(String message) {
        return new AttachmentLifecycleException(AttachmentLifecycleException.Reason.INVALID_METADATA, message);
    }
}
