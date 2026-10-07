package com.requestmanagement.backend.request;

import java.time.LocalDateTime;

public record ProjectTaskBoardResponse(
        Long id, String title, ProjectWorkType workType, RequestPriority priority,
        RequestStatus status, Long assignedToId, String assignedToName, LocalDateTime createdAt
) {
    public static ProjectTaskBoardResponse from(Request task) {
        return new ProjectTaskBoardResponse(task.getId(), task.getTitle(), task.getWorkType(),
                task.getPriority(), task.getStatus(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getFullName(),
                task.getCreatedAt());
    }
}
