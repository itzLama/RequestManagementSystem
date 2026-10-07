package com.requestmanagement.backend.request;

import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectTaskCollaborationIntegrationTests {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired EntityManager entityManager;

    @Test
    void boardContainsOnlySelectedProjectTasksAndArchivedMemberCanView() throws Exception {
        long employee = userId("nora.ahmed@example.com"); long selected = project("Board selected", "ACTIVE"); long other = project("Board other", "ACTIVE"); member(selected, employee);
        long selectedRequest = request("Selected project request", selected, employee, "NEW"); request("Other project request", other, employee, "NEW"); request("General boundary", null, employee, "NEW");
        Session session = login("nora.ahmed@example.com");
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/board", selected).session(session.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(selectedRequest));
        mvc.perform(get("/api/requests/mine/board").session(session.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + selectedRequest + ")]").isEmpty());
        mvc.perform(get("/api/requests/assigned/board").session(session.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + selectedRequest + ")]").isEmpty());
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", selected); entityManager.clear();
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/board", selected).session(session.session())).andExpect(status().isOk());
    }

    @Test
    void activeMemberCreatesAssignedProjectTaskWithoutSpoofableIdentity() throws Exception {
        long employee = userId("nora.ahmed@example.com"); long assignee = createEmployee("Task Assignee", "task.assignee@example.com"); long project = project("Create project task", "ACTIVE"); member(project, employee); member(project, assignee); Session session = login("nora.ahmed@example.com");
        MvcResult result = mvc.perform(post("/api/projects/mine/{projectId}/tasks", project).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json")
                        .content("{\"title\":\" Project need \",\"description\":\" Project description \",\"workType\":\"BUG\",\"priority\":\"HIGH\",\"assignedToId\":" + assignee + ",\"createdBy\":1,\"projectId\":999,\"typeId\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("NEW")).andReturn();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        var row = jdbc.queryForMap("SELECT created_by,project_id,type_id,work_type,guest_name,guest_email,assigned_to,status FROM requests WHERE request_id=?", id);
        assertThat(((Number) row.get("created_by")).longValue()).isEqualTo(employee); assertThat(((Number) row.get("project_id")).longValue()).isEqualTo(project);
        assertThat(row.get("type_id")).isNull(); assertThat(row.get("work_type")).isEqualTo("BUG");
        assertThat(row.get("guest_name")).isNull(); assertThat(row.get("guest_email")).isNull(); assertThat(((Number) row.get("assigned_to")).longValue()).isEqualTo(assignee); assertThat(row.get("status")).isEqualTo("NEW");
        mvc.perform(post("/api/projects/mine/{projectId}/tasks", project).session(session.session()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void membershipAndProjectTaskPathAreAuthoritative() throws Exception {
        long nora = userId("nora.ahmed@example.com"); long active = project("Authoritative A", "ACTIVE"); long other = project("Authoritative B", "ACTIVE"); member(active, nora); long request = request("Mismatch", other, nora, "NEW"); Session session = login("nora.ahmed@example.com");
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{requestId}", active, request).session(session.session())).andExpect(status().isNotFound());
        member(other, nora); mvc.perform(get("/api/projects/mine/{projectId}/tasks/{requestId}", other, request).session(session.session())).andExpect(status().isOk());
        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", other, nora); entityManager.clear();
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{requestId}", other, request).session(session.session())).andExpect(status().isNotFound());
    }

    @Test
    void removedAndInactiveEmployeeCannotCreateCommentOrChangeStatus() throws Exception {
        long nora = userId("nora.ahmed@example.com"); long project = project("Revoked collaboration", "ACTIVE"); member(project, nora); long request = request("Revoked request", project, nora, "NEW"); Session session = login("nora.ahmed@example.com");
        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", project, nora); entityManager.clear();
        assertMutationAccess(project, request, session, 404);
        member(project, nora); jdbc.update("UPDATE users SET is_active=false WHERE user_id=?", nora); entityManager.clear();
        assertMutationAccess(project, request, session, 403);
    }

    @Test
    void commentsArePublicStatusNeutralAndStatusUsesSharedHistoryWorkflow() throws Exception {
        long nora = userId("nora.ahmed@example.com"); long project = project("Collaboration", "ACTIVE"); member(project, nora); long request = request("Collaboration request", project, nora, "COMPLETED"); Session session = login("nora.ahmed@example.com");
        mvc.perform(post("/api/projects/mine/{projectId}/tasks/{requestId}/comments", project, request).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json").content("{\"text\":\"Project comment\",\"internal\":true}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, request)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT is_internal FROM comments WHERE request_id=?", Boolean.class, request)).isFalse();
        mvc.perform(patch("/api/projects/mine/{projectId}/tasks/{requestId}/status", project, request).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json").content("{\"status\":\"NEW\",\"changeNote\":\"Restart project work\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW"));
        var history = jdbc.queryForMap("SELECT old_status,new_status,changed_by,change_note FROM status_history WHERE request_id=?", request);
        assertThat(history.get("old_status")).isEqualTo("COMPLETED"); assertThat(history.get("new_status")).isEqualTo("NEW"); assertThat(((Number) history.get("changed_by")).longValue()).isEqualTo(nora);
        mvc.perform(patch("/api/projects/mine/{projectId}/tasks/{requestId}/status", project, request).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json").content("{\"status\":\"NEW\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, request)).isEqualTo(1L);
    }

    @Test
    void archivedProjectIsFullyReadOnlyForEmployeeAndAdmin() throws Exception {
        long nora = userId("nora.ahmed@example.com"); long project = project("Archived collaboration", "ARCHIVED"); member(project, nora); long request = request("Archived request", project, nora, "NEW"); Session employee = login("nora.ahmed@example.com"); Session admin = login("sara.saad@example.com");
        mvc.perform(get("/api/projects/mine/{p}/tasks/{r}", project, request).session(employee.session())).andExpect(status().isOk());
        mvc.perform(post("/api/projects/mine/{p}/tasks", project).session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue()).contentType("application/json").content("{\"title\":\"No\",\"description\":\"No\",\"workType\":\"TASK\",\"priority\":\"LOW\",\"assignedToId\":" + nora + "}" )).andExpect(status().isConflict());
        mvc.perform(post("/api/projects/mine/{p}/tasks/{r}/comments", project, request).session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue()).contentType("application/json").content("{\"text\":\"No\"}" )).andExpect(status().isConflict());
        mvc.perform(patch("/api/projects/mine/{p}/tasks/{r}/status", project, request).session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue()).contentType("application/json").content("{\"status\":\"COMPLETED\"}" )).andExpect(status().isConflict());
        mvc.perform(get("/api/projects/{p}/tasks/{r}", project, request).session(admin.session())).andExpect(status().isOk()).andExpect(jsonPath("$.projectStatus").value("ARCHIVED"));
        mvc.perform(patch("/api/projects/{p}/tasks/{r}/status", project, request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"status\":\"COMPLETED\"}" )).andExpect(status().isConflict());
        mvc.perform(patch("/api/projects/{p}/tasks/{r}/assignee", project, request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"assignedToId\":" + nora + "}" )).andExpect(status().isConflict());
        for (String suffix : new String[]{"comments", "internal-notes"}) mvc.perform(post("/api/projects/{p}/tasks/{r}/" + suffix, project, request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"text\":\"No\"}" )).andExpect(status().isConflict());
    }

    @Test
    void adminUsesProjectScopedWorkspaceAndAssignsOnlyEligibleProjectMember() throws Exception {
        long nora = userId("nora.ahmed@example.com"); long nouf = userId("nouf.khaled@example.com"); long project = project("Admin context project", "ACTIVE"); member(project, nora); long request = request("Admin context request", project, nora, "NEW"); Session admin = login("sara.saad@example.com");
        mvc.perform(get("/api/projects/{p}/tasks/board", project).session(admin.session())).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(request));
        mvc.perform(get("/api/requests/admin/{id}", request).session(admin.session())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/requests/admin/{id}", request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"status\":\"IN_PROGRESS\",\"assignedToId\":null}" )).andExpect(status().isNotFound());
        mvc.perform(post("/api/requests/admin/{id}/comments", request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"text\":\"Blocked global comment\"}" )).andExpect(status().isNotFound());
        mvc.perform(post("/api/requests/admin/{id}/internal-notes", request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"text\":\"Blocked global note\"}" )).andExpect(status().isNotFound());
        mvc.perform(patch("/api/projects/{p}/tasks/{r}/status", project, request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"status\":\"IN_PROGRESS\",\"changeNote\":\"Admin started\"}" )).andExpect(status().isOk());
        mvc.perform(post("/api/projects/{p}/tasks/{r}/comments", project, request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"text\":\"Public\"}" )).andExpect(status().isCreated());
        mvc.perform(post("/api/projects/{p}/tasks/{r}/internal-notes", project, request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"text\":\"Internal\"}" )).andExpect(status().isCreated());
        mvc.perform(patch("/api/projects/{p}/tasks/{r}/assignee", project, request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"assignedToId\":" + nora + "}" )).andExpect(status().isOk());
        mvc.perform(patch("/api/projects/{p}/tasks/{r}/assignee", project, request).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"assignedToId\":" + nouf + "}" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/mine/{p}/tasks/{r}", project, request).session(login("nora.ahmed@example.com").session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments[?(@.text == 'Public')]").exists())
                .andExpect(jsonPath("$.internalNotes").doesNotExist());
    }

    @Test
    void membersEndpointShowsRetainedMembersWhileReassignmentUsesOnlyActiveProjectMembers() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long activeMember = createEmployee("Active Project Member", "active.project.member@example.com");
        long inactiveMember = createEmployee("Inactive Project Member", "inactive.project.member@example.com");
        long nonMember = createEmployee("Project Outsider", "project.outsider@example.com");
        long project = project("Assignment project", "ACTIVE");
        member(project, nora); member(project, activeMember); member(project, inactiveMember);
        jdbc.update("UPDATE users SET is_active=false WHERE user_id=?", inactiveMember);
        long task = request("Assignable task", project, nora, "NEW");
        Session session = login("nora.ahmed@example.com");

        mvc.perform(get("/api/projects/mine/{projectId}/members", project).session(session.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.employeeId == " + activeMember + ")]").exists())
                .andExpect(jsonPath("$[?(@.employeeId == " + inactiveMember + " && @.active == false)]").exists());
        mvc.perform(patch("/api/projects/mine/{projectId}/tasks/{taskId}/assignee", project, task)
                        .session(session.session()).cookie(session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json")
                        .content("{\"assignedToId\":" + activeMember + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedToId").value(activeMember));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, task))
                .isEqualTo(activeMember);
        for (long invalid : new long[]{inactiveMember, nonMember, userId("sara.saad@example.com")}) {
            mvc.perform(patch("/api/projects/mine/{projectId}/tasks/{taskId}/assignee", project, task)
                            .session(session.session()).cookie(session.csrf())
                            .header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json")
                            .content("{\"assignedToId\":" + invalid + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void adminCreatesTaskWithOwnIdentityAndProjectPathPreventsIdor() throws Exception {
        long adminId = userId("sara.saad@example.com");
        long nora = userId("nora.ahmed@example.com");
        long project = project("Admin task creation", "ACTIVE");
        long otherProject = project("Admin task other", "ACTIVE");
        member(project, nora);
        Session admin = login("sara.saad@example.com");

        MvcResult result = mvc.perform(post("/api/projects/{projectId}/tasks", project)
                        .session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"title\":\"Admin-created Task\",\"description\":\"Project work\",\"workType\":\"IMPROVEMENT\",\"priority\":\"HIGH\",\"assignedToId\":" + nora + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("NEW")).andReturn();
        long task = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
        var row = jdbc.queryForMap("SELECT created_by,project_id,type_id,work_type,assigned_to FROM requests WHERE request_id=?", task);
        assertThat(((Number) row.get("created_by")).longValue()).isEqualTo(adminId);
        assertThat(((Number) row.get("project_id")).longValue()).isEqualTo(project);
        assertThat(row.get("type_id")).isNull();
        assertThat(row.get("work_type")).isEqualTo("IMPROVEMENT");
        assertThat(((Number) row.get("assigned_to")).longValue()).isEqualTo(nora);

        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}", otherProject, task)
                        .session(admin.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/{projectId}/tasks/board", project)
                        .session(login("nora.ahmed@example.com").session())).andExpect(status().isForbidden());
    }

    private long project(String name, String status) { return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id", Long.class, name, status); }
    private void assertMutationAccess(long project, long request, Session session, int expectedStatus) throws Exception {
        mvc.perform(post("/api/projects/mine/{p}/tasks", project).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json").content("{\"title\":\"Blocked\",\"description\":\"Blocked\",\"workType\":\"TASK\",\"priority\":\"LOW\",\"assignedToId\":" + userId("nora.ahmed@example.com") + "}" )).andExpect(status().is(expectedStatus));
        mvc.perform(post("/api/projects/mine/{p}/tasks/{r}/comments", project, request).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json").content("{\"text\":\"Blocked\"}" )).andExpect(status().is(expectedStatus));
        mvc.perform(patch("/api/projects/mine/{p}/tasks/{r}/status", project, request).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json").content("{\"status\":\"COMPLETED\"}" )).andExpect(status().is(expectedStatus));
    }
    private void member(long project, long employee) { jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee); }
    private long createEmployee(String name, String email) { return jdbc.queryForObject("INSERT INTO users(full_name,email,password_hash,role,is_active) VALUES (?,?,(SELECT password_hash FROM users WHERE email='nora.ahmed@example.com'),'EMPLOYEE',true) RETURNING user_id", Long.class, name, email); }
    private long request(String title, Long project, long creator, String status) {
        if (project != null) return jdbc.queryForObject("INSERT INTO requests(title,description,type_id,work_type,priority,status,created_by,project_id) VALUES (?,'Project test',NULL,'TASK','MEDIUM',?,?,?) RETURNING request_id", Long.class, title, status, creator, project);
        return jdbc.queryForObject("INSERT INTO requests(title,description,type_id,work_type,priority,status,created_by,project_id) VALUES (?,'Project test',?,NULL,'MEDIUM',?,?,NULL) RETURNING request_id", Long.class, title, typeId(), status, creator);
    }
    private long typeId() { return jdbc.queryForObject("SELECT type_id FROM request_types WHERE is_active=true ORDER BY type_id LIMIT 1", Long.class); }
    private long userId(String email) { return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email); }
    private Session login(String email) throws Exception { Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN"); assertThat(csrf).isNotNull(); MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}")).andExpect(status().isOk()).andReturn(); return new Session((MockHttpSession) result.getRequest().getSession(false), csrf); }
    private record Session(MockHttpSession session, Cookie csrf) { }
}
