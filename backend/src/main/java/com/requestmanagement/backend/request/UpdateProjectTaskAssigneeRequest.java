package com.requestmanagement.backend.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateProjectTaskAssigneeRequest(
        @NotNull(message = "Assignee is required.")
        @Positive(message = "Assignee ID must be positive.") Long assignedToId
) { }
