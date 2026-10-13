package com.requestmanagement.backend.projectworktype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectWorkTypeNameRequest(
        @NotBlank(message = "Work Type name is required.")
        @Size(max = 100, message = "Work Type name must be at most 100 characters.")
        String name
) { }
