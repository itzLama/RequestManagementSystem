package com.requestmanagement.backend.project;

import java.time.LocalDateTime;

public record ProjectMemberResponse(Long membershipId, Long employeeId, String fullName,
                                    String email, boolean active, LocalDateTime createdAt) {
    public static ProjectMemberResponse from(ProjectMembership membership) {
        return new ProjectMemberResponse(membership.getId(), membership.getEmployee().getId(),
                membership.getEmployee().getFullName(), membership.getEmployee().getEmail(),
                membership.getEmployee().isActive(), membership.getCreatedAt());
    }
}
