package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.project.ProjectStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminProjectTaskDetailsResponse(
        Long id, String title, String description, ProjectWorkType workType,
        RequestPriority priority, RequestStatus status,
        Long assignedToId, String assignedToName,
        Long createdById, String createdByName,
        Long projectId, String projectName, ProjectStatus projectStatus,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        List<RequestDetailsResponse.TimelineEntry> timeline,
        List<CommentResponse> comments,
        List<CommentResponse> internalNotes
) {
    public static AdminProjectTaskDetailsResponse from(
            Request task, List<RequestDetailsResponse.TimelineEntry> timeline,
            List<CommentResponse> comments, List<CommentResponse> internalNotes
    ) {
        return new AdminProjectTaskDetailsResponse(
                task.getId(), task.getTitle(), task.getDescription(), task.getWorkType(),
                task.getPriority(), task.getStatus(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getFullName(),
                task.getCreatedBy().getId(), task.getCreatedBy().getFullName(),
                task.getProject().getId(), task.getProject().getName(), task.getProject().getStatus(),
                task.getCreatedAt(), task.getUpdatedAt(), timeline, comments, internalNotes);
    }
}
