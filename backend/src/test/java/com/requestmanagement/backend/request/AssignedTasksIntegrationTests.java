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
class AssignedTasksIntegrationTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void listsOnlyCurrentEmployeesAssignedGeneralTasksWithPaginationAndBoardOrdering() throws Exception {
        Long viewer = createEmployee("Assigned Viewer", "assigned.viewer@example.com");
        Long other = createEmployee("Other Employee", "other.assigned@example.com");
        Long older = addRequest("Assigned older", "NEW", other, viewer, null, "2026-01-01 09:00:00");
        Long newer = addRequest("Assigned newer", "IN_PROGRESS", other, viewer, null, "2026-01-02 09:00:00");
        addRequest("Other employee task", "NEW", viewer, other, null, "2026-01-03 09:00:00");
        Long projectId = addProject();
        addRequest("Assigned project request", "NEW", other, viewer, projectId, "2026-01-04 09:00:00");
        Session session = login("assigned.viewer@example.com");

        mvc.perform(get("/api/requests/assigned?page=0&size=1").session(session.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(newer))
                .andExpect(jsonPath("$.content[0].assignedToId").value(viewer))
                .andExpect(jsonPath("$.content[0].assignedToName").value("Assigned Viewer"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/requests/assigned?page=1&size=1").session(session.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(older));
        mvc.perform(get("/api/requests/assigned/board").session(session.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer))
                .andExpect(jsonPath("$[0].assignedToId").value(viewer))
                .andExpect(jsonPath("$[0].assignedToName").value("Assigned Viewer"))
                .andExpect(jsonPath("$[1].id").value(older))
                .andExpect(jsonPath("$[?(@.title == 'Assigned project request')]").isEmpty());
    }

    @Test
    void assignedEmployeeViewsCommentsAndExplicitlyChangesStatusWithOneHistoryEntry() throws Exception {
        Long nora = userId("nora.ahmed@example.com");
        Long creator = createEmployee("Task Creator", "task.creator@example.com");
        Long requestId = addRequest("Assigned workflow", "COMPLETED", creator, nora, null, null);
        Session session = login("nora.ahmed@example.com");

        mvc.perform(get("/api/requests/assigned/{id}", requestId).session(session.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(requestId));
        mvc.perform(post("/api/requests/assigned/{id}/comments", requestId)
                        .session(session.session()).cookie(session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType("application/json").content("{\"text\":\"Completed task comment\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, requestId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, requestId))
                .isZero();

        mvc.perform(patch("/api/requests/assigned/{id}/status", requestId)
                        .session(session.session()).cookie(session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"status\":\"NEW\",\"changeNote\":\"Needs more work\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.timeline[0].oldStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.timeline[0].newStatus").value("NEW"));
        var history = jdbc.queryForMap("SELECT old_status,new_status,changed_by,change_note,changed_at FROM status_history WHERE request_id=?", requestId);
        assertThat(history.get("old_status")).isEqualTo("COMPLETED");
        assertThat(history.get("new_status")).isEqualTo("NEW");
        assertThat(((Number) history.get("changed_by")).longValue()).isEqualTo(nora);
        assertThat(history.get("change_note")).isEqualTo("Needs more work");
        assertThat(history.get("changed_at")).isNotNull();

        mvc.perform(patch("/api/requests/assigned/{id}/status", requestId)
                        .session(session.session()).cookie(session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType("application/json").content("{\"status\":\"NEW\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, requestId))
                .isEqualTo(1L);
    }

    @Test
    void creatorWithoutAssignmentCannotChangeStatusAndUnrelatedEmployeeCannotAccess() throws Exception {
        Long nora = userId("nora.ahmed@example.com");
        Long assignee = createEmployee("Assigned Worker", "assigned.worker@example.com");
        Long requestId = addRequest("Creator is not assignee", "NEW", nora, assignee, null, null);
        Session creator = login("nora.ahmed@example.com");

        mvc.perform(get("/api/requests/{id}", requestId).session(creator.session()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/requests/assigned/{id}", requestId).session(creator.session()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/requests/assigned/{id}/comments", requestId)
                        .session(creator.session()).cookie(creator.csrf())
                        .header("X-XSRF-TOKEN", creator.csrf().getValue())
                        .contentType("application/json").content("{\"text\":\"Not allowed\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/requests/assigned/{id}/status", requestId)
                        .session(creator.session()).cookie(creator.csrf())
                        .header("X-XSRF-TOKEN", creator.csrf().getValue())
                        .contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reassignmentImmediatelyRemovesFormerAssigneeAccess() throws Exception {
        Long nora = userId("nora.ahmed@example.com");
        Long replacement = createEmployee("Replacement", "replacement@example.com");
        Long requestId = addRequest("Reassigned task", "NEW", nora, nora, null, null);
        Session noraSession = login("nora.ahmed@example.com");
        mvc.perform(get("/api/requests/assigned/{id}", requestId).session(noraSession.session()))
                .andExpect(status().isOk());

        jdbc.update("UPDATE requests SET assigned_to=? WHERE request_id=?", replacement, requestId);
        entityManager.clear();

        mvc.perform(get("/api/requests/assigned/{id}", requestId).session(noraSession.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/assigned/{id}", requestId)
                        .session(login("replacement@example.com").session()))
                .andExpect(status().isOk());
    }

    @Test
    void creatorAndAssigneeCanBeSameWithoutMergingMyRequestsAndAssignedTasks() throws Exception {
        Long nora = userId("nora.ahmed@example.com");
        Long requestId = addRequest("Owned assigned request", "NEW", nora, nora, null, null);
        Session session = login("nora.ahmed@example.com");
        mvc.perform(get("/api/requests/mine/board").session(session.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + requestId + ")]").exists());
        mvc.perform(get("/api/requests/assigned/board").session(session.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + requestId + ")]").exists());
    }

    @Test
    void assignedEndpointsRejectProjectRequestsInactiveEmployeesAdminAndMissingCsrf() throws Exception {
        Long nora = userId("nora.ahmed@example.com");
        Long requestId = addRequest("Project assigned", "NEW", nora, nora, addProject(), null);
        Session employee = login("nora.ahmed@example.com");
        for (String endpoint : new String[] {
                "/api/requests/assigned/" + requestId,
                "/api/requests/assigned/" + requestId + "/comments"
        }) {
            var request = endpoint.endsWith("comments") ? post(endpoint) : get(endpoint);
            request.session(employee.session());
            if (endpoint.endsWith("comments")) request.cookie(employee.csrf())
                    .header("X-XSRF-TOKEN", employee.csrf().getValue())
                    .contentType("application/json").content("{\"text\":\"No\"}");
            mvc.perform(request).andExpect(status().isNotFound());
        }
        mvc.perform(patch("/api/requests/assigned/{id}/status", requestId)
                        .session(employee.session()).cookie(employee.csrf())
                        .header("X-XSRF-TOKEN", employee.csrf().getValue())
                        .contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/requests/assigned/{id}/status", requestId)
                        .session(employee.session()).contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/assigned").session(login("sara.saad@example.com").session()))
                .andExpect(status().isForbidden());

        jdbc.update("UPDATE users SET is_active=false WHERE user_id=?", nora);
        entityManager.clear();
        mvc.perform(get("/api/requests/assigned").session(employee.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotUseAdminAssignmentOrInternalNoteEndpoints() throws Exception {
        Long nora = userId("nora.ahmed@example.com");
        Long requestId = addRequest("Protected admin actions", "NEW", nora, nora, null, null);
        Session employee = login("nora.ahmed@example.com");
        mvc.perform(patch("/api/requests/admin/{id}", requestId)
                        .session(employee.session()).cookie(employee.csrf())
                        .header("X-XSRF-TOKEN", employee.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"status\":\"NEW\",\"assignedToId\":" + nora + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/admin/{id}/internal-notes", requestId)
                        .session(employee.session()).cookie(employee.csrf())
                        .header("X-XSRF-TOKEN", employee.csrf().getValue())
                        .contentType("application/json").content("{\"text\":\"Private\"}"))
                .andExpect(status().isForbidden());
    }

    private Long addRequest(String title, String status, Long creator, Long assignee,
                            Long projectId, String createdAt) {
        if (projectId != null) {
            return jdbc.queryForObject("""
                    INSERT INTO requests(title,description,type_id,work_type,priority,status,created_by,assigned_to,project_id,created_at,updated_at)
                    VALUES (?, 'Assigned task test', NULL, 'TASK', 'MEDIUM', ?, ?, ?, ?,
                            COALESCE(?::timestamp, CURRENT_TIMESTAMP), COALESCE(?::timestamp, CURRENT_TIMESTAMP))
                    RETURNING request_id
                    """, Long.class, title, status, creator, assignee, projectId, createdAt, createdAt);
        }
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,created_by,assigned_to,project_id,created_at,updated_at)
                VALUES (?, 'Assigned task test', (SELECT type_id FROM request_types WHERE is_active=true ORDER BY type_id LIMIT 1),
                        'MEDIUM', ?, ?, ?, ?, COALESCE(?::timestamp, CURRENT_TIMESTAMP), COALESCE(?::timestamp, CURRENT_TIMESTAMP))
                RETURNING request_id
                """, Long.class, title, status, creator, assignee, projectId, createdAt, createdAt);
    }

    private Long createEmployee(String name, String email) {
        return jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password_hash,role,is_active)
                VALUES (?, ?, (SELECT password_hash FROM users WHERE email='nora.ahmed@example.com'), 'EMPLOYEE', true)
                RETURNING user_id
                """, Long.class, name, email);
    }

    private Long addProject() {
        return jdbc.queryForObject("""
                INSERT INTO projects(project_name,status) VALUES ('Assigned boundary project','ACTIVE')
                RETURNING project_id
                """, Long.class);
    }

    private Long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    private Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
