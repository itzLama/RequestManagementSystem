package com.requestmanagement.backend.tasktemplate;

import com.requestmanagement.backend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only template access for Project members, used to pre-fill the Create Task dialog. */
@RestController
@RequestMapping("/api/projects/mine/{projectId}/task-templates")
@RequiredArgsConstructor
public class EmployeeProjectTaskTemplateController {
    private final ProjectTaskTemplateService service;

    @GetMapping
    public List<ProjectTaskTemplateDtos.Response> list(@PathVariable Long projectId,
                                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.listEmployee(projectId, principal.userId());
    }
}
