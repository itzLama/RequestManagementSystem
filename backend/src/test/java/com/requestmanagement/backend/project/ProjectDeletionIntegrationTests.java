package com.requestmanagement.backend.project;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectDeletionIntegrationTests {
    private static final String CONFLICT_MESSAGE =
            "Project has persisted activity and must be archived instead.";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminDeletesUnusedProjectWhichDisappearsFromListAndDetails() throws Exception {
        long project = project("Unused Project", "ACTIVE");
        Session admin = login("sara.saad@example.com");

        deleteProject(project, admin).andExpect(status().isNoContent());

        assertThat(projectCount(project)).isZero();
        mvc.perform(get("/api/projects/{id}", project).session(admin.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/projects").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + project + ")]").isEmpty());
    }

    @Test
    void membershipBlocksDeletionAndRemains() throws Exception {
        long project = project("Membership History", "ACTIVE");
        long employee = userId("nora.ahmed@example.com");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee);

        assertConflict(project, login("sara.saad@example.com"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_memberships WHERE project_id=? AND employee_id=?",
                Integer.class, project, employee)).isOne();
        assertThat(projectCount(project)).isOne();
    }

    @Test
    void authenticatedProjectTaskBlocksDeletionAndRemains() throws Exception {
        long project = project("Task History", "ACTIVE");
        long task = authenticatedTask(project);

        assertConflict(project, login("sara.saad@example.com"));

        assertThat(requestCount(task)).isOne();
        assertThat(projectCount(project)).isOne();
    }

    @Test
    void guestCreatedProjectRequestBlocksDeletionAndHistoryRemains() throws Exception {
        long project = project("Guest Task History", "ACTIVE");
        long task = guestTask(project);
        long admin = userId("sara.saad@example.com");
        jdbc.update("INSERT INTO comments(request_id,user_id,comment_text,is_internal) VALUES (?,?,?,false)",
                task, admin, "Guest task comment");
        jdbc.update("INSERT INTO status_history(request_id,old_status,new_status,changed_by,change_note) VALUES (?,'NEW','IN_PROGRESS',?,?)",
                task, admin, "Started guest task");
        jdbc.update("INSERT INTO guest_communications(request_id,sent_by,message) VALUES (?,?,?)",
                task, admin, "Guest task update");

        assertConflict(project, login("sara.saad@example.com"));

        assertThat(requestCount(task)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE request_id=?", Integer.class, task)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Integer.class, task)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guest_communications WHERE request_id=?", Integer.class, task)).isOne();
    }

    @Test
    void archivedProjectUsesTheSameDependencySafetyChecks() throws Exception {
        long project = project("Archived Membership History", "ARCHIVED");
        long employee = userId("nora.ahmed@example.com");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee);

        assertConflict(project, login("sara.saad@example.com"));

        assertThat(jdbc.queryForObject("SELECT status FROM projects WHERE project_id=?", String.class, project))
                .isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_memberships WHERE project_id=?", Integer.class, project))
                .isOne();
    }

    @Test
    void adminAuthorizationMissingTargetsAndCsrfAreEnforced() throws Exception {
        Session admin = login("sara.saad@example.com");
        Session employee = login("nora.ahmed@example.com");
        long target = project("Authorization Target", "ACTIVE");

        deleteProject(999999999L, admin).andExpect(status().isNotFound());
        deleteProject(target, employee).andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/{id}", target).session(admin.session()))
                .andExpect(status().isForbidden());
        assertThat(projectCount(target)).isOne();
    }

    private void assertConflict(long project, Session admin) throws Exception {
        deleteProject(project, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CONFLICT_MESSAGE));
    }

    private org.springframework.test.web.servlet.ResultActions deleteProject(long id, Session session)
            throws Exception {
        return mvc.perform(delete("/api/projects/{id}", id).session(session.session())
                .cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()));
    }

    private long project(String name, String status) {
        return jdbc.queryForObject(
                "INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id",
                Long.class, name + " " + UUID.randomUUID(), status);
    }

    private long authenticatedTask(long project) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,project_work_type_id,priority,status,created_by,project_id)
                VALUES (?,'Project deletion test',NULL,?,'MEDIUM','NEW',?,?) RETURNING request_id
                """, Long.class, "Authenticated task " + UUID.randomUUID(), workTypeId(jdbc, project, "Task"), userId("nora.ahmed@example.com"), project);
    }

    private long guestTask(long project) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,project_work_type_id,priority,status,created_by,guest_name,guest_email,project_id)
                VALUES (?,'Guest project deletion test',NULL,?,'MEDIUM','NEW',NULL,'Guest User','guest@example.com',?)
                RETURNING request_id
                """, Long.class, "Guest task " + UUID.randomUUID(), workTypeId(jdbc, project, "Task"), project);
    }

    private int projectCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE project_id=?", Integer.class, id);
    }

    private int requestCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=?", Integer.class, id);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    private Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
