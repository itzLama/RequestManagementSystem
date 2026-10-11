package com.requestmanagement.backend.guestcommunication;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestCommunicationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void adminGeneralCommunicationPersistsTrimmedAuditableHistoryWithoutChangingRequestData() throws Exception {
        long requestId = guestGeneralRequest("Admin guest communication", null);
        Session admin = login("sara.saad@example.com");
        var before = requestState(requestId);
        long commentsBefore = count("comments", requestId);
        long historyBefore = count("status_history", requestId);

        mvc.perform(post("/api/requests/admin/{id}/guest-communications", requestId)
                        .session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"message\":\"  Your request is being reviewed.  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.message").value("Your request is being reviewed."))
                .andExpect(jsonPath("$.senderName").value("Sara Saad"))
                .andExpect(jsonPath("$.senderRole").value("ADMIN"))
                .andExpect(jsonPath("$.sentAt").isString());

        mvc.perform(get("/api/requests/admin/{id}/guest-communications", requestId)
                        .session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("Your request is being reviewed."));

        assertThat(count("guest_communications", requestId)).isEqualTo(1);
        assertThat(count("comments", requestId)).isEqualTo(commentsBefore);
        assertThat(count("status_history", requestId)).isEqualTo(historyBefore);
        assertThat(requestState(requestId)).isEqualTo(before);
    }

    @Test
    void onlyCurrentAssignedActiveEmployeeCanAccessGeneralGuestCommunication() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long reem = userId("reem.khalid@example.com");
        long requestId = guestGeneralRequest("Assigned guest communication", nora);
        Session noraSession = login("nora.ahmed@example.com");

        sendAssigned(requestId, noraSession, "Nora response")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderName").value("Nora Ahmed"))
                .andExpect(jsonPath("$.senderRole").value("EMPLOYEE"));

        jdbc.update("UPDATE requests SET assigned_to=? WHERE request_id=?", reem, requestId);
        entityManager.clear();
        mvc.perform(get("/api/requests/assigned/{id}/guest-communications", requestId)
                        .session(noraSession.session()))
                .andExpect(status().isNotFound());

        Session reemSession = login("reem.khalid@example.com");
        mvc.perform(get("/api/requests/assigned/{id}/guest-communications", requestId)
                        .session(reemSession.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value("Nora response"));

        jdbc.update("UPDATE requests SET assigned_to=NULL WHERE request_id=?", requestId);
        entityManager.clear();
        sendAssigned(requestId, reemSession, "Should fail").andExpect(status().isNotFound());

        long creatorOnlyRequest = employeeGeneralRequest("Creator-only access", nora);
        mvc.perform(get("/api/requests/assigned/{id}/guest-communications", creatorOnlyRequest)
                        .session(noraSession.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminAndActiveProjectMemberCommunicateInChronologicalOrderWithoutTaskAssignment() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long projectId = project("Guest communication project", "ACTIVE");
        member(projectId, nora);
        long taskId = guestProjectTask("Project guest communication", projectId);
        Session admin = login("sara.saad@example.com");
        Session employee = login("nora.ahmed@example.com");

        sendAdminProject(projectId, taskId, admin, "Admin message")
                .andExpect(status().isCreated());
        sendEmployeeProject(projectId, taskId, employee, "Employee message")
                .andExpect(status().isCreated());

        jdbc.update("UPDATE guest_communications SET sent_at=TIMESTAMP '2026-01-01 10:00:00' WHERE request_id=?",
                taskId);
        entityManager.clear();

        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{taskId}/guest-communications",
                        projectId, taskId).session(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].message").value("Admin message"))
                .andExpect(jsonPath("$[0].senderName").value("Sara Saad"))
                .andExpect(jsonPath("$[1].message").value("Employee message"))
                .andExpect(jsonPath("$[1].senderName").value("Nora Ahmed"));

        assertThat(jdbc.queryForObject(
                "SELECT assigned_to FROM requests WHERE request_id=?", Long.class, taskId)).isNull();
    }

    @Test
    void invalidMessagesAndNonGuestRequestsAreRejected() throws Exception {
        Session admin = login("sara.saad@example.com");
        long guestRequest = guestGeneralRequest("Validation guest", null);

        sendAdminGeneral(guestRequest, admin, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Message is required."));
        sendAdminGeneral(guestRequest, admin, "a".repeat(10001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Message must be at most 10000 characters."));

        long normalRequest = employeeGeneralRequest("Normal request", userId("nora.ahmed@example.com"));
        sendAdminGeneral(normalRequest, admin, "Not allowed").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Guest Communication is available only for Guest requests."));
        assertThat(count("guest_communications", guestRequest)).isZero();
        assertThat(count("guest_communications", normalRequest)).isZero();

        long projectId = project("Scope boundary project", "ACTIVE");
        long projectTask = guestProjectTask("Project scope boundary", projectId);
        sendAdminGeneral(projectTask, admin, "Wrong scope").andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/guest-communications",
                        projectId, normalRequest).session(admin.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonmemberRemovedAndInactiveEmployeesCannotAccessProjectCommunication() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long projectId = project("Project communication access", "ACTIVE");
        member(projectId, nora);
        long taskId = guestProjectTask("Protected project guest", projectId);
        Session noraSession = login("nora.ahmed@example.com");
        Session reemSession = login("reem.khalid@example.com");

        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{taskId}/guest-communications",
                        projectId, taskId).session(reemSession.session()))
                .andExpect(status().isNotFound());

        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", projectId, nora);
        entityManager.clear();
        sendEmployeeProject(projectId, taskId, noraSession, "Removed member")
                .andExpect(status().isNotFound());

        member(projectId, nora);
        jdbc.update("UPDATE users SET is_active=false WHERE user_id=?", nora);
        entityManager.clear();
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{taskId}/guest-communications",
                        projectId, taskId).session(noraSession.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void archivedProjectHistoryRemainsReadableButPostingIsRejectedAndProjectPathIsAuthoritative() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long projectId = project("Archived communication project", "ACTIVE");
        long otherProjectId = project("Wrong communication project", "ACTIVE");
        member(projectId, nora);
        member(otherProjectId, nora);
        long taskId = guestProjectTask("Archived guest task", projectId);
        Session admin = login("sara.saad@example.com");
        Session employee = login("nora.ahmed@example.com");
        sendAdminProject(projectId, taskId, admin, "Persisted before archive")
                .andExpect(status().isCreated());

        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", projectId);
        entityManager.clear();
        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/guest-communications",
                        projectId, taskId).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{taskId}/guest-communications",
                        projectId, taskId).session(employee.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        sendAdminProject(projectId, taskId, admin, "Blocked admin")
                .andExpect(status().isConflict());
        sendEmployeeProject(projectId, taskId, employee, "Blocked employee")
                .andExpect(status().isConflict());

        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/guest-communications",
                        otherProjectId, taskId).session(admin.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{taskId}/guest-communications",
                        otherProjectId, taskId).session(employee.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousAndCsrfProtectedAccessAreRejected() throws Exception {
        long requestId = guestGeneralRequest("Security guest", userId("nora.ahmed@example.com"));
        mvc.perform(get("/api/requests/admin/{id}/guest-communications", requestId))
                .andExpect(status().isUnauthorized());

        Session admin = login("sara.saad@example.com");
        Session employee = login("nora.ahmed@example.com");
        mvc.perform(get("/api/requests/admin/{id}/guest-communications", requestId)
                        .session(employee.session()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/assigned/{id}/guest-communications", requestId)
                        .session(admin.session()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/admin/{id}/guest-communications", requestId)
                        .session(admin.session()).contentType("application/json")
                        .content("{\"message\":\"Missing CSRF\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/admin/{id}/guest-communications", requestId)
                        .session(admin.session()).cookie(new Cookie("XSRF-TOKEN", "cookie-token"))
                        .header("X-XSRF-TOKEN", "different-token")
                        .contentType("application/json").content("{\"message\":\"Invalid CSRF\"}"))
                .andExpect(status().isForbidden());
        assertThat(count("guest_communications", requestId)).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions sendAdminGeneral(
            long requestId, Session session, String message
    ) throws Exception {
        return mvc.perform(post("/api/requests/admin/{id}/guest-communications", requestId)
                .session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json").content(jsonMessage(message)));
    }

    private org.springframework.test.web.servlet.ResultActions sendAssigned(
            long requestId, Session session, String message
    ) throws Exception {
        return mvc.perform(post("/api/requests/assigned/{id}/guest-communications", requestId)
                .session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json").content(jsonMessage(message)));
    }

    private org.springframework.test.web.servlet.ResultActions sendAdminProject(
            long projectId, long taskId, Session session, String message
    ) throws Exception {
        return mvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/guest-communications",
                        projectId, taskId).session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json").content(jsonMessage(message)));
    }

    private org.springframework.test.web.servlet.ResultActions sendEmployeeProject(
            long projectId, long taskId, Session session, String message
    ) throws Exception {
        return mvc.perform(post("/api/projects/mine/{projectId}/tasks/{taskId}/guest-communications",
                        projectId, taskId).session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json").content(jsonMessage(message)));
    }

    private String jsonMessage(String message) {
        return "{\"message\":\"" + message + "\"}";
    }

    private long guestGeneralRequest(String title, Long assigneeId) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,guest_name,guest_email,assigned_to)
                VALUES (?,'Guest communication test',?,'MEDIUM','NEW','External Guest','guest@example.com',?)
                RETURNING request_id
                """, Long.class, title, activeTypeId(), assigneeId);
    }

    private long employeeGeneralRequest(String title, long creatorId) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,created_by)
                VALUES (?,'Employee request',?,'LOW','NEW',?) RETURNING request_id
                """, Long.class, title, activeTypeId(), creatorId);
    }

    private long guestProjectTask(String title, long projectId) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,work_type,priority,status,
                                     guest_name,guest_email,project_id)
                VALUES (?,'Guest project communication',NULL,'TASK','HIGH','NEW',
                        'Project Guest','project.guest@example.com',?)
                RETURNING request_id
                """, Long.class, title, projectId);
    }

    private long project(String name, String status) {
        return jdbc.queryForObject(
                "INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id",
                Long.class, name, status);
    }

    private void member(long projectId, long employeeId) {
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)",
                projectId, employeeId);
    }

    private long count(String table, long requestId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE request_id=?", Long.class, requestId);
    }

    private java.util.Map<String, Object> requestState(long requestId) {
        return jdbc.queryForMap(
                "SELECT status, assigned_to, updated_at FROM requests WHERE request_id=?", requestId);
    }

    private long activeTypeId() {
        return jdbc.queryForObject(
                "SELECT type_id FROM request_types WHERE is_active=true ORDER BY type_id LIMIT 1",
                Long.class);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    private Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
