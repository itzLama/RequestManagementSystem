package com.requestmanagement.backend.project;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ProjectMembershipController {
    private final ProjectMembershipService membershipService;

    @GetMapping
    public List<ProjectMemberResponse> list(@PathVariable Long projectId) {
        return membershipService.list(projectId);
    }

    @PostMapping
    public ResponseEntity<ProjectMemberResponse> add(@PathVariable Long projectId,
                                                      @Valid @RequestBody AddProjectMemberRequest input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(membershipService.add(projectId, input));
    }

    @DeleteMapping("/{employeeId}")
    public ResponseEntity<Void> remove(@PathVariable Long projectId, @PathVariable Long employeeId) {
        membershipService.remove(projectId, employeeId);
        return ResponseEntity.noContent().build();
    }
}
