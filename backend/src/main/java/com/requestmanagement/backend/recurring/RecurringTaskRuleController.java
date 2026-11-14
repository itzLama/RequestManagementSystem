package com.requestmanagement.backend.recurring;

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
@RequestMapping("/api/projects/{projectId}/recurring-tasks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RecurringTaskRuleController {
    private final RecurringTaskRuleService service;

    @GetMapping
    public List<RecurringTaskRuleDtos.Response> list(@PathVariable Long projectId,
                                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.list(projectId, principal.userId());
    }

    @PostMapping
    public ResponseEntity<RecurringTaskRuleDtos.Response> create(@PathVariable Long projectId,
                                                                 @Valid @RequestBody RecurringTaskRuleDtos.Input input,
                                                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, principal.userId(), input));
    }

    @PutMapping("/{ruleId}")
    public RecurringTaskRuleDtos.Response update(@PathVariable Long projectId, @PathVariable Long ruleId,
                                                 @Valid @RequestBody RecurringTaskRuleDtos.Input input,
                                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.update(projectId, ruleId, principal.userId(), input);
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long ruleId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        service.delete(projectId, ruleId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{ruleId}/run")
    public RecurringTaskRuleDtos.RunResult runNow(@PathVariable Long projectId, @PathVariable Long ruleId,
                                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        return service.runNow(projectId, ruleId, principal.userId());
    }
}
