package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.project.ProjectStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminRequestDetailsResponse(
        Long id, String title, RequestStatus status,
        String requesterName, String requesterEmail,
        String typeName, RequestPriority priority,
        Long assignedToId, String assignedToName,
        String description, LocalDateTime createdAt, LocalDateTime updatedAt,
        Long projectId, String projectName, ProjectStatus projectStatus,
        ProjectWorkType workType, Long createdById, String createdByName,
        List<RequestDetailsResponse.TimelineEntry> timeline,
        List<CommentResponse> comments,
        List<CommentResponse> internalNotes
) {
    public static AdminRequestDetailsResponse from(
            Request request,
            List<RequestDetailsResponse.TimelineEntry> timeline,
            List<CommentResponse> comments,
            List<CommentResponse> internalNotes
    ) {
        return new AdminRequestDetailsResponse(
                request.getId(), request.getTitle(), request.getStatus(),
                request.requesterName(), request.requesterEmail(),
                request.getType() == null ? null : request.getType().getTypeName(), request.getPriority(),
                request.getAssignedTo() == null ? null : request.getAssignedTo().getId(),
                request.getAssignedTo() == null ? null : request.getAssignedTo().getFullName(),
                request.getDescription(), request.getCreatedAt(), request.getUpdatedAt(),
                request.getProject() == null ? null : request.getProject().getId(),
                request.getProject() == null ? null : request.getProject().getName(),
                request.getProject() == null ? null : request.getProject().getStatus(),
                request.getWorkType(),
                request.getCreatedBy() == null ? null : request.getCreatedBy().getId(),
                request.getCreatedBy() == null ? null : request.getCreatedBy().getFullName(),
                timeline, comments, internalNotes
        );
    }
}
