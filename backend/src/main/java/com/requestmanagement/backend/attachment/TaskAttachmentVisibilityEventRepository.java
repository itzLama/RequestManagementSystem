package com.requestmanagement.backend.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAttachmentVisibilityEventRepository
        extends JpaRepository<TaskAttachmentVisibilityEvent, Long> {
    List<TaskAttachmentVisibilityEvent> findByAttachment_IdOrderByOccurredAtAscIdAsc(Long attachmentId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TaskAttachmentVisibilityEvent e where e.requestId in :requestIds")
    int deleteByRequestIds(@Param("requestIds") List<Long> requestIds);
}
