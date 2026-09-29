package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.CommentResponse;

import java.time.LocalDateTime;
import java.util.List;

public record AdminRequestDetailsResponse(
        Long id, String title, RequestStatus status,
        String requesterName, String requesterEmail,
        String typeName, RequestPriority priority,
        Long assignedToId, String assignedToName,
        String description, LocalDateTime createdAt, LocalDateTime updatedAt,
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
                request.getCreatedBy().getFullName(), request.getCreatedBy().getEmail(),
                request.getType().getTypeName(), request.getPriority(),
                request.getAssignedTo() == null ? null : request.getAssignedTo().getId(),
                request.getAssignedTo() == null ? null : request.getAssignedTo().getFullName(),
                request.getDescription(), request.getCreatedAt(), request.getUpdatedAt(),
                timeline, comments, internalNotes
        );
    }
}
