package com.requestmanagement.backend.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEmployeeRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 150, message = "Full name must be at most 150 characters.") String fullName,
        @NotBlank(message = "Email is required.")
        @Size(max = 255, message = "Email must be at most 255 characters.") String email
) { }
