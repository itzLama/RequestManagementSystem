package com.requestmanagement.backend.tasklink;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class ProjectTaskLinkDtos {
    private ProjectTaskLinkDtos() {}

    public enum PerspectiveType { BLOCKS, IS_BLOCKED_BY, DUPLICATES, IS_DUPLICATED_BY, RELATES_TO }

    public record CreateInput(@NotNull Long relatedTaskId, @NotNull PerspectiveType relationshipType) {}

    public record Response(Long id, PerspectiveType relationshipType, String relationshipLabel,
                           Long relatedTaskId, String relatedTaskTitle, boolean relatedTaskSubtask,
                           LocalDateTime createdAt) {}

    public record Candidate(Long id, String title, boolean subtask) {}
}
