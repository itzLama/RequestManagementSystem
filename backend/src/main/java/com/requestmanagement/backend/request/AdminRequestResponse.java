package com.requestmanagement.backend.request;

import java.time.LocalDateTime;

public record AdminRequestResponse(
        Long id,
        String title,
        String requesterName,
        String typeName,
        RequestPriority priority,
        RequestStatus status,
        LocalDateTime createdAt,
        Long projectId,
        String projectName,
        ProjectWorkType workType,
        Long assignedToId,
        String assignedToName,
        boolean guest
) {
    public static AdminRequestResponse from(Request request) {
        return new AdminRequestResponse(
                request.getId(),
                request.getTitle(),
                request.requesterName(),
                request.getType() == null ? null : request.getType().getTypeName(),
                request.getPriority(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getProject() == null ? null : request.getProject().getId(),
                request.getProject() == null ? null : request.getProject().getName(),
                request.getWorkType(),
                request.getAssignedTo() == null ? null : request.getAssignedTo().getId(),
                request.getAssignedTo() == null ? null : request.getAssignedTo().getFullName(),
                request.isGuest()
        );
    }
}
