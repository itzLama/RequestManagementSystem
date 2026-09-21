package com.requestmanagement.backend.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRequestRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be at most 200 characters.")
        String title,

        @NotBlank(message = "Description is required.")
        String description,

        @NotNull(message = "Request type is required.")
        @Positive(message = "Request type ID must be positive.")
        Long typeId,

        @NotNull(message = "Priority is required.")
        RequestPriority priority
) {
}
