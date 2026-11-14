package com.requestmanagement.backend.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;

public record CreateProjectTaskRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be at most 200 characters.") String title,
        String description,
        JsonNode descriptionDocument,
        @NotNull(message = "Work Type is required.")
        @Positive(message = "Work Type ID must be positive.") Long workTypeId,
        @NotNull(message = "Priority is required.") RequestPriority priority,
        @NotNull(message = "Assignee is required.")
        @Positive(message = "Assignee ID must be positive.") Long assignedToId,
        LocalDate startDate,
        LocalDate dueDate,
        /*
         * Sent by the Create Task form only when the user replaced the assignee suggested by a
         * "task created -> assign" automation rule. Those assign actions then leave this Task alone;
         * every other automation action still runs.
         */
        Boolean assigneeManuallySelected
) {
    public boolean keepsChosenAssignee() {
        return Boolean.TRUE.equals(assigneeManuallySelected);
    }
}
