package com.requestmanagement.backend.automation;

import com.requestmanagement.backend.request.RequestPriority;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC access to Workflow Automation rules and their notification recipients. */
@Repository
class AutomationRuleStore {
    record RuleView(
            Long id, Long projectId, String name, String nameAr, AutomationTrigger trigger, Long triggerStatusId,
            Long conditionWorkTypeId, RequestPriority conditionPriority,
            Long actionAssigneeId, String actionAssigneeName, Long actionStatusId, List<Long> notifyUserIds,
            boolean active, Long createdBy, LocalDateTime lastRunAt, String lastRunResult, Long lastRunRequestId,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            boolean projectActive, boolean triggerStatusActive, boolean actionStatusActive,
            boolean assigneeEligible, boolean recipientsEligible
    ) {
        boolean hasAction() {
            return actionAssigneeId != null || actionStatusId != null || !notifyUserIds.isEmpty();
        }

        /** Reasons the rule is skipped right now (empty = it can run). */
        List<String> warnings() {
            List<String> warnings = new ArrayList<>();
            if (!projectActive) warnings.add("ARCHIVED_PROJECT");
            if (!triggerStatusActive) warnings.add("TRIGGER_STATUS_RETIRED");
            if (!actionStatusActive) warnings.add("ACTION_STATUS_RETIRED");
            if (!assigneeEligible) warnings.add("ASSIGNEE_NOT_MEMBER");
            if (!recipientsEligible) warnings.add("RECIPIENT_NOT_MEMBER");
            if (!hasAction()) warnings.add("NO_ACTION");
            return warnings;
        }

        AutomationContext.Rule context() {
            return new AutomationContext.Rule(id, name, nameAr);
        }
    }

    record Values(String name, String nameAr, AutomationTrigger trigger, Long triggerStatusId,
                  Long conditionWorkTypeId, RequestPriority conditionPriority,
                  Long actionAssigneeId, Long actionStatusId, List<Long> notifyUserIds, boolean active) { }

    private static final String ELIGIBLE_MEMBER = """
            (%1$s.is_active AND %1$s.role = 'EMPLOYEE' AND EXISTS (
                SELECT 1 FROM project_memberships m WHERE m.project_id = r.project_id AND m.employee_id = %1$s.user_id))
            """;

    private static final String SELECT_VIEW = """
            SELECT r.*, aa.full_name AS assignee_name,
                   (p.status = 'ACTIVE') AS project_active,
                   (r.trigger_status_id IS NULL OR ts.is_active) AS trigger_status_active,
                   (r.action_status_id IS NULL OR acs.is_active) AS action_status_active,
                   (r.action_assignee_id IS NULL OR %s) AS assignee_eligible,
                   NOT EXISTS (SELECT 1 FROM project_automation_rule_recipients rr
                                 JOIN users ru ON ru.user_id = rr.user_id
                                WHERE rr.rule_id = r.rule_id AND NOT %s) AS recipients_eligible
              FROM project_automation_rules r
              JOIN projects p ON p.project_id = r.project_id
              LEFT JOIN project_status_columns ts ON ts.status_id = r.trigger_status_id
              LEFT JOIN project_status_columns acs ON acs.status_id = r.action_status_id
              LEFT JOIN users aa ON aa.user_id = r.action_assignee_id
            """.formatted(ELIGIBLE_MEMBER.formatted("aa"), ELIGIBLE_MEMBER.formatted("ru"));

    private final NamedParameterJdbcTemplate jdbc;

    AutomationRuleStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<RuleView> listByProject(Long projectId) {
        return query(" WHERE r.project_id = :project ORDER BY r.created_at, r.rule_id",
                new MapSqlParameterSource("project", projectId));
    }

    /** Enabled rules for one trigger, in a stable order (oldest first). */
    List<RuleView> listEnabled(Long projectId, AutomationTrigger trigger) {
        return query(" WHERE r.project_id = :project AND r.trigger_type = :trigger AND r.is_active ORDER BY r.rule_id",
                new MapSqlParameterSource("project", projectId).addValue("trigger", trigger.name()));
    }

    Optional<RuleView> find(Long projectId, Long ruleId) {
        return query(" WHERE r.rule_id = :id AND r.project_id = :project",
                new MapSqlParameterSource("id", ruleId).addValue("project", projectId)).stream().findFirst();
    }

    Long insert(Long projectId, Long adminId, Values values) {
        Long id = jdbc.queryForObject("""
                INSERT INTO project_automation_rules(project_id, rule_name, rule_name_ar, trigger_type, trigger_status_id,
                        condition_work_type_id, condition_priority, action_assignee_id, action_status_id, is_active,
                        created_by, created_at, updated_at)
                VALUES (:project, :name, :nameAr, :trigger, :triggerStatus, :workType, :priority, :assignee,
                        :actionStatus, :active, :admin, :now, :now)
                RETURNING rule_id
                """, params(values).addValue("project", projectId).addValue("admin", adminId), Long.class);
        replaceRecipients(id, values.notifyUserIds());
        return id;
    }

    void update(Long ruleId, Values values) {
        jdbc.update("""
                UPDATE project_automation_rules
                   SET rule_name = :name, rule_name_ar = :nameAr, trigger_type = :trigger,
                       trigger_status_id = :triggerStatus, condition_work_type_id = :workType,
                       condition_priority = :priority, action_assignee_id = :assignee,
                       action_status_id = :actionStatus, is_active = :active, updated_at = :now
                 WHERE rule_id = :id
                """, params(values).addValue("id", ruleId));
        replaceRecipients(ruleId, values.notifyUserIds());
    }

    void delete(Long ruleId) {
        jdbc.update("DELETE FROM project_automation_rules WHERE rule_id = :id", new MapSqlParameterSource("id", ruleId));
    }

    void recordRun(Long ruleId, String result, Long requestId) {
        jdbc.update("""
                UPDATE project_automation_rules
                   SET last_run_at = :now, last_run_result = :result, last_run_request_id = :request
                 WHERE rule_id = :id
                """, new MapSqlParameterSource("id", ruleId).addValue("result", result).addValue("request", requestId)
                .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
    }

    private void replaceRecipients(Long ruleId, List<Long> userIds) {
        jdbc.update("DELETE FROM project_automation_rule_recipients WHERE rule_id = :id", new MapSqlParameterSource("id", ruleId));
        for (Long userId : userIds) {
            jdbc.update("INSERT INTO project_automation_rule_recipients(rule_id, user_id) VALUES (:id, :user)",
                    new MapSqlParameterSource("id", ruleId).addValue("user", userId));
        }
    }

    private List<RuleView> query(String where, MapSqlParameterSource params) {
        List<RuleView> partial = jdbc.query(SELECT_VIEW + where, params, (rs, row) -> view(rs, List.of()));
        if (partial.isEmpty()) return partial;
        Map<Long, List<Long>> recipients = new HashMap<>();
        jdbc.query("""
                SELECT rule_id, user_id FROM project_automation_rule_recipients
                 WHERE rule_id IN (:ids) ORDER BY rule_id, user_id
                """, new MapSqlParameterSource("ids", partial.stream().map(RuleView::id).toList()), rs -> {
            recipients.computeIfAbsent(rs.getLong("rule_id"), key -> new ArrayList<>()).add(rs.getLong("user_id"));
        });
        return partial.stream().map(rule -> withRecipients(rule, recipients.getOrDefault(rule.id(), List.of()))).toList();
    }

    private static RuleView withRecipients(RuleView rule, List<Long> recipients) {
        return new RuleView(rule.id(), rule.projectId(), rule.name(), rule.nameAr(), rule.trigger(), rule.triggerStatusId(),
                rule.conditionWorkTypeId(), rule.conditionPriority(), rule.actionAssigneeId(), rule.actionAssigneeName(),
                rule.actionStatusId(), List.copyOf(recipients), rule.active(), rule.createdBy(), rule.lastRunAt(),
                rule.lastRunResult(), rule.lastRunRequestId(), rule.createdAt(), rule.updatedAt(), rule.projectActive(),
                rule.triggerStatusActive(), rule.actionStatusActive(), rule.assigneeEligible(), rule.recipientsEligible());
    }

    private static MapSqlParameterSource params(Values values) {
        return new MapSqlParameterSource()
                .addValue("name", values.name()).addValue("nameAr", values.nameAr())
                .addValue("trigger", values.trigger().name()).addValue("triggerStatus", values.triggerStatusId())
                .addValue("workType", values.conditionWorkTypeId())
                .addValue("priority", values.conditionPriority() == null ? null : values.conditionPriority().name())
                .addValue("assignee", values.actionAssigneeId()).addValue("actionStatus", values.actionStatusId())
                .addValue("active", values.active()).addValue("now", Timestamp.valueOf(LocalDateTime.now()));
    }

    private static RuleView view(ResultSet rs, List<Long> recipients) throws SQLException {
        String priority = rs.getString("condition_priority");
        return new RuleView(rs.getLong("rule_id"), rs.getLong("project_id"), rs.getString("rule_name"),
                rs.getString("rule_name_ar"), AutomationTrigger.valueOf(rs.getString("trigger_type")),
                rs.getObject("trigger_status_id", Long.class), rs.getObject("condition_work_type_id", Long.class),
                priority == null ? null : RequestPriority.valueOf(priority),
                rs.getObject("action_assignee_id", Long.class), rs.getString("assignee_name"),
                rs.getObject("action_status_id", Long.class), recipients, rs.getBoolean("is_active"),
                rs.getLong("created_by"), time(rs, "last_run_at"), rs.getString("last_run_result"),
                rs.getObject("last_run_request_id", Long.class), time(rs, "created_at"), time(rs, "updated_at"),
                rs.getBoolean("project_active"), rs.getBoolean("trigger_status_active"),
                rs.getBoolean("action_status_active"), rs.getBoolean("assignee_eligible"),
                rs.getBoolean("recipients_eligible"));
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
