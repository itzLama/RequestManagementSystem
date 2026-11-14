package com.requestmanagement.backend.projectinsights;

import com.requestmanagement.backend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProjectDashboardController {
    private final ProjectDashboardService service;

    /** URL rules already restrict /api/projects/** to Administrators. */
    @GetMapping("/api/projects/{projectId}/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectDashboardDtos.Response adminDashboard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable Long projectId) {
        return service.adminDashboard(projectId, principal.userId());
    }

    /** Project members only (URL rules restrict /api/projects/mine/** to Employees). */
    @GetMapping("/api/projects/mine/{projectId}/dashboard")
    public ProjectDashboardDtos.Response memberDashboard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable Long projectId) {
        return service.memberDashboard(projectId, principal.userId());
    }
}
