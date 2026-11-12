package com.requestmanagement.backend.request;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectRichDescriptionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void internalCreationStoresRichRootAndSubtaskAtomicallyUsingServerDerivedText() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Rich creation", "ACTIVE");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee);
        long workType = workTypeId(jdbc, project, "Task");
        long newStatus = statusId(project, "NEW");
        Session admin = login("sara.saad@example.com");

        MvcResult rootResult = mvc.perform(post("/api/projects/{p}/tasks", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content(richCreateInput("Rich root", employee, workType, null)))
                .andExpect(status().isCreated()).andReturn();
        long root = ((Number) com.jayway.jsonpath.JsonPath.read(
                rootResult.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(post("/api/projects/{p}/tasks/{t}/subtasks", project, root)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content(richCreateInput("Rich child", employee, workType, newStatus)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Documentation\nHeader\tValue"))
                .andExpect(jsonPath("$.descriptionDocument.content[1].type").value("table"));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT description FROM requests WHERE request_id=?", String.class, root))
                .isEqualTo("Documentation\nHeader\tValue");
        assertThat(jdbc.queryForObject("SELECT description_document IS NOT NULL FROM requests WHERE request_id=?",
                Boolean.class, root)).isTrue();
    }

    @Test
    void plainCreationRemainsCompatibleAndInvalidRichCreationRollsBack() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Rich creation compatibility", "ACTIVE");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee);
        long workType = workTypeId(jdbc, project, "Task");
        Session admin = login("sara.saad@example.com");
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE project_id=?", Long.class, project);

        mvc.perform(post("/api/projects/{p}/tasks", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"title\":\"Plain compatible\",\"description\":\"Plain details\",\"workTypeId\":"
                                + workType + ",\"priority\":\"MEDIUM\",\"assignedToId\":" + employee + "}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/projects/{p}/tasks", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"title\":\"Invalid rich\",\"descriptionDocument\":{\"type\":\"doc\",\"content\":[{\"type\":\"image\"}]},\"workTypeId\":"
                                + workType + ",\"priority\":\"MEDIUM\",\"assignedToId\":" + employee + "}"))
                .andExpect(status().isBadRequest());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE project_id=?", Long.class, project))
                .isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("SELECT description_document IS NULL FROM requests WHERE title='Plain compatible' AND project_id=?",
                Boolean.class, project)).isTrue();
    }

    @Test
    void adminStoresDocumentAndServerDerivedPlainTextForRootTaskAndSubtask() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Rich description admin", "ACTIVE");
        long parent = task(project, employee, null, "Parent");
        long subtask = task(project, employee, parent, "Child");
        Session admin = login("sara.saad@example.com");

        updateAdmin(admin, project, parent, validDocument()).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Heading\n1. First\n2. Second"))
                .andExpect(jsonPath("$.descriptionDocument.type").value("doc"));
        updateAdmin(admin, project, subtask, validDocument()).andExpect(status().isOk());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT description FROM requests WHERE request_id=?", String.class, parent))
                .isEqualTo("Heading\n1. First\n2. Second");
        assertThat(jdbc.queryForObject("SELECT description_document IS NOT NULL FROM requests WHERE request_id=?",
                Boolean.class, subtask)).isTrue();
        mvc.perform(get("/api/projects/{p}/tasks/{t}", project, parent).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descriptionDocument.type").value("doc"));
    }

    @Test
    void employeeMemberCanUpdateButNonMemberArchivedAndCsrfRequestsAreRejected() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Rich description employee", "ACTIVE");
        long task = task(project, employee, null, "Task");
        Session member = login("nora.ahmed@example.com");

        updateAdmin(member, project, task, validDocument()).andExpect(status().isForbidden());
        updateEmployee(member, project, task, validDocument()).andExpect(status().isNotFound());
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee);
        updateEmployee(member, project, task, validDocument()).andExpect(status().isOk());
        mvc.perform(put("/api/projects/mine/{p}/tasks/{t}/description-document", project, task)
                        .session(member.session()).contentType("application/json").content(validDocument()))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        entityManager.clear();
        updateEmployee(member, project, task, validDocument()).andExpect(status().isConflict());
    }

    @Test
    void projectBoundaryAndMalformedPayloadAreRejected() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Rich boundary A", "ACTIVE");
        long otherProject = project("Rich boundary B", "ACTIVE");
        long otherTask = task(otherProject, employee, null, "Other Task");
        Session admin = login("sara.saad@example.com");

        updateAdmin(admin, project, otherTask, validDocument()).andExpect(status().isNotFound());
        updateAdmin(admin, project, Long.MAX_VALUE, validDocument()).andExpect(status().isNotFound());
        updateAdmin(admin, project, task(project, employee, null, "Malformed"), "{not-json")
                .andExpect(status().isBadRequest());
    }

    @Test
    void guestTaskAndUnsupportedContentAreRejectedWithoutChangingPlainText() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Rich description rejection", "ACTIVE");
        long guest = guestTask(project, "Guest plain text");
        long internal = task(project, employee, null, "Internal");
        Session admin = login("sara.saad@example.com");

        updateAdmin(admin, project, guest, validDocument()).andExpect(status().isForbidden());
        updateAdmin(admin, project, internal,
                """
                {"document":{"type":"doc","content":[{"type":"image","attrs":{"src":"https://example.com/x.png"}}]}}
                """)
                .andExpect(status().isBadRequest());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT description FROM requests WHERE request_id=?", String.class, guest))
                .isEqualTo("Guest plain text");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id IN (?,?) AND description_document IS NULL",
                Long.class, guest, internal)).isEqualTo(2);
    }

    @Test
    void canonicalTiptapTableAndLinkPersistAndReload() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Rich table and link", "ACTIVE");
        long task = task(project, employee, null, "Table and link");
        Session admin = login("sara.saad@example.com");

        updateAdmin(admin, project, task, tableAndLinkDocument()).andExpect(status().isOk())
                .andExpect(jsonPath("$.descriptionDocument.content[0].content[0].marks[0].attrs.href")
                        .value("https://example.com/docs"))
                .andExpect(jsonPath("$.descriptionDocument.content[1].content[0].content[0].type")
                        .value("tableHeader"));
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/projects/{p}/tasks/{t}", project, task).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descriptionDocument.content[0].content[0].marks[0].type").value("link"))
                .andExpect(jsonPath("$.descriptionDocument.content[1].type").value("table"));
    }

    @Test
    void metadataOnlySubtaskUpdatePreservesRichDocumentAndPlainFallback() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Rich metadata preservation", "ACTIVE");
        long parent = task(project, employee, null, "Parent");
        long child = task(project, employee, parent, "Child");
        Session admin = login("sara.saad@example.com");
        updateAdmin(admin, project, child, validDocument()).andExpect(status().isOk());
        entityManager.flush();
        String storedDocument = jdbc.queryForObject(
                "SELECT description_document::text FROM requests WHERE request_id=?", String.class, child);
        long newWorkType = workTypeId(jdbc, project, "Bug");

        mvc.perform(put("/api/projects/{p}/tasks/{parent}/subtasks/{child}", project, parent, child)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"title\":\"Renamed child\",\"workTypeId\":" + newWorkType
                                + ",\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed child"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.description").value("Heading\n1. First\n2. Second"))
                .andExpect(jsonPath("$.descriptionDocument.type").value("doc"));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT description_document::text FROM requests WHERE request_id=?",
                String.class, child)).isEqualTo(storedDocument);
        assertThat(jdbc.queryForObject("SELECT project_work_type_id FROM requests WHERE request_id=?",
                Long.class, child)).isEqualTo(newWorkType);
    }

    @Test
    void employeeMetadataUpdateRequiresMembershipAndMutableMatchingProject() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Rich employee metadata", "ACTIVE");
        long otherProject = project("Rich employee metadata boundary", "ACTIVE");
        long parent = task(project, employee, null, "Parent");
        long child = task(project, employee, parent, "Child");
        Session admin = login("sara.saad@example.com");
        Session member = login("nora.ahmed@example.com");
        updateAdmin(admin, project, child, validDocument()).andExpect(status().isOk());
        long workType = workTypeId(jdbc, project, "Task");
        String body = "{\"title\":\"Employee rename\",\"workTypeId\":" + workType
                + ",\"priority\":\"LOW\"}";

        mvc.perform(put("/api/projects/mine/{p}/tasks/{parent}/subtasks/{child}", project, parent, child)
                        .session(member.session()).cookie(member.csrf()).header("X-XSRF-TOKEN", member.csrf().getValue())
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee);
        mvc.perform(put("/api/projects/mine/{p}/tasks/{parent}/subtasks/{child}", project, parent, child)
                        .session(member.session()).cookie(member.csrf()).header("X-XSRF-TOKEN", member.csrf().getValue())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descriptionDocument.type").value("doc"));
        mvc.perform(put("/api/projects/mine/{p}/tasks/{parent}/subtasks/{child}", otherProject, parent, child)
                        .session(member.session()).cookie(member.csrf()).header("X-XSRF-TOKEN", member.csrf().getValue())
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        entityManager.clear();
        mvc.perform(put("/api/projects/mine/{p}/tasks/{parent}/subtasks/{child}", project, parent, child)
                        .session(member.session()).cookie(member.csrf()).header("X-XSRF-TOKEN", member.csrf().getValue())
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void existingPlainSubtaskUpdateClearsStaleDocument() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Plain fallback", "ACTIVE");
        long parent = task(project, employee, null, "Parent");
        long child = task(project, employee, parent, "Child");
        Session admin = login("sara.saad@example.com");
        updateAdmin(admin, project, child, validDocument()).andExpect(status().isOk());

        long workType = workTypeId(jdbc, project, "Task");
        mvc.perform(put("/api/projects/{p}/tasks/{parent}/subtasks/{child}", project, parent, child)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"title\":\"Child\",\"description\":\"Plain replacement\",\"workTypeId\":"
                                + workType + ",\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Plain replacement"));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT description_document IS NULL FROM requests WHERE request_id=?",
                Boolean.class, child)).isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions updateAdmin(
            Session session, long project, long task, String body) throws Exception {
        return mvc.perform(put("/api/projects/{p}/tasks/{t}/description-document", project, task)
                .session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json").content(body));
    }

    private org.springframework.test.web.servlet.ResultActions updateEmployee(
            Session session, long project, long task, String body) throws Exception {
        return mvc.perform(put("/api/projects/mine/{p}/tasks/{t}/description-document", project, task)
                .session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json").content(body));
    }

    private String validDocument() {
        return """
                {"document":{"type":"doc","content":[
                {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Heading"}]},
                {"type":"orderedList","attrs":{"start":1,"type":null},"content":[
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"First"}]}]},
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Second"}]}]}
                ]}]}}
                """;
    }

    private String tableAndLinkDocument() {
        return """
                {"document":{"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"Documentation","marks":[
                    {"type":"link","attrs":{"href":"https://example.com/docs","target":"_blank","rel":"noopener noreferrer nofollow","class":null}}
                  ]}]},
                  {"type":"table","content":[{"type":"tableRow","content":[
                    {"type":"tableHeader","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Header"}]}]},
                    {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Value"}]}]}
                  ]}]}
                ]}}
                """;
    }

    private String richCreateInput(String title, long assignee, long workType, Long status) {
        String document = tableAndLinkDocument().trim();
        document = document.substring("{\"document\":".length(), document.length() - 1);
        return "{\"title\":\"" + title + "\",\"description\":\"Client fallback must be ignored\","
                + "\"descriptionDocument\":" + document + ",\"workTypeId\":" + workType
                + ",\"priority\":\"MEDIUM\",\"assignedToId\":" + assignee
                + (status == null ? "" : ",\"statusId\":" + status) + "}";
    }

    private long project(String name, String state) {
        return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id",
                Long.class, name, state);
    }

    private long task(long project, long creator, Long parent, String title) {
        long workType = workTypeId(jdbc, project, "Task");
        long status = statusId(project, "NEW");
        return jdbc.queryForObject("INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,created_by,project_id,assigned_to,parent_request_id) VALUES (?,'Plain',?,'MEDIUM',?,?,?,?,?) RETURNING request_id",
                Long.class, title, workType, status, creator, project, creator, parent);
    }

    private long guestTask(long project, String description) {
        long workType = workTypeId(jdbc, project, "Task");
        long status = statusId(project, "NEW");
        return jdbc.queryForObject("INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,guest_name,guest_email,project_id) VALUES ('Guest',?,?,'MEDIUM',?,'Guest','guest@example.com',?) RETURNING request_id",
                Long.class, description, workType, status, project);
    }

    private long statusId(long project, String key) {
        return jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?",
                Long.class, project, key);
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
