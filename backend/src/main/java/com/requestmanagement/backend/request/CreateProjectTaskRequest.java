package com.requestmanagement.backend.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateProjectTaskRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be at most 200 characters.") String title,
        @NotBlank(message = "Description is required.") String description,
        @NotNull(message = "Work type is required.") ProjectWorkType workType,
        @NotNull(message = "Priority is required.") RequestPriority priority,
        @NotNull(message = "Assignee is required.")
        @Positive(message = "Assignee ID must be positive.") Long assignedToId
) { }
