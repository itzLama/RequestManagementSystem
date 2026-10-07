package com.requestmanagement.backend.project;

public record ProjectTaskMemberResponse(Long employeeId, String fullName, String email, boolean active) {
    public static ProjectTaskMemberResponse from(ProjectMembership membership) {
        return new ProjectTaskMemberResponse(membership.getEmployee().getId(),
                membership.getEmployee().getFullName(), membership.getEmployee().getEmail(),
                membership.getEmployee().isActive());
    }
}
