package com.requestmanagement.backend.tasktemplate;

import com.requestmanagement.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/task-templates")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ProjectTaskTemplateController {
    private final ProjectTaskTemplateService service;

    @GetMapping
    public List<ProjectTaskTemplateDtos.Response> list(@PathVariable Long projectId,
                                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.listAdmin(projectId, principal.userId());
    }

    @PostMapping
    public ResponseEntity<ProjectTaskTemplateDtos.Response> create(
            @PathVariable Long projectId, @Valid @RequestBody ProjectTaskTemplateDtos.Input input,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, principal.userId(), input));
    }

    @PutMapping("/{templateId}")
    public ProjectTaskTemplateDtos.Response update(
            @PathVariable Long projectId, @PathVariable Long templateId,
            @Valid @RequestBody ProjectTaskTemplateDtos.Input input,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.update(projectId, templateId, principal.userId(), input);
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long templateId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        service.delete(projectId, templateId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
