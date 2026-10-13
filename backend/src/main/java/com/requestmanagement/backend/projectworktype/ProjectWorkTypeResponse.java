package com.requestmanagement.backend.projectworktype;

public record ProjectWorkTypeResponse(Long id, String name, boolean active) {
    public static ProjectWorkTypeResponse from(ProjectWorkType type) {
        return new ProjectWorkTypeResponse(type.getId(), type.getName(), type.isActive());
    }
}
