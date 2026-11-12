package com.requestmanagement.backend.request;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectTaskBoardResponse(
        Long id, String title, Long workTypeId, String workTypeName, String workTypeNameAr, RequestPriority priority,
        RequestStatus status, Long statusId, String statusName, String statusSemantic, Long assignedToId, String assignedToName, LocalDateTime createdAt,
        String requesterName, boolean guest, LocalDate startDate, LocalDate dueDate
) {
    public static ProjectTaskBoardResponse from(Request task) {
        return new ProjectTaskBoardResponse(task.getId(), task.getTitle(), task.getWorkType().getId(),
                task.getWorkType().getName(), task.getWorkType().getNameAr(),
                task.getPriority(), task.getStatus(), task.getProjectStatus().getId(), task.getProjectStatus().getName(),
                task.getProjectStatus().getSemanticKey()==null?null:task.getProjectStatus().getSemanticKey().name(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getFullName(),
                task.getCreatedAt(), task.requesterName(), task.isGuest(),
                task.getStartDate(), task.getDueDate());
    }
}
