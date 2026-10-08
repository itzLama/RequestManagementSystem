package com.requestmanagement.backend.guest;

import com.requestmanagement.backend.project.Project;

public record PublicProjectResponse(Long id, String name) {
    public static PublicProjectResponse from(Project project) {
        return new PublicProjectResponse(project.getId(), project.getName());
    }
}
