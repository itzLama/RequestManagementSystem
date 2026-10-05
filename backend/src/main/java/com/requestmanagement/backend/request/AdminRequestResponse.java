package com.requestmanagement.backend.request;

import java.time.LocalDateTime;

public record AdminRequestResponse(
        Long id,
        String title,
        String requesterName,
        String typeName,
        RequestPriority priority,
        RequestStatus status,
        LocalDateTime createdAt
) {
    public static AdminRequestResponse from(Request request) {
        return new AdminRequestResponse(
                request.getId(),
                request.getTitle(),
                request.requesterName(),
                request.getType().getTypeName(),
                request.getPriority(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }
}
