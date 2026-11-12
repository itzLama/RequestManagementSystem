package com.requestmanagement.backend.request;

import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.project.ProjectStatus;

import java.time.LocalDateTime;
import java.util.List;
import com.requestmanagement.backend.guestform.GuestFormDtos.AnswerResponse;
import tools.jackson.databind.JsonNode;

public record ProjectTaskDetailsResponse(
        Long id, String title, String description, JsonNode descriptionDocument,
        Long workTypeId, String workTypeName, String workTypeNameAr,
        RequestPriority priority, RequestStatus status, Long statusId, String statusName, String statusSemantic,
        Long assignedToId, String assignedToName,
        Long createdById, String createdByName, String requesterName, String requesterEmail, boolean guest,
        Long projectId, String projectName, ProjectStatus projectStatus,
        Long parentTaskId, String parentTaskTitle,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        List<RequestDetailsResponse.TimelineEntry> timeline, List<CommentResponse> comments,
        List<AnswerResponse> guestAnswers, List<SubtaskDtos.Summary> subtasks,
        SubtaskDtos.Progress subtaskProgress
) {
    public static ProjectTaskDetailsResponse from(Request task,
                                                  List<RequestDetailsResponse.TimelineEntry> timeline,
                                                  List<CommentResponse> comments, List<AnswerResponse> guestAnswers,
                                                  List<SubtaskDtos.Summary> subtasks,
                                                  SubtaskDtos.Progress subtaskProgress) {
        return new ProjectTaskDetailsResponse(task.getId(), task.getTitle(), task.getDescription(),
                task.getDescriptionDocument(),
                task.getWorkType().getId(), task.getWorkType().getName(), task.getWorkType().getNameAr(), task.getPriority(), task.getStatus(), task.getProjectStatus().getId(), task.getProjectStatus().getName(), task.getProjectStatus().getSemanticKey()==null?null:task.getProjectStatus().getSemanticKey().name(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getFullName(),
                task.getCreatedBy() == null ? null : task.getCreatedBy().getId(),
                task.getCreatedBy() == null ? null : task.getCreatedBy().getFullName(),
                task.requesterName(), task.requesterEmail(), task.isGuest(),
                task.getProject().getId(), task.getProject().getName(), task.getProject().getStatus(),
                task.getParentTask() == null ? null : task.getParentTask().getId(),
                task.getParentTask() == null ? null : task.getParentTask().getTitle(),
                task.getCreatedAt(), task.getUpdatedAt(), timeline, comments, guestAnswers,
                subtasks, subtaskProgress);
    }
}
