package com.requestmanagement.backend.recurring;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access to recurring task rules and their occurrences (one row per rule and date). */
@Repository
class RecurringTaskRuleStore {
    /** A rule plus everything needed to explain whether it can run right now. */
    record RuleView(
            Long id, Long projectId, Long templateId, String templateName, String templateNameAr,
            RecurrenceSchedule.Frequency frequency, Integer weekday, Integer monthDay,
            LocalDate startDate, LocalDate endDate, Long assigneeId, String assigneeName, boolean active,
            LocalDate effectiveFrom, Long ownerId, String ownerName,
            LocalDateTime lastRunAt, String lastRunResult, LocalDate lastRunOccurrenceDate, Long lastRunRequestId,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            boolean projectActive, boolean workTypeActive, boolean assigneeEligible, boolean ownerEligible
    ) {
        RecurrenceSchedule schedule() {
            return new RecurrenceSchedule(frequency, weekday, monthDay, startDate, endDate);
        }

        /** Reasons the rule is skipped right now (empty = it can run). */
        List<String> warnings() {
            List<String> warnings = new ArrayList<>();
            if (!projectActive) warnings.add("ARCHIVED_PROJECT");
            if (!workTypeActive) warnings.add("RETIRED_WORK_TYPE");
            if (!assigneeEligible) warnings.add("ASSIGNEE_NOT_MEMBER");
            if (!ownerEligible) warnings.add("OWNER_INACTIVE");
            return warnings;
        }
    }

    record Values(Long templateId, RecurrenceSchedule.Frequency frequency, Integer weekday, Integer monthDay,
                  LocalDate startDate, LocalDate endDate, Long assigneeId, boolean active) { }

    private static final String SELECT_VIEW = """
            SELECT r.*, t.template_name, t.template_name_ar,
                   a.full_name AS assignee_name, o.full_name AS owner_name,
                   (p.status = 'ACTIVE') AS project_active,
                   wt.is_active AS work_type_active,
                   (r.assignee_id IS NULL OR (a.is_active AND a.role = 'EMPLOYEE' AND EXISTS (
                        SELECT 1 FROM project_memberships m
                         WHERE m.project_id = r.project_id AND m.employee_id = r.assignee_id))) AS assignee_eligible,
                   (o.is_active AND o.role = 'ADMIN') AS owner_eligible
              FROM project_recurring_task_rules r
              JOIN projects p ON p.project_id = r.project_id
              JOIN project_task_templates t ON t.template_id = r.template_id
              JOIN project_work_types wt ON wt.work_type_id = t.work_type_id
              JOIN users o ON o.user_id = r.owner_id
              LEFT JOIN users a ON a.user_id = r.assignee_id
            """;

    private static final RowMapper<RuleView> VIEW = RecurringTaskRuleStore::view;

    private final NamedParameterJdbcTemplate jdbc;

    RecurringTaskRuleStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<RuleView> listByProject(Long projectId) {
        return jdbc.query(SELECT_VIEW + " WHERE r.project_id = :project ORDER BY r.created_at, r.rule_id",
                new MapSqlParameterSource("project", projectId), VIEW);
    }

    /** Active rules of active Projects: the candidates of a scheduled run. */
    List<RuleView> listRunnable() {
        return jdbc.query(SELECT_VIEW + " WHERE r.is_active AND p.status = 'ACTIVE' ORDER BY r.rule_id",
                new MapSqlParameterSource(), VIEW);
    }

    Optional<RuleView> find(Long projectId, Long ruleId) {
        return jdbc.query(SELECT_VIEW + " WHERE r.rule_id = :id AND r.project_id = :project",
                new MapSqlParameterSource("id", ruleId).addValue("project", projectId), VIEW).stream().findFirst();
    }

    /** Locks the rule row for the rest of the transaction, serializing concurrent runs of one rule. */
    boolean lock(Long ruleId) {
        return !jdbc.queryForList("SELECT rule_id FROM project_recurring_task_rules WHERE rule_id = :id FOR UPDATE",
                new MapSqlParameterSource("id", ruleId), Long.class).isEmpty();
    }

    Long insert(Long projectId, Long ownerId, Values values, LocalDate effectiveFrom) {
        return jdbc.queryForObject("""
                INSERT INTO project_recurring_task_rules(project_id, template_id, frequency, weekday, month_day,
                        start_date, end_date, assignee_id, is_active, effective_from, owner_id, created_at, updated_at)
                VALUES (:project, :template, :frequency, :weekday, :monthDay, :start, :end, :assignee, :active,
                        :effectiveFrom, :owner, :now, :now)
                RETURNING rule_id
                """, params(values).addValue("project", projectId).addValue("owner", ownerId)
                .addValue("effectiveFrom", Date.valueOf(effectiveFrom)), Long.class);
    }

    void update(Long ruleId, Long ownerId, Values values, LocalDate effectiveFrom) {
        jdbc.update("""
                UPDATE project_recurring_task_rules
                   SET template_id = :template, frequency = :frequency, weekday = :weekday, month_day = :monthDay,
                       start_date = :start, end_date = :end, assignee_id = :assignee, is_active = :active,
                       effective_from = :effectiveFrom, owner_id = :owner, updated_at = :now
                 WHERE rule_id = :id
                """, params(values).addValue("id", ruleId).addValue("owner", ownerId)
                .addValue("effectiveFrom", Date.valueOf(effectiveFrom)));
    }

    void delete(Long ruleId) {
        jdbc.update("DELETE FROM project_recurring_task_rules WHERE rule_id = :id", new MapSqlParameterSource("id", ruleId));
    }

    void recordRun(Long ruleId, String result, LocalDate occurrenceDate, Long requestId) {
        jdbc.update("""
                UPDATE project_recurring_task_rules
                   SET last_run_at = :now, last_run_result = :result, last_run_occurrence_date = :date,
                       last_run_request_id = :request
                 WHERE rule_id = :id
                """, new MapSqlParameterSource("id", ruleId).addValue("result", result)
                .addValue("date", occurrenceDate == null ? null : Date.valueOf(occurrenceDate))
                .addValue("request", requestId).addValue("now", Timestamp.valueOf(LocalDateTime.now())));
    }

    boolean occurrenceExists(Long ruleId, LocalDate date) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_recurring_task_occurrences WHERE rule_id = :id AND occurrence_date = :date
                """, new MapSqlParameterSource("id", ruleId).addValue("date", Date.valueOf(date)), Integer.class);
        return count != null && count > 0;
    }

    /**
     * Claims (rule, date). Returns empty when it already exists: the unique key makes a second or
     * concurrent run a no-op (a concurrent insert waits for the first transaction to finish).
     */
    Optional<Long> claimOccurrence(Long ruleId, LocalDate date, boolean manual) {
        return jdbc.queryForList("""
                INSERT INTO project_recurring_task_occurrences(rule_id, occurrence_date, manual, created_at)
                VALUES (:id, :date, :manual, :now)
                ON CONFLICT (rule_id, occurrence_date) DO NOTHING
                RETURNING occurrence_id
                """, new MapSqlParameterSource("id", ruleId).addValue("date", Date.valueOf(date))
                .addValue("manual", manual).addValue("now", Timestamp.valueOf(LocalDateTime.now())), Long.class)
                .stream().findFirst();
    }

    void attachRequest(Long occurrenceId, Long requestId) {
        jdbc.update("UPDATE project_recurring_task_occurrences SET request_id = :request WHERE occurrence_id = :id",
                new MapSqlParameterSource("id", occurrenceId).addValue("request", requestId));
    }

    private static MapSqlParameterSource params(Values values) {
        return new MapSqlParameterSource()
                .addValue("template", values.templateId()).addValue("frequency", values.frequency().name())
                .addValue("weekday", values.weekday()).addValue("monthDay", values.monthDay())
                .addValue("start", Date.valueOf(values.startDate()))
                .addValue("end", values.endDate() == null ? null : Date.valueOf(values.endDate()))
                .addValue("assignee", values.assigneeId()).addValue("active", values.active())
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()));
    }

    private static RuleView view(ResultSet rs, int row) throws SQLException {
        return new RuleView(rs.getLong("rule_id"), rs.getLong("project_id"), rs.getLong("template_id"),
                rs.getString("template_name"), rs.getString("template_name_ar"),
                RecurrenceSchedule.Frequency.valueOf(rs.getString("frequency")),
                rs.getObject("weekday", Integer.class), rs.getObject("month_day", Integer.class),
                date(rs, "start_date"), date(rs, "end_date"),
                rs.getObject("assignee_id", Long.class), rs.getString("assignee_name"), rs.getBoolean("is_active"),
                date(rs, "effective_from"), rs.getLong("owner_id"), rs.getString("owner_name"),
                time(rs, "last_run_at"), rs.getString("last_run_result"), date(rs, "last_run_occurrence_date"),
                rs.getObject("last_run_request_id", Long.class), time(rs, "created_at"), time(rs, "updated_at"),
                rs.getBoolean("project_active"), rs.getBoolean("work_type_active"),
                rs.getBoolean("assignee_eligible"), rs.getBoolean("owner_eligible"));
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
