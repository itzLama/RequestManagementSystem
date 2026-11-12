package com.requestmanagement.backend.attachment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
class AttachmentMetadataPurgeService {
    private final TaskAttachmentRepository attachments;
    private final TaskAttachmentVisibilityEventRepository visibilityEvents;

    @Transactional
    void purgeDeleted(List<Long> requestIds) {
        if (attachments.findByRequestIdIn(requestIds).stream()
                .anyMatch(a -> a.getStorageStatus() != TaskAttachment.StorageStatus.DELETED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Task attachments are still awaiting private storage cleanup.");
        }
        visibilityEvents.deleteByRequestIds(requestIds);
        attachments.deleteDeletedByRequestIds(requestIds);
        attachments.flush();
    }
}
