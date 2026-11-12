package com.requestmanagement.backend.planning;

import com.requestmanagement.backend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProjectRoadmapController {
    private final ProjectRoadmapService service;

    /** Admin Global Roadmap: every Project. URL rules already restrict /api/projects/** to admins. */
    @GetMapping("/api/projects/roadmap")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectRoadmapDtos.Response adminRoadmap(@AuthenticationPrincipal AuthenticatedUser principal) {
        return service.adminRoadmap(principal.userId());
    }

    /** Employee Global Roadmap: only Projects where the Employee is a member. */
    @GetMapping("/api/projects/mine/roadmap")
    public ProjectRoadmapDtos.Response employeeRoadmap(@AuthenticationPrincipal AuthenticatedUser principal) {
        return service.employeeRoadmap(principal.userId());
    }
}
