package com.requestmanagement.backend.projectworktype;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/work-types")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ProjectWorkTypeController {
    private final ProjectWorkTypeService service;

    @GetMapping
    public List<ProjectWorkTypeResponse> list(@PathVariable Long projectId) { return service.listAdmin(projectId); }

    @PostMapping
    public ResponseEntity<ProjectWorkTypeResponse> create(@PathVariable Long projectId,
                                                           @Valid @RequestBody ProjectWorkTypeNameRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, input));
    }

    @PutMapping("/{workTypeId}")
    public ProjectWorkTypeResponse rename(@PathVariable Long projectId, @PathVariable Long workTypeId,
                                          @Valid @RequestBody ProjectWorkTypeNameRequest input) {
        return service.rename(projectId, workTypeId, input);
    }

    @DeleteMapping("/{workTypeId}")
    public ResponseEntity<Void> retire(@PathVariable Long projectId, @PathVariable Long workTypeId) {
        service.retire(projectId, workTypeId);
        return ResponseEntity.noContent().build();
    }
}
