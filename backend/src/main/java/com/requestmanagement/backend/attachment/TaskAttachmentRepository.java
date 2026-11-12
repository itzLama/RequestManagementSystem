package com.requestmanagement.backend.attachment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.UUID;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    boolean existsByRequestId(Long requestId);

    boolean existsByRequestIdIn(List<Long> requestIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TaskAttachment a where a.id=:attachmentId and a.projectId=:projectId and a.requestId=:requestId")
    Optional<TaskAttachment> findScopedForUpdate(@Param("projectId") Long projectId,
                                                 @Param("requestId") Long requestId,
                                                 @Param("attachmentId") Long attachmentId);

    @Query(value = """
            SELECT slot_number FROM task_attachments
             WHERE request_id=:requestId
               AND storage_status IN ('PENDING','QUARANTINED','AVAILABLE','DELETION_PENDING')
             ORDER BY slot_number
            """, nativeQuery = true)
    List<Short> findReservedSlots(@Param("requestId") Long requestId);

    @Query("""
            select a from TaskAttachment a
             where a.projectId=:projectId and a.requestId=:requestId
               and a.storageStatus not in (com.requestmanagement.backend.attachment.TaskAttachment.StorageStatus.UPLOAD_FAILED,
                                           com.requestmanagement.backend.attachment.TaskAttachment.StorageStatus.DELETED)
             order by a.createdAt, a.id
            """)
    List<TaskAttachment> findActiveScoped(@Param("projectId") Long projectId,
                                          @Param("requestId") Long requestId);

    @Query("""
            select a from TaskAttachment a
             where a.requestId=:requestId
               and a.visibility=com.requestmanagement.backend.attachment.TaskAttachment.Visibility.GUEST_VISIBLE
               and a.storageStatus not in (com.requestmanagement.backend.attachment.TaskAttachment.StorageStatus.UPLOAD_FAILED,
                                           com.requestmanagement.backend.attachment.TaskAttachment.StorageStatus.DELETED,
                                           com.requestmanagement.backend.attachment.TaskAttachment.StorageStatus.DELETION_PENDING)
             order by a.createdAt, a.id
            """)
    List<TaskAttachment> findGuestVisible(@Param("requestId") Long requestId);

    Optional<TaskAttachment> findByIdAndProjectIdAndRequestId(Long id, Long projectId, Long requestId);
    boolean existsByObjectKey(String objectKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE task_attachments
               SET scan_claim_token=:token, scan_claimed_at=:now,
                   scan_attempts=scan_attempts + 1, updated_at=:now
             WHERE attachment_id=:attachmentId AND project_id=:projectId AND request_id=:requestId
               AND storage_status='QUARANTINED' AND scan_status IN ('PENDING','ERROR')
               AND scan_attempts < :maxAttempts
               AND (next_scan_at IS NULL OR next_scan_at <= :now)
               AND (scan_claim_token IS NULL OR scan_claimed_at < :staleBefore)
            """, nativeQuery = true)
    int claimForScan(@Param("projectId") Long projectId, @Param("requestId") Long requestId,
                     @Param("attachmentId") Long attachmentId, @Param("token") UUID token,
                     @Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore,
                     @Param("maxAttempts") int maxAttempts);

    @Query(value = """
            SELECT attachment_id FROM task_attachments
             WHERE storage_status='QUARANTINED' AND scan_status IN ('PENDING','ERROR')
               AND scan_attempts < :maxAttempts
               AND (next_scan_at IS NULL OR next_scan_at <= :now)
               AND (scan_claim_token IS NULL OR scan_claimed_at < :staleBefore)
             ORDER BY attachment_id LIMIT :limit
            """, nativeQuery = true)
    List<Long> findScanCandidates(@Param("now") LocalDateTime now,
                                  @Param("staleBefore") LocalDateTime staleBefore,
                                  @Param("maxAttempts") int maxAttempts,
                                  @Param("limit") int limit);

    @Query(value = """
            SELECT attachment_id FROM task_attachments
             WHERE storage_status='DELETION_PENDING'
               AND cleanup_attempts < :maxAttempts
               AND (next_cleanup_at IS NULL OR next_cleanup_at <= :now)
               AND (cleanup_claim_token IS NULL OR cleanup_claimed_at < :staleBefore)
             ORDER BY attachment_id LIMIT :limit
            """, nativeQuery = true)
    List<Long> findCleanupCandidates(@Param("now") LocalDateTime now,
                                     @Param("staleBefore") LocalDateTime staleBefore,
                                     @Param("maxAttempts") int maxAttempts,
                                     @Param("limit") int limit);

    @Query(value = """
            SELECT attachment_id FROM task_attachments
             WHERE storage_status IN ('PENDING','UPLOAD_FAILED') AND created_at < :before
             ORDER BY attachment_id LIMIT :limit
            """, nativeQuery = true)
    List<Long> findStaleUploadCandidates(@Param("before") LocalDateTime before, @Param("limit") int limit);

    @Query("select a from TaskAttachment a where a.id=:id")
    Optional<TaskAttachment> findMetadata(@Param("id") Long id);

    List<TaskAttachment> findByRequestIdIn(List<Long> requestIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from TaskAttachment a where a.requestId in :requestIds and a.storageStatus=com.requestmanagement.backend.attachment.TaskAttachment.StorageStatus.DELETED")
    int deleteDeletedByRequestIds(@Param("requestIds") List<Long> requestIds);
}
