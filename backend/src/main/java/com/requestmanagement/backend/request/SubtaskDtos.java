package com.requestmanagement.backend.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public final class SubtaskDtos {
    private SubtaskDtos() { }

    public record CreateInput(
            @NotBlank @Size(max = 200) String title,
            String description,
            JsonNode descriptionDocument,
            @NotNull @Positive Long workTypeId,
            @NotNull RequestPriority priority,
            @NotNull @Positive Long assignedToId,
            @NotNull @Positive Long statusId) { }

    public record UpdateInput(
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotNull @Positive Long workTypeId,
            @NotNull RequestPriority priority) { }

    public record Summary(Long id, String title, Long statusId, String statusName,
                          String statusSemantic, Long assignedToId, String assignedToName,
                          boolean countsAsCompleted, java.time.LocalDate startDate,
                          java.time.LocalDate dueDate) {
        static Summary from(Request task) {
            return new Summary(task.getId(), task.getTitle(), task.getProjectStatus().getId(),
                    task.getProjectStatus().getName(),
                    task.getProjectStatus().getSemanticKey() == null ? null : task.getProjectStatus().getSemanticKey().name(),
                    task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                    task.getAssignedTo() == null ? null : task.getAssignedTo().getFullName(),
                    task.getProjectStatus().isCountsAsCompleted(), task.getStartDate(), task.getDueDate());
        }
    }

    public record Progress(int completed, int total, int percentage) {
        static Progress from(java.util.List<Request> tasks) {
            int completed = (int) tasks.stream().filter(t -> t.getProjectStatus().isCountsAsCompleted()).count();
            int total = tasks.size();
            int percentage = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
            return new Progress(completed, total, percentage);
        }
    }
}
