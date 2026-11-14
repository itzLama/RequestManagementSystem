package com.requestmanagement.backend.automation;

import com.requestmanagement.backend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only automation preview for Project members' Create Task form. No rule management here. */
@RestController
@RequestMapping("/api/projects/mine/{projectId}/automation-rules")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class EmployeeAutomationPreviewController {
    private final AutomationRuleService service;

    @GetMapping("/preview")
    public List<AutomationRuleDtos.Preview> preview(@PathVariable Long projectId,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.memberPreview(projectId, principal.userId());
    }
}
