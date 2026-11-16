package com.requestmanagement.backend.projectinsights;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee;
import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectDashboardIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProjectDashboardService service;

    @Test
    void numbersUseRootTasksAndTheProjectsOwnCompletedStatuses() throws Exception {
        LocalDate today = service.today();
        assertThat(today).isEqualTo(LocalDate.now(ZoneId.of("Asia/Riyadh")));
        long nora = userId("nora.ahmed@example.com");
        long project = project("Dashboard numbers " + System.nanoTime()); member(project, nora);
        // A non-default completed status proves completion is not tied to status names or semantics.
        jdbc.update("UPDATE project_status_columns SET counts_as_completed=true WHERE project_id=? AND semantic_key='WAITING_USER'", project);

        long overdue = task(project, nora, null, "NEW", "HIGH", today.minusDays(2));
        task(project, nora, null, "COMPLETED", "HIGH", today.minusDays(1));          // completed: never overdue
        task(project, nora, null, "WAITING_USER", "MEDIUM", null);                   // completed via setting
        long upcoming = task(project, null, null, "IN_PROGRESS", "LOW", today.plusDays(3));
        task(project, nora, null, "NEW", "MEDIUM", today.plusDays(10));              // outside the 7-day window
        task(project, nora, null, "NEW", "LOW", today.plusDays(7));                  // last day of the window
        task(project, nora, overdue, "IN_PROGRESS", "HIGH", today.minusDays(9));     // subtask: ignored

        Session admin = login("sara.saad@example.com");
        mvc.perform(get("/api/projects/" + project + "/dashboard").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.upcomingWindowDays").value(7))
                .andExpect(jsonPath("$.completion.totalTasks").value(6))
                .andExpect(jsonPath("$.completion.completedTasks").value(2))
                .andExpect(jsonPath("$.completion.openTasks").value(4))
                .andExpect(jsonPath("$.completion.rejectedTasks").value(0))
                .andExpect(jsonPath("$.completion.percentage").value(33))
                .andExpect(jsonPath("$.statuses[?(@.statusName == 'New')].count").value(contains(3)))
                .andExpect(jsonPath("$.statuses[?(@.statusName == 'In Progress')].count").value(contains(1)))
                .andExpect(jsonPath("$.statuses[?(@.statusName == 'Waiting User')].countsAsCompleted").value(contains(true)))
                .andExpect(jsonPath("$.priorities[?(@.priority == 'HIGH')].count").value(contains(2)))
                .andExpect(jsonPath("$.priorities[?(@.priority == 'MEDIUM')].count").value(contains(2)))
                .andExpect(jsonPath("$.priorities[?(@.priority == 'LOW')].count").value(contains(2)))
                .andExpect(jsonPath("$.workload[?(@.assigneeId == " + nora + ")].openTasks").value(contains(3)))
                .andExpect(jsonPath("$.workload[?(@.assigneeId == " + nora + ")].overdueTasks").value(contains(1)))
                .andExpect(jsonPath("$.workload[?(@.assigneeId == null)].openTasks").value(contains(1)))
                .andExpect(jsonPath("$.overdueCount").value(1))
                .andExpect(jsonPath("$.overdueTasks", hasSize(1)))
                .andExpect(jsonPath("$.overdueTasks[0].taskId").value(overdue))
                .andExpect(jsonPath("$.upcomingCount").value(2))
                .andExpect(jsonPath("$.upcomingTasks[0].taskId").value(upcoming));
    }

    @Test
    void rejectedTasksAreClosedNotOpenUnlessTheColumnCountsAsCompleted() throws Exception {
        LocalDate today = service.today();
        long nora = userId("nora.ahmed@example.com");
        long project = project("Dashboard rejected " + System.nanoTime()); member(project, nora);
        long openOverdue = task(project, nora, null, "NEW", "HIGH", today.minusDays(2));
        task(project, nora, null, "REJECTED", "HIGH", today.minusDays(3));           // rejected + past due
        task(project, null, null, "REJECTED", "LOW", today.plusDays(2));             // rejected + due soon, unassigned
        task(project, nora, null, "COMPLETED", "MEDIUM", null);
        // Renaming keeps the system REJECTED tag.
        jdbc.update("UPDATE project_status_columns SET status_name='Declined' WHERE project_id=? AND semantic_key='REJECTED'", project);

        Session admin = login("sara.saad@example.com");
        mvc.perform(get("/api/projects/" + project + "/dashboard").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completion.totalTasks").value(4))
                .andExpect(jsonPath("$.completion.completedTasks").value(1))
                .andExpect(jsonPath("$.completion.rejectedTasks").value(2))
                .andExpect(jsonPath("$.completion.openTasks").value(1))
                .andExpect(jsonPath("$.completion.percentage").value(50))     // 1 / (4 - 2 rejected)
                .andExpect(jsonPath("$.statuses[?(@.statusName == 'Declined')].count").value(contains(2)))
                .andExpect(jsonPath("$.workload[?(@.assigneeId == " + nora + ")].openTasks").value(contains(1)))
                .andExpect(jsonPath("$.workload[?(@.assigneeId == " + nora + ")].overdueTasks").value(contains(1)))
                .andExpect(jsonPath("$.workload[?(@.assigneeId == null)].openTasks").value(contains(0)))
                .andExpect(jsonPath("$.overdueCount").value(1))
                .andExpect(jsonPath("$.overdueTasks[0].taskId").value(openOverdue))
                .andExpect(jsonPath("$.upcomingCount").value(0));

        // The Counts as Completed setting wins over the REJECTED tag.
        jdbc.update("UPDATE project_status_columns SET counts_as_completed=true WHERE project_id=? AND semantic_key='REJECTED'", project);
        mvc.perform(get("/api/projects/" + project + "/dashboard").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completion.completedTasks").value(3))
                .andExpect(jsonPath("$.completion.rejectedTasks").value(0))
                .andExpect(jsonPath("$.completion.openTasks").value(1))
                .andExpect(jsonPath("$.completion.percentage").value(75))
                .andExpect(jsonPath("$.overdueCount").value(1))
                .andExpect(jsonPath("$.upcomingCount").value(0));
    }

    @Test
    void completionRateLeavesRejectedTasksOutOfTheDenominator() throws Exception {
        Session admin = login("sara.saad@example.com");

        // 8 completed + 2 rejected -> 8 / (10 - 2) = 100%.
        long full = project("Dashboard rate full " + System.nanoTime());
        for (int i = 0; i < 8; i++) task(full, null, null, "COMPLETED", "LOW", null);
        for (int i = 0; i < 2; i++) task(full, null, null, "REJECTED", "LOW", null);
        mvc.perform(get("/api/projects/" + full + "/dashboard").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completion.totalTasks").value(10))
                .andExpect(jsonPath("$.completion.completedTasks").value(8))
                .andExpect(jsonPath("$.completion.rejectedTasks").value(2))
                .andExpect(jsonPath("$.completion.openTasks").value(0))
                .andExpect(jsonPath("$.completion.percentage").value(100));

        // Only rejected tasks -> denominator 0 -> 0%, no division by zero.
        long allRejected = project("Dashboard rate rejected " + System.nanoTime());
        for (int i = 0; i < 3; i++) task(allRejected, null, null, "REJECTED", "LOW", null);
        mvc.perform(get("/api/projects/" + allRejected + "/dashboard").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completion.totalTasks").value(3))
                .andExpect(jsonPath("$.completion.completedTasks").value(0))
                .andExpect(jsonPath("$.completion.rejectedTasks").value(3))
                .andExpect(jsonPath("$.completion.openTasks").value(0))
                .andExpect(jsonPath("$.completion.percentage").value(0));

        // No tasks at all -> 0% (unchanged empty-project behaviour).
        long empty = project("Dashboard rate empty " + System.nanoTime());
        mvc.perform(get("/api/projects/" + empty + "/dashboard").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completion.totalTasks").value(0))
                .andExpect(jsonPath("$.completion.rejectedTasks").value(0))
                .andExpect(jsonPath("$.completion.percentage").value(0));
    }

    @Test
    void recentActivityComesFromRealRecordsAndHidesInternalNotes() throws Exception {
        long nora = userId("nora.ahmed@example.com"); long sara = userId("sara.saad@example.com");
        long project = project("Dashboard activity " + System.nanoTime()); member(project, nora);
        long task = task(project, nora, null, "NEW", "MEDIUM", null);
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        jdbc.update("UPDATE requests SET created_at=? WHERE request_id=?", Timestamp.valueOf(base), task);
        jdbc.update("""
                INSERT INTO status_history(request_id,old_status,new_status,old_project_status_id,new_project_status_id,changed_by,change_note,changed_at)
                SELECT ?, 'NEW', 'IN_PROGRESS', o.status_id, n.status_id, ?, 'moved', ?
                  FROM project_status_columns o, project_status_columns n
                 WHERE o.project_id=? AND o.semantic_key='NEW' AND n.project_id=? AND n.semantic_key='IN_PROGRESS'
                """, task, nora, Timestamp.valueOf(base.plusMinutes(1)), project, project);
        jdbc.update("INSERT INTO comments(request_id,user_id,comment_text,is_internal,created_at) VALUES (?,?,?,false,?)",
                task, nora, "Visible", Timestamp.valueOf(base.plusMinutes(2)));
        jdbc.update("INSERT INTO comments(request_id,user_id,comment_text,is_internal,created_at) VALUES (?,?,?,true,?)",
                task, sara, "Internal only", Timestamp.valueOf(base.plusMinutes(3)));

        Session member = login("nora.ahmed@example.com");
        mvc.perform(get("/api/projects/mine/" + project + "/dashboard").session(member.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentActivity", hasSize(3)))
                .andExpect(jsonPath("$.recentActivity[0].kind").value("COMMENT_ADDED"))
                .andExpect(jsonPath("$.recentActivity[0].actorName").value("Nora Ahmed"))
                .andExpect(jsonPath("$.recentActivity[1].kind").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.recentActivity[1].statusName").value("In Progress"))
                .andExpect(jsonPath("$.recentActivity[2].kind").value("TASK_CREATED"))
                .andExpect(jsonPath("$.recentActivity[2].taskId").value(task));
    }

    @Test
    void onlyAdminsAndMembersCanReadAndArchivedProjectsStayViewable() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        var outsider = createActiveEmployee(jdbc, "Dashboard Outsider");
        long project = project("Dashboard access " + System.nanoTime()); member(project, nora);
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        Session admin = login("sara.saad@example.com"); Session member = login("nora.ahmed@example.com");
        Session stranger = login(outsider.email());

        mvc.perform(get("/api/projects/" + project + "/dashboard").session(admin.session())).andExpect(status().isOk());
        mvc.perform(get("/api/projects/mine/" + project + "/dashboard").session(member.session())).andExpect(status().isOk());
        mvc.perform(get("/api/projects/mine/" + project + "/dashboard").session(stranger.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/" + project + "/dashboard").session(member.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/mine/" + project + "/dashboard").session(admin.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/mine/" + project + "/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/" + project + "/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/999999999/dashboard").session(admin.session())).andExpect(status().isNotFound());
    }

    private long project(String name) {
        return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id", Long.class, name);
    }

    private long task(long project, Long assignee, Long parent, String semantic, String priority, LocalDate due) {
        long workType = workTypeId(jdbc, project, "Task");
        long status = jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?",
                Long.class, project, semantic);
        long creator = userId("sara.saad@example.com");
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,created_by,
                                     project_id,assigned_to,parent_request_id,due_date)
                VALUES ('Dashboard','Details',?,?,?,?,?,?,?,?) RETURNING request_id
                """, Long.class, workType, priority, status, creator, project, assignee, parent,
                due == null ? null : Date.valueOf(due));
    }

    private void member(long project, long employee) {
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    private Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        var result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
