package com.requestmanagement.backend.automation;

import com.requestmanagement.backend.TestEmployeeFixtureSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared fixtures for Stage 6 (Recurring Tasks, Workflow Automation, Roadmap drag). Not
 * @Transactional: automation and notifications run only after the business commit, so the tests
 * commit real data to masar_test and remove it afterwards.
 */
public abstract class WorkflowIntegrationTestBase {
    protected static final String DOCUMENT =
            "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Weekly checklist\"}]}]}";

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    protected final JsonMapper json = JsonMapper.builder().build();
    protected final List<Long> projects = new ArrayList<>();
    protected final List<Long> users = new ArrayList<>();
    protected long adminId;
    protected long nora;
    protected TestEmployeeFixtureSupport.EmployeeFixture peer;
    protected long project;
    protected long workType;
    protected long otherWorkType;

    @BeforeEach
    void createWorkflowFixtures() {
        adminId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='sara.saad@example.com'", Long.class);
        nora = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
        peer = TestEmployeeFixtureSupport.createActiveEmployee(jdbc, "Workflow Peer");
        users.add(peer.id());
        project = newProject("Workflow");
        workType = workTypeId(jdbc, project, "Maintenance");
        otherWorkType = workTypeId(jdbc, project, "Bug");
    }

    @AfterEach
    void removeWorkflowFixtures() {
        for (Long id : projects) {
            jdbc.update("DELETE FROM project_automation_rules WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_recurring_task_rules WHERE project_id=?", id);
            jdbc.update("DELETE FROM user_notifications WHERE project_id=?", id);
            jdbc.update("DELETE FROM comments WHERE request_id IN (SELECT request_id FROM requests WHERE project_id=?)", id);
            jdbc.update("DELETE FROM status_history WHERE request_id IN (SELECT request_id FROM requests WHERE project_id=?)", id);
            jdbc.update("DELETE FROM requests WHERE project_id=? AND parent_request_id IS NOT NULL", id);
            jdbc.update("DELETE FROM requests WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_task_templates WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_guest_form_settings WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_memberships WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_work_types WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_status_columns WHERE project_id=?", id);
            jdbc.update("DELETE FROM projects WHERE project_id=?", id);
        }
        for (Long id : users) jdbc.update("DELETE FROM users WHERE user_id=?", id);
        projects.clear();
        users.clear();
    }

    protected long newProject(String name) {
        long id = jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",
                Long.class, name + " " + System.nanoTime());
        projects.add(id);
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?),(?,?)", id, nora, id, peer.id());
        jdbc.update("INSERT INTO project_guest_form_settings(project_id,priority_required,description_required) VALUES (?,false,false) ON CONFLICT DO NOTHING", id);
        return id;
    }

    protected long template(Integer dueOffsetDays) {
        return jdbc.queryForObject("""
                INSERT INTO project_task_templates(project_id,template_name,template_name_ar,work_type_id,priority,
                        default_title,description,description_document,due_offset_days,created_by)
                VALUES (?,?,?,?,'HIGH','Weekly server check','Weekly checklist',CAST(? AS jsonb),?,?)
                RETURNING template_id
                """, Long.class, project, "Weekly check " + System.nanoTime(), "فحص أسبوعي " + System.nanoTime(),
                workType, DOCUMENT, dueOffsetDays, adminId);
    }

    protected long statusId(String semantic) {
        return statusId(project, semantic);
    }

    protected long statusId(long projectId, String semantic) {
        return jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?",
                Long.class, projectId, semantic);
    }

    protected long adminCreateTask(Session session, long assignee, String priority, long type) throws Exception {
        String body = mvc.perform(write(post("/api/projects/" + project + "/tasks"), session)
                        .content("{\"title\":\"Workflow task\",\"description\":\"Details\",\"workTypeId\":" + type
                                + ",\"priority\":\"" + priority + "\",\"assignedToId\":" + assignee + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    protected Long assigneeOf(long task) {
        return jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, task);
    }

    protected long statusOf(long task) {
        return jdbc.queryForObject("SELECT project_status_id FROM requests WHERE request_id=?", Long.class, task);
    }

    protected JsonNode body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder, Session session) {
        return builder.session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType(MediaType.APPLICATION_JSON);
    }

    protected Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        var result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    protected Session adminSession() throws Exception { return login("sara.saad@example.com"); }

    protected Session noraSession() throws Exception { return login("nora.ahmed@example.com"); }

    public record Session(MockHttpSession session, Cookie csrf) { }
}
