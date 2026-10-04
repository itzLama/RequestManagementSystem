package com.requestmanagement.backend.project;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping
    public List<ProjectResponse> list() { return projectService.list(); }

    @GetMapping("/{projectId}")
    public ProjectResponse details(@PathVariable Long projectId) { return projectService.details(projectId); }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(input));
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(@PathVariable Long projectId, @Valid @RequestBody UpdateProjectRequest input) {
        return projectService.update(projectId, input);
    }

    @PatchMapping("/{projectId}/archive")
    public ProjectResponse archive(@PathVariable Long projectId) { return projectService.archive(projectId); }
}
