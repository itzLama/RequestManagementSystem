package com.requestmanagement.backend.request;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestIdentityAndProjectIntegrationTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired RequestRepository requestRepository;

    @Test
    void existingAuthenticatedRequestsRemainGeneral() {
        var row = jdbc.queryForMap("""
                SELECT project_id, created_by, guest_name, guest_email
                FROM requests
                WHERE title = 'Laptop Issue'
                ORDER BY request_id
                LIMIT 1
                """);

        assertThat(row.get("project_id")).isNull();
        assertThat(row.get("created_by")).isNotNull();
        assertThat(row.get("guest_name")).isNull();
        assertThat(row.get("guest_email")).isNull();
        long requestId = jdbc.queryForObject("""
                SELECT request_id FROM requests
                WHERE title = 'Laptop Issue'
                ORDER BY request_id
                LIMIT 1
                """, Long.class);
        assertThat(requestRepository.findById(requestId).orElseThrow().isGeneralRequest()).isTrue();
    }

    @Test
    void requestCanReferenceProjectAndArchivePreservesAssociation() {
        long projectId = insertProject("Request project");
        long requestId = insertAuthenticatedRequest(projectId);

        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", projectId);

        assertThat(jdbc.queryForObject(
                "SELECT project_id FROM requests WHERE request_id=?", Long.class, requestId))
                .isEqualTo(projectId);
        Request request = requestRepository.findById(requestId).orElseThrow();
        assertThat(request.isProjectRequest()).isTrue();
        assertThat(request.getProject().getId()).isEqualTo(projectId);
    }

    @Test
    void nonexistentProjectIsRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertAuthenticatedRequest(Long.MAX_VALUE));
    }

    @Test
    void referencedProjectCannotBeDeleted() {
        long projectId = insertProject("Protected project");
        insertAuthenticatedRequest(projectId);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM projects WHERE project_id=?", projectId));
    }

    @Test
    void authenticatedAndGuestCreatorFormsAreAccepted() {
        long authenticatedId = insertAuthenticatedRequest(null);
        long guestId = insertGuestRequest("Guest Person", "guest@example.com");

        assertThat(jdbc.queryForObject(
                "SELECT created_by IS NOT NULL AND guest_name IS NULL AND guest_email IS NULL FROM requests WHERE request_id=?",
                Boolean.class, authenticatedId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT created_by IS NULL AND guest_name='Guest Person' AND guest_email='guest@example.com' FROM requests WHERE request_id=?",
                Boolean.class, guestId)).isTrue();
    }

    @Test
    void missingCreatorIdentityIsRejected() {
        assertCreatorConstraintViolation(null, null, null);
    }

    @Test
    void authenticatedAndGuestIdentityTogetherAreRejected() {
        assertCreatorConstraintViolation(employeeId(), "Guest Person", "guest@example.com");
    }

    @Test
    void guestNameWithoutEmailIsRejected() {
        assertCreatorConstraintViolation(null, "Guest Person", null);
    }

    @Test
    void guestEmailWithoutNameIsRejected() {
        assertCreatorConstraintViolation(null, null, "guest@example.com");
    }

    @Test
    void blankGuestIdentityIsRejected() {
        assertCreatorConstraintViolation(null, "   ", "  ");
    }

    @Test
    void adminResponsesResolveGuestIdentityWithoutAuthenticatedUser() throws Exception {
        long requestId = insertGuestRequest("External Guest", "external.guest@example.com");
        MockHttpSession admin = login("sara.saad@example.com");

        mvc.perform(get("/api/requests/admin")
                        .param("search", "Guest foundation request")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(requestId))
                .andExpect(jsonPath("$.content[0].requesterName").value("External Guest"));

        mvc.perform(get("/api/requests/admin/{id}", requestId).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requesterName").value("External Guest"))
                .andExpect(jsonPath("$.requesterEmail").value("external.guest@example.com"));
    }

    @Test
    void guestRequestIsExcludedFromEmployeeOwnedRequestsAndDashboardStillWorks() throws Exception {
        insertGuestRequest("Dashboard Guest", "dashboard.guest@example.com");

        mvc.perform(get("/api/requests/mine")
                        .session(login("nora.ahmed@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == 'Guest foundation request')]").isEmpty());

        mvc.perform(get("/api/dashboard")
                        .session(login("sara.saad@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").isNumber());
    }

    private void assertCreatorConstraintViolation(Long createdBy, String guestName, String guestEmail) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO requests(title, description, type_id, priority, status,
                                     created_by, guest_name, guest_email)
                VALUES ('Invalid identity', 'Constraint test', ?, 'LOW', 'NEW', ?, ?, ?)
                """, typeId(), createdBy, guestName, guestEmail));
    }

    private long insertAuthenticatedRequest(Long projectId) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title, description, type_id, priority, status, created_by, project_id)
                VALUES ('Authenticated foundation request', 'Foundation test', ?, 'LOW', 'NEW', ?, ?)
                RETURNING request_id
                """, Long.class, typeId(), employeeId(), projectId);
    }

    private long insertGuestRequest(String guestName, String guestEmail) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title, description, type_id, priority, status, guest_name, guest_email)
                VALUES ('Guest foundation request', 'Foundation test', ?, 'MEDIUM', 'NEW', ?, ?)
                RETURNING request_id
                """, Long.class, typeId(), guestName, guestEmail);
    }

    private long insertProject(String name) {
        return jdbc.queryForObject("""
                INSERT INTO projects(project_name, status)
                VALUES (?, 'ACTIVE')
                RETURNING project_id
                """, Long.class, name);
    }

    private Long employeeId() {
        return jdbc.queryForObject(
                "SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
    }

    private Long typeId() {
        return jdbc.queryForObject(
                "SELECT type_id FROM request_types WHERE is_active=true ORDER BY type_id LIMIT 1", Long.class);
    }

    private MockHttpSession login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
