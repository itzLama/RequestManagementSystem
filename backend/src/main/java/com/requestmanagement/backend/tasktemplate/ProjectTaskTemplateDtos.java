package com.requestmanagement.backend.tasktemplate;

import com.requestmanagement.backend.request.RequestPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public final class ProjectTaskTemplateDtos {
    private ProjectTaskTemplateDtos() { }

    public record Input(
            @NotBlank(message = "Template name is required.")
            @Size(max = 100, message = "Template name must be at most 100 characters.") String name,
            @NotBlank(message = "Arabic template name is required.")
            @Size(max = 100, message = "Arabic template name must be at most 100 characters.") String nameAr,
            @NotNull(message = "Work Type is required.")
            @Positive(message = "Work Type ID must be positive.") Long workTypeId,
            @NotNull(message = "Priority is required.") RequestPriority priority,
            @NotBlank(message = "Default title is required.")
            @Size(max = 200, message = "Default title must be at most 200 characters.") String defaultTitle,
            @NotNull(message = "Description is required.") JsonNode descriptionDocument,
            @Min(value = 0, message = "Due date offset must be between 0 and 365 days.")
            @Max(value = 365, message = "Due date offset must be between 0 and 365 days.") Integer dueOffsetDays
    ) { }

    public record Response(
            Long id, String name, String nameAr,
            Long workTypeId, String workTypeName, String workTypeNameAr, boolean workTypeActive,
            RequestPriority priority, String defaultTitle, String description, JsonNode descriptionDocument,
            Integer dueOffsetDays, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        static Response from(ProjectTaskTemplate template) {
            var workType = template.getWorkType();
            return new Response(template.getId(), template.getName(), template.getNameAr(),
                    workType.getId(), workType.getName(), workType.getNameAr(), workType.isActive(),
                    template.getPriority(), template.getDefaultTitle(), template.getDescription(),
                    template.getDescriptionDocument(), template.getDueOffsetDays(),
                    template.getCreatedAt(), template.getUpdatedAt());
        }
    }
}
