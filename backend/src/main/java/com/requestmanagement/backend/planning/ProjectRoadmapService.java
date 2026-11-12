package com.requestmanagement.backend.planning;

import com.requestmanagement.backend.project.EmployeeProjectService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.project.ProjectResponse;
import com.requestmanagement.backend.project.ProjectStatus;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Global Roadmap: one bar per Project, spanning its root Tasks' planning dates.
 * Admins see every Project; Employees see exactly the Projects returned by "My Projects".
 */
@Service
public class ProjectRoadmapService {
    private static final String AGGREGATE_SQL = """
            SELECT p.project_id, p.project_name, p.status,
                   MIN(LEAST(COALESCE(r.start_date, r.due_date), COALESCE(r.due_date, r.start_date))) AS span_start,
                   MAX(GREATEST(COALESCE(r.start_date, r.due_date), COALESCE(r.due_date, r.start_date))) AS span_end,
                   COUNT(r.request_id) AS total_tasks,
                   COUNT(r.request_id) FILTER (WHERE r.start_date IS NOT NULL OR r.due_date IS NOT NULL) AS scheduled_tasks,
                   COUNT(r.request_id) FILTER (WHERE r.due_date < :today AND NOT s.counts_as_completed) AS overdue_tasks
              FROM projects p
              LEFT JOIN requests r ON r.project_id = p.project_id AND r.parent_request_id IS NULL
              LEFT JOIN project_status_columns s ON s.status_id = r.project_status_id
             WHERE p.project_id IN (:projectIds)
             GROUP BY p.project_id, p.project_name, p.status
             ORDER BY span_start NULLS LAST, LOWER(p.project_name), p.project_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ProjectRepository projectRepository;
    private final EmployeeProjectService employeeProjectService;
    private final UserRepository userRepository;
    private final ZoneId planningZone;

    public ProjectRoadmapService(NamedParameterJdbcTemplate jdbc, ProjectRepository projectRepository,
                                 EmployeeProjectService employeeProjectService, UserRepository userRepository,
                                 @Value("${masar.planning.time-zone:Asia/Riyadh}") String planningZone) {
        this.jdbc = jdbc;
        this.projectRepository = projectRepository;
        this.employeeProjectService = employeeProjectService;
        this.userRepository = userRepository;
        this.planningZone = ZoneId.of(planningZone);
    }

    @Transactional(readOnly = true)
    public ProjectRoadmapDtos.Response adminRoadmap(Long adminId) {
        requireActiveAdmin(adminId);
        List<Long> ids = projectRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(project -> project.getId()).toList();
        return roadmap(ids);
    }

    @Transactional(readOnly = true)
    public ProjectRoadmapDtos.Response employeeRoadmap(Long employeeId) {
        List<Long> ids = employeeProjectService.listMine(employeeId).stream().map(ProjectResponse::id).toList();
        return roadmap(ids);
    }

    public LocalDate today() {
        return LocalDate.now(planningZone);
    }

    private ProjectRoadmapDtos.Response roadmap(List<Long> projectIds) {
        LocalDate today = today();
        if (projectIds.isEmpty()) return new ProjectRoadmapDtos.Response(today, List.of());
        var params = new MapSqlParameterSource().addValue("projectIds", projectIds).addValue("today", Date.valueOf(today));
        List<ProjectRoadmapDtos.ProjectSpan> spans = jdbc.query(AGGREGATE_SQL, params, (rs, row) -> {
            Date start = rs.getDate("span_start");
            Date end = rs.getDate("span_end");
            return new ProjectRoadmapDtos.ProjectSpan(rs.getLong("project_id"), rs.getString("project_name"),
                    ProjectStatus.valueOf(rs.getString("status")),
                    start == null ? null : start.toLocalDate(), end == null ? null : end.toLocalDate(),
                    rs.getLong("total_tasks"), rs.getLong("scheduled_tasks"), rs.getLong("overdue_tasks"));
        });
        return new ProjectRoadmapDtos.Response(today, spans);
    }

    private User requireActiveAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Administrator access is required."));
    }
}
