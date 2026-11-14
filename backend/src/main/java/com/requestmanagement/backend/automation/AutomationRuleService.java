package com.requestmanagement.backend.automation;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.projectstatus.ProjectStatusService;
import com.requestmanagement.backend.projectworktype.ProjectWorkTypeService;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Admin-managed, predefined Workflow Automation rules (per Project): one trigger, optional
 * work type / priority conditions, and assign / move status / notify actions. Every target is
 * validated on save; targets that later become invalid are reported as warnings and the rule is
 * skipped at run time.
 */
@Service
@RequiredArgsConstructor
public class AutomationRuleService {
    private final AutomationRuleStore store;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectStatusService projectStatusService;
    private final ProjectWorkTypeService projectWorkTypeService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AutomationRuleDtos.Response> list(Long projectId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        return store.listByProject(projectId).stream().map(AutomationRuleService::response).toList();
    }

    @Transactional(readOnly = true)
    public List<AutomationRuleDtos.Preview> adminPreview(Long projectId, Long adminId) {
        requireActiveAdmin(adminId);
        return preview(requireProject(projectId));
    }

    /** Project members only (same access check as the member board). */
    @Transactional(readOnly = true)
    public List<AutomationRuleDtos.Preview> memberPreview(Long projectId, Long employeeId) {
        return preview(projectAccessService.requireAccessibleProject(projectId, employeeId));
    }

    /**
     * Enabled "task created" rules that would run (no warnings) and assign or move the Task, in the
     * engine's order. Archived Projects never run automation, so they preview nothing.
     */
    private List<AutomationRuleDtos.Preview> preview(Project project) {
        if (!project.isActive()) return List.of();
        return store.listEnabled(project.getId(), AutomationTrigger.TASK_CREATED).stream()
                .filter(rule -> rule.warnings().isEmpty())
                .filter(rule -> rule.actionAssigneeId() != null || rule.actionStatusId() != null)
                .map(rule -> {
                    var status = rule.actionStatusId() == null ? null
                            : projectStatusService.requireActive(project.getId(), rule.actionStatusId());
                    return new AutomationRuleDtos.Preview(rule.id(), rule.name(), rule.nameAr(),
                            rule.conditionWorkTypeId(), rule.conditionPriority(),
                            rule.actionAssigneeId(), rule.actionAssigneeName(),
                            status == null ? null : status.getId(), status == null ? null : status.getName(),
                            status == null || status.getSemanticKey() == null ? null : status.getSemanticKey().name());
                }).toList();
    }

    @Transactional
    public AutomationRuleDtos.Response create(Long projectId, Long adminId, AutomationRuleDtos.Input input) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireMutableProject(projectId);
        Long id = store.insert(projectId, admin.getId(), values(project, input));
        return response(store.find(projectId, id).orElseThrow());
    }

    @Transactional
    public AutomationRuleDtos.Response update(Long projectId, Long ruleId, Long adminId, AutomationRuleDtos.Input input) {
        requireActiveAdmin(adminId);
        Project project = requireMutableProject(projectId);
        requireRule(projectId, ruleId);
        store.update(ruleId, values(project, input));
        return response(store.find(projectId, ruleId).orElseThrow());
    }

    @Transactional
    public void delete(Long projectId, Long ruleId, Long adminId) {
        requireActiveAdmin(adminId);
        requireMutableProject(projectId);
        requireRule(projectId, ruleId);
        store.delete(ruleId);
    }

    private AutomationRuleStore.Values values(Project project, AutomationRuleDtos.Input input) {
        Long projectId = project.getId();
        String name = required(input.name(), "Rule name is required.");
        String nameAr = required(input.nameAr(), "Arabic rule name is required.");
        Long triggerStatusId = null;
        if (input.trigger() == AutomationTrigger.STATUS_CHANGED) {
            if (input.triggerStatusId() == null) throw badRequest("Choose the status that triggers the rule.");
            triggerStatusId = activeStatus(projectId, input.triggerStatusId());
        }
        if (input.conditionWorkTypeId() != null) projectWorkTypeService.requireActiveForProject(projectId, input.conditionWorkTypeId());
        Long actionStatusId = input.actionStatusId() == null ? null : activeStatus(projectId, input.actionStatusId());
        if (input.actionAssigneeId() != null) projectAccessService.requireEligibleAssignee(project, input.actionAssigneeId());
        List<Long> recipients = new ArrayList<>(new LinkedHashSet<>(input.notifyUserIds() == null ? List.of() : input.notifyUserIds()));
        for (Long recipient : recipients) {
            try {
                projectAccessService.requireEligibleAssignee(project, recipient);
            } catch (ResponseStatusException exception) {
                throw badRequest("Members to notify must be active Project members.");
            }
        }
        if (actionStatusId == null && input.actionAssigneeId() == null && recipients.isEmpty()) {
            throw badRequest("Choose at least one action.");
        }
        return new AutomationRuleStore.Values(name, nameAr, input.trigger(), triggerStatusId, input.conditionWorkTypeId(),
                input.conditionPriority(), input.actionAssigneeId(), actionStatusId, recipients,
                input.active() == null || input.active());
    }

    private Long activeStatus(Long projectId, Long statusId) {
        try {
            return projectStatusService.requireActive(projectId, statusId).getId();
        } catch (ResponseStatusException exception) {
            throw badRequest("Choose an active status of this Project.");
        }
    }

    private static AutomationRuleDtos.Response response(AutomationRuleStore.RuleView rule) {
        return new AutomationRuleDtos.Response(rule.id(), rule.name(), rule.nameAr(), rule.trigger(), rule.triggerStatusId(),
                rule.conditionWorkTypeId(), rule.conditionPriority(), rule.actionAssigneeId(), rule.actionAssigneeName(),
                rule.actionStatusId(), rule.notifyUserIds(), rule.active(), rule.lastRunAt(), rule.lastRunResult(),
                rule.lastRunRequestId(), rule.warnings(), rule.createdAt(), rule.updatedAt());
    }

    private AutomationRuleStore.RuleView requireRule(Long projectId, Long ruleId) {
        return store.find(projectId, ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation rule not found."));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private Project requireMutableProject(Long projectId) {
        Project project = requireProject(projectId);
        projectAccessService.requireActive(project);
        return project;
    }

    private User requireActiveAdmin(Long adminId) {
        return userRepository.findById(adminId).filter(User::isActive).filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required."));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
        return value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
