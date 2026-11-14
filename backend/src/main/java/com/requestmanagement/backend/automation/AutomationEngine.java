package com.requestmanagement.backend.automation;

import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.projectstatus.ProjectStatusService;
import com.requestmanagement.backend.request.ProjectTaskService;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import com.requestmanagement.backend.usernotification.TaskNotificationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

/**
 * Evaluates the enabled rules of a Project for one committed trigger. Every rule runs in its own
 * new transaction inside {@link AutomationContext} (no further automation, changes attributed to
 * the rule). A failing or invalid rule is recorded and skipped; it never affects the original
 * change or the other rules. Archived Projects never run automation.
 */
@Service
@Slf4j
public class AutomationEngine {
    public static final String APPLIED = "APPLIED";
    public static final String NO_CHANGE = "NO_CHANGE";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED_INVALID_TARGET = "SKIPPED_INVALID_TARGET";

    private final AutomationRuleStore store;
    private final ProjectRepository projectRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ProjectStatusService projectStatusService;
    private final ProjectTaskService projectTaskService;
    private final TaskNotificationPublisher notifications;
    private final TransactionTemplate transaction;

    public AutomationEngine(AutomationRuleStore store, ProjectRepository projectRepository,
                            RequestRepository requestRepository, UserRepository userRepository,
                            ProjectStatusService projectStatusService, ProjectTaskService projectTaskService,
                            TaskNotificationPublisher notifications, PlatformTransactionManager transactionManager) {
        this.store = store;
        this.projectRepository = projectRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.projectStatusService = projectStatusService;
        this.projectTaskService = projectTaskService;
        this.notifications = notifications;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void run(AutomationTriggerEvent event) {
        if (AutomationContext.current().isPresent()) return; // defensive: automation never triggers automation
        boolean projectActive = projectRepository.findById(event.projectId()).map(project -> project.isActive()).orElse(false);
        if (!projectActive) return;
        List<AutomationRuleStore.RuleView> rules = store.listEnabled(event.projectId(), event.trigger());
        for (AutomationRuleStore.RuleView rule : rules) {
            if (rule.trigger() == AutomationTrigger.STATUS_CHANGED && !Objects.equals(rule.triggerStatusId(), event.statusId())) continue;
            runRule(rule, event);
        }
    }

    private void runRule(AutomationRuleStore.RuleView rule, AutomationTriggerEvent event) {
        try {
            if (!rule.warnings().isEmpty()) {
                if (conditionsMatch(rule, event)) record(rule, SKIPPED_INVALID_TARGET, event.taskId());
                return;
            }
            AutomationContext.runAs(rule.context(), () -> transaction.execute(status -> {
                Request task = requestRepository.findByIdAndProject_Id(event.taskId(), event.projectId()).orElse(null);
                if (task == null || !matches(rule, task)) return null;
                String result = apply(rule, task, event);
                store.recordRun(rule.id(), result, task.getId());
                return result;
            }));
        } catch (RuntimeException exception) {
            log.error("Automation rule {} failed on task {}.", rule.id(), event.taskId(), exception);
            record(rule, FAILED, event.taskId());
        }
    }

    private String apply(AutomationRuleStore.RuleView rule, Request task, AutomationTriggerEvent event) {
        // The rule's author is the stored actor reference; the history row is marked with the rule.
        User owner = userRepository.findById(rule.createdBy()).orElseThrow();
        boolean changed = false;
        if (rule.actionStatusId() != null) {
            changed |= projectStatusService.change(task, rule.actionStatusId(), owner, null);
        }
        // The creator chose a different assignee than the one suggested in the form: respect it.
        boolean keepAssignee = event.keepAssignee() && event.trigger() == AutomationTrigger.TASK_CREATED;
        if (rule.actionAssigneeId() != null && !keepAssignee) {
            changed |= projectTaskService.applyAutomationAssignee(task, userRepository.findById(rule.actionAssigneeId()).orElseThrow());
        }
        if (!rule.notifyUserIds().isEmpty()) {
            notifications.automationNotify(task, rule.notifyUserIds());
            changed = true;
        }
        return changed ? APPLIED : NO_CHANGE;
    }

    private boolean conditionsMatch(AutomationRuleStore.RuleView rule, AutomationTriggerEvent event) {
        return transaction.execute(status -> requestRepository.findByIdAndProject_Id(event.taskId(), event.projectId())
                .map(task -> matches(rule, task)).orElse(false));
    }

    private static boolean matches(AutomationRuleStore.RuleView rule, Request task) {
        if (rule.conditionWorkTypeId() != null
                && (task.getWorkType() == null || !rule.conditionWorkTypeId().equals(task.getWorkType().getId()))) return false;
        return rule.conditionPriority() == null || rule.conditionPriority() == task.getPriority();
    }

    private void record(AutomationRuleStore.RuleView rule, String result, Long taskId) {
        try {
            transaction.executeWithoutResult(status -> store.recordRun(rule.id(), result, taskId));
        } catch (RuntimeException exception) {
            log.warn("Unable to record the run of automation rule {}.", rule.id(), exception);
        }
    }
}
