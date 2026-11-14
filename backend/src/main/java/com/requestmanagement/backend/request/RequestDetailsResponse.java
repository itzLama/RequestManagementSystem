package com.requestmanagement.backend.request;

import java.time.LocalDateTime;
import java.util.List;
import com.requestmanagement.backend.comment.CommentResponse;
import com.requestmanagement.backend.statushistory.StatusHistory;

public record RequestDetailsResponse(
        Long id, String title, RequestStatus status, String requesterName, String requesterEmail, boolean guest,
        String typeName, RequestPriority priority, String assignedToName,
        String description, LocalDateTime createdAt,
        List<TimelineEntry> timeline, List<CommentResponse> comments
) {
    public record TimelineEntry(RequestStatus oldStatus, RequestStatus newStatus,
                                Long oldProjectStatusId, String oldProjectStatusName,
                                String oldProjectStatusSemantic,
                                Long newProjectStatusId, String newProjectStatusName, String newProjectStatusSemantic,
                                String changedByName, String changeNote, LocalDateTime changedAt,
                                String automationRuleName, String automationRuleNameAr) {
        public static TimelineEntry from(StatusHistory history) {
            return new TimelineEntry(history.getOldStatus(), history.getNewStatus(),
                    history.getOldProjectStatus()==null?null:history.getOldProjectStatus().getId(), history.getOldProjectStatus()==null?null:history.getOldProjectStatus().getName(),
                    history.getOldProjectStatus()==null||history.getOldProjectStatus().getSemanticKey()==null?null:history.getOldProjectStatus().getSemanticKey().name(),
                    history.getNewProjectStatus()==null?null:history.getNewProjectStatus().getId(), history.getNewProjectStatus()==null?null:history.getNewProjectStatus().getName(),
                    history.getNewProjectStatus()==null||history.getNewProjectStatus().getSemanticKey()==null?null:history.getNewProjectStatus().getSemanticKey().name(),
                    // An automation change is never presented as a person's change.
                    history.getAutomationRuleName() != null ? null : history.getChangedBy().getFullName(),
                    history.getChangeNote(), history.getChangedAt(),
                    history.getAutomationRuleName(), history.getAutomationRuleNameAr());
        }
    }

    public static RequestDetailsResponse from(Request request, List<TimelineEntry> timeline,
                                              List<CommentResponse> comments) {
        return new RequestDetailsResponse(request.getId(), request.getTitle(), request.getStatus(),
                request.requesterName(), request.requesterEmail(), request.isGuest(), request.getType().getTypeName(),
                request.getPriority(), request.getAssignedTo() == null ? null : request.getAssignedTo().getFullName(),
                request.getDescription(), request.getCreatedAt(), timeline, comments);
    }
}
