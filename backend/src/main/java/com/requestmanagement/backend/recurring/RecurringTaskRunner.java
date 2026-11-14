package com.requestmanagement.backend.recurring;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.request.ProjectTaskService;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.tasktemplate.ProjectTaskTemplate;
import com.requestmanagement.backend.tasktemplate.ProjectTaskTemplateRepository;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Creates Recurring Task occurrences. Each occurrence runs in its own transaction and claims the
 * unique (rule, date) key before creating the Task, so running twice, overlapping with "Run now",
 * or several instances running the job never produce a duplicate. One failing rule never stops
 * the others.
 */
@Service
@Slf4j
public class RecurringTaskRunner {
    public static final String CREATED = "CREATED";
    public static final String ALREADY_EXISTS = "ALREADY_EXISTS";
    public static final String FAILED = "FAILED";

    private final RecurringTaskRuleStore store;
    private final ProjectRepository projectRepository;
    private final ProjectTaskTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final ProjectTaskService projectTaskService;
    private final TransactionTemplate transaction;
    private final ZoneId planningZone;

    public RecurringTaskRunner(RecurringTaskRuleStore store, ProjectRepository projectRepository,
                               ProjectTaskTemplateRepository templateRepository, UserRepository userRepository,
                               ProjectTaskService projectTaskService, PlatformTransactionManager transactionManager,
                               @Value("${masar.planning.time-zone:Asia/Riyadh}") String planningZone) {
        this.store = store;
        this.projectRepository = projectRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.projectTaskService = projectTaskService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.planningZone = ZoneId.of(planningZone);
    }

    public record Outcome(String result, LocalDate occurrenceDate, Long taskId) { }

    public LocalDate today() {
        return LocalDate.now(planningZone);
    }

    /**
     * Scheduled run: for every active rule of an active Project, creates the latest due occurrence
     * on or before today if it does not exist yet. A server that was off creates at most that one
     * latest missed occurrence (no backfill flood), never one before the rule became effective.
     */
    public int runDue() {
        LocalDate today = today();
        int created = 0;
        for (RecurringTaskRuleStore.RuleView rule : store.listRunnable()) {
            Optional<LocalDate> due = rule.schedule().latestOnOrBefore(today)
                    .filter(date -> !date.isBefore(rule.effectiveFrom()));
            if (due.isEmpty() || store.occurrenceExists(rule.id(), due.get())) continue;
            if (CREATED.equals(run(rule.projectId(), rule.id(), due.get(), false).result())) created++;
        }
        return created;
    }

    /** "Run now": creates today's occurrence immediately (the unique key still applies). */
    public Outcome runNow(Long projectId, Long ruleId) {
        return run(projectId, ruleId, today(), true);
    }

    private Outcome run(Long projectId, Long ruleId, LocalDate date, boolean manual) {
        try {
            Outcome outcome = transaction.execute(status -> createOccurrence(projectId, ruleId, date, manual));
            return outcome == null ? new Outcome(FAILED, date, null) : outcome;
        } catch (RuntimeException exception) {
            log.error("Recurring task rule {} failed for {}.", ruleId, date, exception);
            try {
                transaction.executeWithoutResult(status -> store.recordRun(ruleId, FAILED, date, null));
            } catch (RuntimeException recordFailure) {
                log.warn("Unable to record the failed run of recurring task rule {}.", ruleId, recordFailure);
            }
            return new Outcome(FAILED, date, null);
        }
    }

    private Outcome createOccurrence(Long projectId, Long ruleId, LocalDate date, boolean manual) {
        if (!store.lock(ruleId)) return new Outcome(FAILED, date, null);
        RecurringTaskRuleStore.RuleView rule = store.find(projectId, ruleId).orElse(null);
        if (rule == null) return new Outcome(FAILED, date, null);
        List<String> warnings = rule.warnings();
        if (!warnings.isEmpty()) {
            String result = "SKIPPED_" + warnings.get(0);
            store.recordRun(ruleId, result, date, null);
            return new Outcome(result, date, null);
        }
        Optional<Long> occurrence = store.claimOccurrence(ruleId, date, manual);
        if (occurrence.isEmpty()) {
            if (manual) store.recordRun(ruleId, ALREADY_EXISTS, date, null);
            return new Outcome(ALREADY_EXISTS, date, null);
        }
        Project project = projectRepository.findById(projectId).orElseThrow();
        ProjectTaskTemplate template = templateRepository.findByIdAndProject_Id(rule.templateId(), projectId).orElseThrow();
        User owner = userRepository.findById(rule.ownerId()).orElseThrow();
        User assignee = rule.assigneeId() == null ? null : userRepository.findById(rule.assigneeId()).orElseThrow();
        LocalDate due = template.getDueOffsetDays() == null ? null : date.plusDays(template.getDueOffsetDays());
        Request task = projectTaskService.createRecurringOccurrence(project, owner, template, assignee, date, due);
        store.attachRequest(occurrence.get(), task.getId());
        store.recordRun(ruleId, CREATED, date, task.getId());
        return new Outcome(CREATED, date, task.getId());
    }
}
