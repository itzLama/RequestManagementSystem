package com.requestmanagement.backend.project;

import com.requestmanagement.backend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/mine")
@RequiredArgsConstructor
public class EmployeeProjectController {
    private final EmployeeProjectService employeeProjectService;

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return employeeProjectService.listMine(principal.userId());
    }

    @GetMapping("/{projectId}")
    public ProjectResponse details(@PathVariable Long projectId,
                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        return employeeProjectService.details(projectId, principal.userId());
    }

    @GetMapping("/{projectId}/members")
    public List<ProjectTaskMemberResponse> members(@PathVariable Long projectId,
                                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        return employeeProjectService.members(projectId, principal.userId());
    }
}
