package com.requestmanagement.backend.request;

import java.time.LocalDateTime;

public record CreateRequestResponse(
        Long id,
        String title,
        String description,
        Long typeId,
        String typeName,
        RequestPriority priority,
        RequestStatus status,
        Long createdByUserId,
        Long assignedToUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CreateRequestResponse from(Request request) {
        return new CreateRequestResponse(
                request.getId(), request.getTitle(), request.getDescription(),
                request.getType().getId(), request.getType().getTypeName(),
                request.getPriority(), request.getStatus(), request.getCreatedBy().getId(),
                request.getAssignedTo() == null ? null : request.getAssignedTo().getId(),
                request.getCreatedAt(), request.getUpdatedAt()
        );
    }
}
