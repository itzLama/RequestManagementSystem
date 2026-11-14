package com.requestmanagement.backend.usernotification;

import com.requestmanagement.backend.TestEmployeeFixtureSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Commits real data (events are sent after commit) and cleans it up afterwards. */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectLiveUpdateIntegrationTests {
    private static final String SECRET_TITLE = "Confidential live title";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Long> users = new ArrayList<>();
    private long project;
    private long otherProject;
    private long nora;

    @BeforeEach
    void setUp() {
        nora = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
        project = newProject();
        otherProject = newProject();
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, nora);
    }

    @AfterEach
    void cleanUp() {
        for (long id : List.of(project, otherProject)) {
            jdbc.update("DELETE FROM user_notifications WHERE project_id=?", id);
            jdbc.update("DELETE FROM comments WHERE request_id IN (SELECT request_id FROM requests WHERE project_id=?)", id);
            jdbc.update("DELETE FROM status_history WHERE request_id IN (SELECT request_id FROM requests WHERE project_id=?)", id);
            jdbc.update("DELETE FROM requests WHERE project_id=? AND parent_request_id IS NOT NULL", id);
            jdbc.update("DELETE FROM requests WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_memberships WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_work_types WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_status_columns WHERE project_id=?", id);
            jdbc.update("DELETE FROM projects WHERE project_id=?", id);
        }
        users.forEach(id -> jdbc.update("DELETE FROM users WHERE user_id=?", id));
        users.clear();
    }

    @Test
    void changesReachOnlyUsersWithProjectAccessAndCarryNoTaskContent() throws Exception {
        var outsider = employee("Live Outsider");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", otherProject, outsider.id());
        var former = employee("Live Former Member");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, former.id());
        Session admin = login("sara.saad@example.com"); Session member = login("nora.ahmed@example.com");
        Session stranger = login(outsider.email()); Session formerSession = login(former.email());
        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", project, former.id());

        MvcResult adminStream = stream(admin); MvcResult memberStream = stream(member);
        MvcResult strangerStream = stream(stranger); MvcResult formerStream = stream(formerSession);

        long task = createTask(admin);
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/status"), admin)
                .content("{\"statusId\":" + statusId("IN_PROGRESS") + "}")).andExpect(status().is2xxSuccessful());
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/dates"), admin)
                .content("{\"startDate\":null,\"dueDate\":\"2026-12-01\"}")).andExpect(status().is2xxSuccessful());

        List<JsonNode> received = projectChanges(memberStream);
        assertThat(received).extracting(node -> node.get("change").asString())
                .containsExactly("TASK_CREATED", "STATUS_CHANGED", "DATES_CHANGED");
        for (JsonNode node : received) {
            Set<String> fields = StreamSupport.stream(node.propertyNames().spliterator(), false).collect(Collectors.toSet());
            assertThat(fields).containsExactlyInAnyOrder("projectId", "taskId", "change");
            assertThat(node.get("projectId").asLong()).isEqualTo(project);
            assertThat(node.get("taskId").asLong()).isEqualTo(task);
        }
        // (Nora's separate assignment notification legitimately names the task; project-change events never do.)
        for (String block : memberStream.getResponse().getContentAsString().split("\n\n")) {
            if (block.contains("event:project-change")) assertThat(block).doesNotContain(SECRET_TITLE).doesNotContain("Secret details");
        }
        assertThat(projectChanges(adminStream)).hasSize(3);
        assertThat(projectChanges(strangerStream)).isEmpty();
        assertThat(projectChanges(formerStream)).isEmpty();
    }

    @Test
    void guestsCannotOpenTheStream() throws Exception {
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/stream").cookie(new Cookie("MASAR_GUEST_TRACKING", "any-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletionAndReassignmentAreSignalledToMembers() throws Exception {
        var peer = employee("Live Peer");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, peer.id());
        Session admin = login("sara.saad@example.com"); Session member = login("nora.ahmed@example.com");
        long task = createTask(admin);
        MvcResult memberStream = stream(member);

        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/assignee"), admin)
                .content("{\"assignedToId\":" + peer.id() + "}")).andExpect(status().is2xxSuccessful());
        mvc.perform(write(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/projects/" + project + "/tasks/" + task), admin)).andExpect(status().is2xxSuccessful());

        assertThat(projectChanges(memberStream)).extracting(node -> node.get("change").asString())
                .containsExactly("ASSIGNEE_CHANGED", "TASK_DELETED");
    }

    private List<JsonNode> projectChanges(MvcResult stream) throws Exception {
        String body = stream.getResponse().getContentAsString();
        List<JsonNode> events = new ArrayList<>();
        String[] blocks = body.split("\n\n");
        for (String block : blocks) {
            if (!block.contains("event:project-change")) continue;
            for (String line : block.split("\n")) {
                if (line.startsWith("data:")) events.add(json.readTree(line.substring(5)));
            }
        }
        return events;
    }

    private MvcResult stream(Session session) throws Exception {
        return mvc.perform(get("/api/notifications/stream").session(session.session()))
                .andExpect(request().asyncStarted()).andReturn();
    }

    private long createTask(Session admin) throws Exception {
        String body = mvc.perform(write(post("/api/projects/" + project + "/tasks"), admin)
                        .content("{\"title\":\"" + SECRET_TITLE + "\",\"description\":\"Secret details\",\"workTypeId\":"
                                + workTypeId(jdbc, project, "Task") + ",\"priority\":\"HIGH\",\"assignedToId\":" + nora + "}"))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long newProject() {
        return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",
                Long.class, "Live updates " + System.nanoTime());
    }

    private TestEmployeeFixtureSupport.EmployeeFixture employee(String name) {
        var fixture = TestEmployeeFixtureSupport.createActiveEmployee(jdbc, name);
        users.add(fixture.id());
        return fixture;
    }

    private long statusId(String semantic) {
        return jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?",
                Long.class, project, semantic);
    }

    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder, Session session) {
        return builder.session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType(MediaType.APPLICATION_JSON);
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
