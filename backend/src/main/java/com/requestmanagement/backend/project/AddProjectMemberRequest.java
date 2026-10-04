package com.requestmanagement.backend.project;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddProjectMemberRequest(
        @NotNull(message = "Employee is required.")
        @Positive(message = "Employee must be valid.") Long employeeId
) { }
