package com.requestmanagement.backend.request;

import java.time.LocalDateTime;

public record AssignedTaskResponse(
        Long id,
        String title,
        String typeName,
        RequestPriority priority,
        RequestStatus status,
        LocalDateTime createdAt,
        Long assignedToId,
        String assignedToName
) {
    public static AssignedTaskResponse from(Request request) {
        return new AssignedTaskResponse(
                request.getId(), request.getTitle(), request.getType().getTypeName(),
                request.getPriority(), request.getStatus(), request.getCreatedAt(),
                request.getAssignedTo().getId(), request.getAssignedTo().getFullName()
        );
    }
}
