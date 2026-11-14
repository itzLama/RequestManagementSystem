package com.requestmanagement.backend.projectinsights;

import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.request.RequestPriority;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-Project dashboard. Task metrics use root Tasks only (subtasks stay in Task Details) and the
 * Project's own Counts-as-Completed statuses; status names are never hardcoded. Overdue and
 * upcoming follow the Stage 4 rule with dates in the planning time zone (Asia/Riyadh).
 * Readable for archived Projects.
 */
@Service
public class ProjectDashboardService {
    public static final int UPCOMING_WINDOW_DAYS = 7;
    static final int LIST_LIMIT = 10;
    static final int ACTIVITY_LIMIT = 15;

    private static final String ROOT_TASKS = """
              FROM requests r
              JOIN project_status_columns s ON s.status_id = r.project_status_id
             WHERE r.project_id = :project AND r.parent_request_id IS NULL
            """;

    private static final String TASK_REF_SELECT = """
            SELECT r.request_id, r.title, r.priority, s.status_id, s.status_name, s.semantic_key, r.assigned_to,
                   u.full_name AS assignee_name, r.start_date, r.due_date
              FROM requests r
              JOIN project_status_columns s ON s.status_id = r.project_status_id
              LEFT JOIN users u ON u.user_id = r.assigned_to
             WHERE r.project_id = :project AND r.parent_request_id IS NULL AND NOT s.counts_as_completed
            """;

    private static final String ACTIVITY_SQL = """
            SELECT * FROM (
                SELECT 'TASK_CREATED' AS kind, r.request_id AS task_id, r.title, r.parent_request_id,
                       COALESCE(u.full_name, r.guest_name) AS actor_name, (r.created_by IS NULL) AS actor_is_guest,
                       NULL AS status_name, NULL AS status_semantic, NULL AS file_name, r.created_at AS occurred_at, 1 AS kind_order, r.request_id AS source_id
                  FROM requests r LEFT JOIN users u ON u.user_id = r.created_by
                 WHERE r.project_id = :project
                UNION ALL
                SELECT 'STATUS_CHANGED', r.request_id, r.title, r.parent_request_id, u.full_name, FALSE,
                       ns.status_name, ns.semantic_key, NULL, h.changed_at, 2, h.history_id
                  FROM status_history h
                  JOIN requests r ON r.request_id = h.request_id
                  JOIN users u ON u.user_id = h.changed_by
                  JOIN project_status_columns ns ON ns.status_id = h.new_project_status_id
                 WHERE r.project_id = :project AND h.old_project_status_id IS NOT NULL
                UNION ALL
                SELECT 'COMMENT_ADDED', r.request_id, r.title, r.parent_request_id, u.full_name, FALSE,
                       NULL, NULL, NULL, c.created_at, 3, c.comment_id
                  FROM comments c
                  JOIN requests r ON r.request_id = c.request_id
                  JOIN users u ON u.user_id = c.user_id
                 WHERE r.project_id = :project AND NOT c.is_internal
                UNION ALL
                SELECT 'ATTACHMENT_UPLOADED', r.request_id, r.title, r.parent_request_id,
                       COALESCE(u.full_name, r.guest_name), (a.upload_source = 'GUEST'),
                       NULL, NULL, a.original_filename, a.created_at, 4, a.attachment_id
                  FROM task_attachments a
                  JOIN requests r ON r.request_id = a.request_id
                  LEFT JOIN users u ON u.user_id = a.uploaded_by_user_id
                 WHERE a.project_id = :project AND a.storage_status = 'AVAILABLE' AND a.deleted_at IS NULL
            ) activity
            ORDER BY occurred_at DESC, kind_order DESC, source_id DESC
            LIMIT :limit
            """;

    private static final RowMapper<ProjectDashboardDtos.TaskRef> TASK_REF = (rs, row) -> new ProjectDashboardDtos.TaskRef(
            rs.getLong("request_id"), rs.getString("title"), RequestPriority.valueOf(rs.getString("priority")),
            rs.getLong("status_id"), rs.getString("status_name"), rs.getString("semantic_key"), rs.getObject("assigned_to", Long.class),
            rs.getString("assignee_name"), toLocalDate(rs.getDate("start_date")), toLocalDate(rs.getDate("due_date")));

    private final NamedParameterJdbcTemplate jdbc;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final UserRepository userRepository;
    private final ZoneId planningZone;

    public ProjectDashboardService(NamedParameterJdbcTemplate jdbc, ProjectRepository projectRepository,
                                   ProjectAccessService projectAccessService, UserRepository userRepository,
                                   @Value("${masar.planning.time-zone:Asia/Riyadh}") String planningZone) {
        this.jdbc = jdbc;
        this.projectRepository = projectRepository;
        this.projectAccessService = projectAccessService;
        this.userRepository = userRepository;
        this.planningZone = ZoneId.of(planningZone);
    }

    @Transactional(readOnly = true)
    public ProjectDashboardDtos.Response adminDashboard(Long projectId, Long adminId) {
        userRepository.findById(adminId).filter(User::isActive).filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required."));
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }
        return dashboard(projectId);
    }

    /** Members only; archived Projects remain viewable. Non-members get "not found". */
    @Transactional(readOnly = true)
    public ProjectDashboardDtos.Response memberDashboard(Long projectId, Long employeeId) {
        projectAccessService.requireAccessibleProject(projectId, employeeId);
        return dashboard(projectId);
    }

    public LocalDate today() {
        return LocalDate.now(planningZone);
    }

    private ProjectDashboardDtos.Response dashboard(Long projectId) {
        LocalDate today = today();
        var params = new MapSqlParameterSource("project", projectId)
                .addValue("today", Date.valueOf(today))
                .addValue("upcomingEnd", Date.valueOf(today.plusDays(UPCOMING_WINDOW_DAYS)))
                .addValue("listLimit", LIST_LIMIT)
                .addValue("limit", ACTIVITY_LIMIT);

        ProjectDashboardDtos.Completion completion = jdbc.queryForObject("""
                SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE s.counts_as_completed) AS completed
                """ + ROOT_TASKS, params, (rs, row) -> {
            long total = rs.getLong("total");
            long completed = rs.getLong("completed");
            int percentage = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
            return new ProjectDashboardDtos.Completion(total, completed, total - completed, percentage);
        });

        List<ProjectDashboardDtos.StatusCount> statuses = jdbc.query("""
                SELECT s.status_id, s.status_name, s.semantic_key, s.counts_as_completed, s.is_active, s.display_order,
                       COUNT(r.request_id) AS task_count
                  FROM project_status_columns s
                  LEFT JOIN requests r ON r.project_status_id = s.status_id AND r.parent_request_id IS NULL
                 WHERE s.project_id = :project
                 GROUP BY s.status_id, s.status_name, s.semantic_key, s.counts_as_completed, s.is_active, s.display_order
                HAVING s.is_active OR COUNT(r.request_id) > 0
                 ORDER BY s.is_active DESC, s.display_order, s.status_id
                """, params, (rs, row) -> new ProjectDashboardDtos.StatusCount(rs.getLong("status_id"),
                rs.getString("status_name"), rs.getString("semantic_key"), rs.getBoolean("counts_as_completed"), rs.getBoolean("is_active"),
                rs.getInt("display_order"), rs.getLong("task_count")));

        Map<RequestPriority, Long> priorityCounts = new LinkedHashMap<>();
        Arrays.stream(RequestPriority.values()).forEach(priority -> priorityCounts.put(priority, 0L));
        jdbc.query("SELECT r.priority, COUNT(*) AS task_count " + ROOT_TASKS + " GROUP BY r.priority", params,
                rs -> { priorityCounts.put(RequestPriority.valueOf(rs.getString("priority")), rs.getLong("task_count")); });
        List<ProjectDashboardDtos.PriorityCount> priorities = priorityCounts.entrySet().stream()
                .map(entry -> new ProjectDashboardDtos.PriorityCount(entry.getKey(), entry.getValue())).toList();

        List<ProjectDashboardDtos.Workload> workload = workload(params);

        long overdueCount = count(TASK_REF_SELECT + " AND r.due_date < :today", params);
        List<ProjectDashboardDtos.TaskRef> overdue = jdbc.query(TASK_REF_SELECT
                + " AND r.due_date < :today ORDER BY r.due_date, r.request_id LIMIT :listLimit", params, TASK_REF);
        String upcomingFilter = " AND r.due_date >= :today AND r.due_date <= :upcomingEnd";
        long upcomingCount = count(TASK_REF_SELECT + upcomingFilter, params);
        List<ProjectDashboardDtos.TaskRef> upcoming = jdbc.query(TASK_REF_SELECT + upcomingFilter
                + " ORDER BY r.due_date, r.request_id LIMIT :listLimit", params, TASK_REF);

        List<ProjectDashboardDtos.Activity> activity = jdbc.query(ACTIVITY_SQL, params, (rs, row) ->
                new ProjectDashboardDtos.Activity(ProjectDashboardDtos.ActivityKind.valueOf(rs.getString("kind")),
                        rs.getLong("task_id"), rs.getString("title"), rs.getObject("parent_request_id", Long.class),
                        rs.getString("actor_name"), rs.getBoolean("actor_is_guest"), rs.getString("status_name"), rs.getString("status_semantic"),
                        rs.getString("file_name"), toLocalDateTime(rs.getTimestamp("occurred_at"))));

        return new ProjectDashboardDtos.Response(projectId, today, UPCOMING_WINDOW_DAYS, completion, statuses,
                priorities, workload, overdueCount, overdue, upcomingCount, upcoming, activity);
    }

    /** Current members (even with zero open tasks), anyone else holding open tasks, then Unassigned. */
    private List<ProjectDashboardDtos.Workload> workload(MapSqlParameterSource params) {
        List<ProjectDashboardDtos.Workload> rows = new ArrayList<>(jdbc.query("""
                WITH open_tasks AS (
                    SELECT r.assigned_to, r.due_date
                      FROM requests r
                      JOIN project_status_columns s ON s.status_id = r.project_status_id
                     WHERE r.project_id = :project AND r.parent_request_id IS NULL AND NOT s.counts_as_completed
                ), people AS (
                    SELECT m.employee_id AS user_id FROM project_memberships m WHERE m.project_id = :project
                    UNION
                    SELECT assigned_to FROM open_tasks WHERE assigned_to IS NOT NULL
                )
                SELECT u.user_id, u.full_name,
                       COUNT(o.assigned_to) AS open_tasks,
                       COUNT(o.assigned_to) FILTER (WHERE o.due_date < :today) AS overdue_tasks
                  FROM people p
                  JOIN users u ON u.user_id = p.user_id
                  LEFT JOIN open_tasks o ON o.assigned_to = p.user_id
                 GROUP BY u.user_id, u.full_name
                 ORDER BY open_tasks DESC, LOWER(u.full_name), u.user_id
                """, params, (rs, row) -> new ProjectDashboardDtos.Workload(rs.getLong("user_id"),
                rs.getString("full_name"), rs.getLong("open_tasks"), rs.getLong("overdue_tasks"))));
        jdbc.query("""
                SELECT COUNT(*) AS open_tasks, COUNT(*) FILTER (WHERE r.due_date < :today) AS overdue_tasks
                  FROM requests r
                  JOIN project_status_columns s ON s.status_id = r.project_status_id
                 WHERE r.project_id = :project AND r.parent_request_id IS NULL
                   AND NOT s.counts_as_completed AND r.assigned_to IS NULL
                """, params, rs -> {
            rows.add(new ProjectDashboardDtos.Workload(null, null, rs.getLong("open_tasks"), rs.getLong("overdue_tasks")));
        });
        return rows;
    }

    private long count(String taskRefQuery, MapSqlParameterSource params) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM (" + taskRefQuery + ") counted", params, Long.class);
        return count == null ? 0 : count;
    }

    private static LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
