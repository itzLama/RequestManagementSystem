package com.requestmanagement.backend.recurring;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.tasktemplate.ProjectTaskTemplate;
import com.requestmanagement.backend.tasktemplate.ProjectTaskTemplateRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin-managed Recurring Task rules (per Project). Rules are configuration only; occurrences are
 * created by {@link RecurringTaskRunner} through the normal Task creation path.
 */
@Service
@RequiredArgsConstructor
public class RecurringTaskRuleService {
    private final RecurringTaskRuleStore store;
    private final RecurringTaskRunner runner;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectTaskTemplateRepository templateRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RecurringTaskRuleDtos.Response> list(Long projectId, Long adminId) {
        requireActiveAdmin(adminId);
        requireProject(projectId);
        LocalDate today = runner.today();
        return store.listByProject(projectId).stream().map(rule -> response(rule, today)).toList();
    }

    @Transactional
    public RecurringTaskRuleDtos.Response create(Long projectId, Long adminId, RecurringTaskRuleDtos.Input input) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireMutableProject(projectId);
        RecurringTaskRuleStore.Values values = values(project, input);
        Long id = store.insert(projectId, admin.getId(), values, runner.today());
        return response(store.find(projectId, id).orElseThrow(), runner.today());
    }

    @Transactional
    public RecurringTaskRuleDtos.Response update(Long projectId, Long ruleId, Long adminId, RecurringTaskRuleDtos.Input input) {
        User admin = requireActiveAdmin(adminId);
        Project project = requireMutableProject(projectId);
        RecurringTaskRuleStore.RuleView existing = requireRule(projectId, ruleId);
        RecurringTaskRuleStore.Values values = values(project, input);
        // Re-activating a paused rule starts from today: paused dates are never backfilled.
        LocalDate effectiveFrom = !existing.active() && values.active() ? runner.today() : existing.effectiveFrom();
        // The Administrator who last saved the rule owns it (and becomes the creator of new Tasks).
        store.update(ruleId, admin.getId(), values, effectiveFrom);
        return response(store.find(projectId, ruleId).orElseThrow(), runner.today());
    }

    @Transactional
    public void delete(Long projectId, Long ruleId, Long adminId) {
        requireActiveAdmin(adminId);
        requireMutableProject(projectId);
        requireRule(projectId, ruleId);
        store.delete(ruleId);
    }

    /** Not transactional: the runner commits the occurrence in its own transaction first. */
    public RecurringTaskRuleDtos.RunResult runNow(Long projectId, Long ruleId, Long adminId) {
        requireActiveAdmin(adminId);
        requireMutableProject(projectId);
        requireRule(projectId, ruleId);
        RecurringTaskRunner.Outcome outcome = runner.runNow(projectId, ruleId);
        LocalDate today = runner.today();
        return new RecurringTaskRuleDtos.RunResult(outcome.result(), outcome.occurrenceDate(), outcome.taskId(),
                response(store.find(projectId, ruleId).orElseThrow(), today));
    }

    private RecurringTaskRuleStore.Values values(Project project, RecurringTaskRuleDtos.Input input) {
        ProjectTaskTemplate template = templateRepository.findByIdAndProject_Id(input.templateId(), project.getId())
                .orElseThrow(() -> badRequest("Task Template not found in this Project."));
        if (!template.getWorkType().isActive()) {
            throw badRequest("The Task Template's Work Type is retired. Update the template first.");
        }
        Integer weekday = input.frequency() == RecurrenceSchedule.Frequency.WEEKLY ? input.weekday() : null;
        Integer monthDay = input.frequency() == RecurrenceSchedule.Frequency.MONTHLY ? input.monthDay() : null;
        if (input.frequency() == RecurrenceSchedule.Frequency.WEEKLY && weekday == null) throw badRequest("Choose a weekday.");
        if (input.frequency() == RecurrenceSchedule.Frequency.MONTHLY && monthDay == null) throw badRequest("Choose a day of the month.");
        if (input.endDate() != null && input.endDate().isBefore(input.startDate())) {
            throw badRequest("End date must be on or after the start date.");
        }
        if (input.assigneeId() != null) projectAccessService.requireEligibleAssignee(project, input.assigneeId());
        return new RecurringTaskRuleStore.Values(template.getId(), input.frequency(), weekday, monthDay,
                input.startDate(), input.endDate(), input.assigneeId(), input.active() == null || input.active());
    }

    private RecurringTaskRuleDtos.Response response(RecurringTaskRuleStore.RuleView rule, LocalDate today) {
        return new RecurringTaskRuleDtos.Response(rule.id(), rule.templateId(), rule.templateName(), rule.templateNameAr(),
                rule.frequency(), rule.weekday(), rule.monthDay(), rule.startDate(), rule.endDate(),
                rule.assigneeId(), rule.assigneeName(), rule.active(), rule.ownerId(), rule.ownerName(),
                nextRunDate(rule, today), rule.lastRunAt(), rule.lastRunResult(), rule.lastRunOccurrenceDate(),
                rule.lastRunRequestId(), rule.warnings(), rule.createdAt(), rule.updatedAt());
    }

    /** The next date the scheduler will create (today if today's occurrence is still due). */
    private LocalDate nextRunDate(RecurringTaskRuleStore.RuleView rule, LocalDate today) {
        if (!rule.active() || !rule.projectActive()) return null;
        LocalDate from = today.isBefore(rule.effectiveFrom()) ? rule.effectiveFrom() : today;
        LocalDate next = rule.schedule().nextOnOrAfter(from).orElse(null);
        if (next != null && store.occurrenceExists(rule.id(), next)) next = rule.schedule().nextOnOrAfter(next.plusDays(1)).orElse(null);
        return next;
    }

    private RecurringTaskRuleStore.RuleView requireRule(Long projectId, Long ruleId) {
        return store.find(projectId, ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring Task rule not found."));
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

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
