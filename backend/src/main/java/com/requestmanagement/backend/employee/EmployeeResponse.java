package com.requestmanagement.backend.employee;

import com.requestmanagement.backend.user.User;

import java.time.LocalDateTime;

public record EmployeeResponse(Long id, String fullName, String email, boolean active,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static EmployeeResponse from(User employee) {
        return new EmployeeResponse(employee.getId(), employee.getFullName(), employee.getEmail(),
                employee.isActive(), employee.getCreatedAt(), employee.getUpdatedAt());
    }
}
