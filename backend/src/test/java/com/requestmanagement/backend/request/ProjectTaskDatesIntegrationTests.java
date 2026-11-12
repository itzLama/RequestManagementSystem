package com.requestmanagement.backend.request;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee;
import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectTaskDatesIntegrationTests {
    private static final String DOC = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Details\"}]}]}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void createWithDatesExposesThemOnBoardAndDetailsAndRejectsReversedRange() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long project = project("Dates create"); member(project, nora);
        long workType = workTypeId(jdbc, project, "Task");
        Session admin = login("sara.saad@example.com");

        String created = mvc.perform(post("/api/projects/{p}/tasks", project).session(admin.session())
                        .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content(createBody(workType, nora, "\"2026-11-16\"", "\"2026-11-20\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2026-11-16"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-20"))
                .andReturn().getResponse().getContentAsString();
        long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        mvc.perform(get("/api/projects/{p}/tasks/board", project).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].dueDate").value("2026-11-20"));
        mvc.perform(get("/api/projects/{p}/tasks/{t}", project, taskId).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startDate").value("2026-11-16"));

        Session member = login("nora.ahmed@example.com");
        mvc.perform(post("/api/projects/mine/{p}/tasks", project).session(member.session())
                        .cookie(member.csrf()).header("X-XSRF-TOKEN", member.csrf().getValue())
                        .contentType("application/json")
                        .content(createBody(workType, nora, "\"2026-11-21\"", "\"2026-11-20\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/projects/mine/{p}/tasks", project).session(member.session())
                        .cookie(member.csrf()).header("X-XSRF-TOKEN", member.csrf().getValue())
                        .contentType("application/json").content(createBody(workType, nora, "null", "null")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dueDate").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void membersAndAdminsRescheduleTasksAndSubtasksAndCanClearDates() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long project = project("Dates update"); member(project, nora);
        long parent = task(project, nora, null); long child = task(project, nora, parent);
        Session admin = login("sara.saad@example.com"); Session member = login("nora.ahmed@example.com");

        employeeDates(member, project, parent, "\"2026-11-15\"", "\"2026-11-30\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-11-15"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-30"));
        adminDates(admin, project, child, "null", "\"2026-11-18\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2026-11-18"));
        mvc.perform(get("/api/projects/{p}/tasks/{t}", project, parent).session(admin.session()))
                .andExpect(jsonPath("$.subtasks[0].dueDate").value("2026-11-18"));
        employeeDates(member, project, parent, "null", "null").andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT start_date, due_date FROM requests WHERE request_id=?", parent))
                .containsEntry("start_date", null).containsEntry("due_date", null);
        employeeDates(member, project, parent, "\"2026-12-02\"", "\"2026-12-01\"").andExpect(status().isBadRequest());
    }

    @Test
    void enforcesMembershipCsrfArchivedReadOnlyAndProjectOnlyDates() throws Exception {
        long nora = userId("nora.ahmed@example.com"); var outsiderFixture = createActiveEmployee(jdbc, "Dates Outsider");
        long project = project("Dates access"); member(project, nora);
        long task = task(project, nora, null);
        Session member = login("nora.ahmed@example.com"); Session outsider = login(outsiderFixture.email());

        employeeDates(outsider, project, task, "null", "\"2026-11-20\"").andExpect(status().isNotFound());
        mvc.perform(patch("/api/projects/mine/{p}/tasks/{t}/dates", project, task).session(member.session())
                        .contentType("application/json").content(datesBody("null", "\"2026-11-20\"")))
                .andExpect(status().isForbidden());

        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project); entityManager.clear();
        employeeDates(member, project, task, "null", "\"2026-11-20\"").andExpect(status().isConflict());
        Session admin = login("sara.saad@example.com");
        adminDates(admin, project, task, "null", "\"2026-11-20\"").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT due_date FROM requests WHERE request_id=?", java.sql.Date.class, task)).isNull();

        long type = jdbc.queryForObject("SELECT type_id FROM request_types ORDER BY type_id LIMIT 1", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO requests(title,description,type_id,priority,status,created_by,due_date)
                VALUES ('General','Details',?,'MEDIUM','NEW',?,DATE '2026-11-20')
                """, type, nora)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private String createBody(long workType, long assignee, String start, String due) {
        return "{\"title\":\"Dated\",\"descriptionDocument\":" + DOC + ",\"workTypeId\":" + workType
                + ",\"priority\":\"MEDIUM\",\"assignedToId\":" + assignee
                + ",\"startDate\":" + start + ",\"dueDate\":" + due + "}";
    }

    private String datesBody(String start, String due) {
        return "{\"startDate\":" + start + ",\"dueDate\":" + due + "}";
    }

    private ResultActions employeeDates(Session s, long project, long task, String start, String due) throws Exception {
        return mvc.perform(patch("/api/projects/mine/{p}/tasks/{t}/dates", project, task).session(s.session())
                .cookie(s.csrf()).header("X-XSRF-TOKEN", s.csrf().getValue())
                .contentType("application/json").content(datesBody(start, due)));
    }

    private ResultActions adminDates(Session s, long project, long task, String start, String due) throws Exception {
        return mvc.perform(patch("/api/projects/{p}/tasks/{t}/dates", project, task).session(s.session())
                .cookie(s.csrf()).header("X-XSRF-TOKEN", s.csrf().getValue())
                .contentType("application/json").content(datesBody(start, due)));
    }

    private long project(String name) {
        return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id", Long.class, name);
    }

    private long task(long project, long creator, Long parent) {
        long workType = workTypeId(jdbc, project, "Task");
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,created_by,
                                     project_id,assigned_to,parent_request_id)
                VALUES ('Planned','Details',?,'MEDIUM',?,?,?,?,?) RETURNING request_id
                """, Long.class, workType, statusId(project, "NEW"), creator, project, creator, parent);
    }

    private long statusId(long project, String key) {
        return jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?", Long.class, project, key);
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
