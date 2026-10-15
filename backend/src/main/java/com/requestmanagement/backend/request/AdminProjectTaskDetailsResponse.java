package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.project.ProjectStatus;

import java.time.LocalDateTime;
import java.util.List;
import com.requestmanagement.backend.guestform.GuestFormDtos.AnswerResponse;

public record AdminProjectTaskDetailsResponse(
        Long id, String title, String description, Long workTypeId, String workTypeName,
        RequestPriority priority, RequestStatus status,
        Long assignedToId, String assignedToName,
        Long createdById, String createdByName, String requesterName, String requesterEmail, boolean guest,
        Long projectId, String projectName, ProjectStatus projectStatus,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        List<RequestDetailsResponse.TimelineEntry> timeline,
        List<CommentResponse> comments,
        List<CommentResponse> internalNotes,
        List<AnswerResponse> guestAnswers
) {
    public static AdminProjectTaskDetailsResponse from(
            Request task, List<RequestDetailsResponse.TimelineEntry> timeline,
            List<CommentResponse> comments, List<CommentResponse> internalNotes, List<AnswerResponse> guestAnswers
    ) {
        return new AdminProjectTaskDetailsResponse(
                task.getId(), task.getTitle(), task.getDescription(), task.getWorkType().getId(), task.getWorkType().getName(),
                task.getPriority(), task.getStatus(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getFullName(),
                task.getCreatedBy() == null ? null : task.getCreatedBy().getId(),
                task.getCreatedBy() == null ? null : task.getCreatedBy().getFullName(),
                task.requesterName(), task.requesterEmail(), task.isGuest(),
                task.getProject().getId(), task.getProject().getName(), task.getProject().getStatus(),
                task.getCreatedAt(), task.getUpdatedAt(), timeline, comments, internalNotes, guestAnswers);
    }
}
