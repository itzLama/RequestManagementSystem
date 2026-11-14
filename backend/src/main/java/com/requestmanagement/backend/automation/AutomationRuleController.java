package com.requestmanagement.backend.automation;

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
@RequestMapping("/api/projects/{projectId}/automation-rules")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AutomationRuleController {
    private final AutomationRuleService service;

    @GetMapping
    public List<AutomationRuleDtos.Response> list(@PathVariable Long projectId,
                                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.list(projectId, principal.userId());
    }

    @GetMapping("/preview")
    public List<AutomationRuleDtos.Preview> preview(@PathVariable Long projectId,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.adminPreview(projectId, principal.userId());
    }

    @PostMapping
    public ResponseEntity<AutomationRuleDtos.Response> create(@PathVariable Long projectId,
                                                              @Valid @RequestBody AutomationRuleDtos.Input input,
                                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, principal.userId(), input));
    }

    @PutMapping("/{ruleId}")
    public AutomationRuleDtos.Response update(@PathVariable Long projectId, @PathVariable Long ruleId,
                                              @Valid @RequestBody AutomationRuleDtos.Input input,
                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.update(projectId, ruleId, principal.userId(), input);
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long ruleId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        service.delete(projectId, ruleId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
