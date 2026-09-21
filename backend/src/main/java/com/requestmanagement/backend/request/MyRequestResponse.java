package com.requestmanagement.backend.request;

import java.time.LocalDateTime;

public record MyRequestResponse(
        Long id,
        String title,
        String typeName,
        RequestPriority priority,
        RequestStatus status,
        LocalDateTime createdAt
) {
    public static MyRequestResponse from(Request request) {
        return new MyRequestResponse(
                request.getId(), request.getTitle(), request.getType().getTypeName(),
                request.getPriority(), request.getStatus(), request.getCreatedAt()
        );
    }
}
