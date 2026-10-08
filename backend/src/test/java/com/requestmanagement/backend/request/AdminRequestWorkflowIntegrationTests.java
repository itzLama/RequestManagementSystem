package com.requestmanagement.backend.request;

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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminRequestWorkflowIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminDetailsAreSafeAndRequesterIsRejected() throws Exception {
        Long id = requestId();
        jdbc.update("INSERT INTO comments (request_id,user_id,comment_text,is_internal) VALUES (?,1,'Admin secret',true)", id);
        mvc.perform(get("/api/requests/admin/{id}", id).session(login("sara.saad@example.com").session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requesterEmail").isString())
                .andExpect(jsonPath("$.updatedAt").isString()).andExpect(jsonPath("$.internalNotes[0].text").value("Admin secret"));
        mvc.perform(get("/api/requests/admin/{id}", id).session(login("nora.ahmed@example.com").session()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/{id}", id).session(login("nora.ahmed@example.com").session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments[?(@.text == 'Admin secret')]").isEmpty());
    }

    @Test
    void statusAndAssignmentUpdateArePersistedWithAuthenticatedHistory() throws Exception {
        Long id = jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,created_by)
                VALUES ('Workflow test','Details',(SELECT type_id FROM request_types LIMIT 1),'MEDIUM','NEW',2)
                RETURNING request_id
                """, Long.class);
        Long nora = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
        Session admin = login("sara.saad@example.com");
        mvc.perform(patch("/api/requests/admin/{id}", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"status\":\"IN_PROGRESS\",\"assignedToId\":" + nora + ",\"changeNote\":\"Review started\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedToId").value(nora)).andExpect(jsonPath("$.timeline[0].changeNote").value("Review started"));
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, id)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, id)).isEqualTo(nora);
        assertThat(jdbc.queryForObject("SELECT old_status FROM status_history WHERE request_id=?", String.class, id)).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("SELECT new_status FROM status_history WHERE request_id=?", String.class, id)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT changed_by FROM status_history WHERE request_id=?", Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT changed_at IS NOT NULL FROM status_history WHERE request_id=?", Boolean.class, id)).isTrue();

        mvc.perform(patch("/api/requests/admin/{id}", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"status\":\"IN_PROGRESS\",\"assignedToId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedToId").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void adminCanCompleteOrRejectRequestsWithUpdatedTimestampsAndHistory() throws Exception {
        Session admin = login("sara.saad@example.com");
        for (String terminalStatus : new String[] { "COMPLETED", "REJECTED" }) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO requests(title,description,type_id,priority,status,created_by,updated_at)
                    VALUES (?, 'Terminal workflow test', (SELECT type_id FROM request_types LIMIT 1),
                            'MEDIUM', 'NEW', 2, CURRENT_TIMESTAMP - INTERVAL '1 day')
                    RETURNING request_id
                    """, Long.class, "Workflow " + terminalStatus);
            LocalDateTime previousUpdatedAt = jdbc.queryForObject(
                    "SELECT updated_at FROM requests WHERE request_id=?", LocalDateTime.class, id);

            mvc.perform(patch("/api/requests/admin/{id}", id).session(admin.session()).cookie(admin.csrf())
                            .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                            .content("{\"status\":\"" + terminalStatus + "\",\"assignedToId\":null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(terminalStatus));

            assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, id))
                    .isEqualTo(terminalStatus);
            assertThat(jdbc.queryForObject("SELECT updated_at FROM requests WHERE request_id=?", LocalDateTime.class, id))
                    .isAfter(previousUpdatedAt);
            assertThat(jdbc.queryForObject("SELECT old_status FROM status_history WHERE request_id=?", String.class, id))
                    .isEqualTo("NEW");
            assertThat(jdbc.queryForObject("SELECT new_status FROM status_history WHERE request_id=?", String.class, id))
                    .isEqualTo(terminalStatus);
        }
    }

    @Test
    void adminCanExplicitlyReopenCompletedAndRejectedRequests() throws Exception {
        Session admin = login("sara.saad@example.com");
        for (String[] transition : new String[][] {
                { "COMPLETED", "IN_PROGRESS" },
                { "REJECTED", "NEW" },
                { "WAITING_USER", "COMPLETED" },
                { "COMPLETED", "NEW" }
        }) {
            String oldStatus = transition[0];
            String newStatus = transition[1];
            Long id = jdbc.queryForObject("""
                    INSERT INTO requests(title,description,type_id,priority,status,created_by)
                    VALUES (?, 'Terminal reopen test', (SELECT type_id FROM request_types LIMIT 1),
                            'MEDIUM', ?, 2)
                    RETURNING request_id
                    """, Long.class, "Free transition " + oldStatus + " to " + newStatus, oldStatus);

            mvc.perform(patch("/api/requests/admin/{id}", id).session(admin.session()).cookie(admin.csrf())
                            .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                            .content("{\"status\":\"" + newStatus + "\",\"assignedToId\":null,\"changeNote\":\"Explicit transition\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(newStatus));

            assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, id))
                    .isEqualTo(newStatus);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, id))
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT old_status FROM status_history WHERE request_id=?", String.class, id))
                    .isEqualTo(oldStatus);
            assertThat(jdbc.queryForObject("SELECT new_status FROM status_history WHERE request_id=?", String.class, id))
                    .isEqualTo(newStatus);
            assertThat(jdbc.queryForObject("SELECT change_note FROM status_history WHERE request_id=?", String.class, id))
                    .isEqualTo("Explicit transition");
        }
    }

    @Test
    void onlyActiveEmployeesAreAssignableAndInvalidAdminInactiveAndEmployeeActionsAreRejected() throws Exception {
        Session admin = login("sara.saad@example.com");
        mvc.perform(get("/api/requests/admin/assignees").session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.fullName == 'Nouf Khaled')]").isEmpty())
                .andExpect(jsonPath("$[?(@.fullName == 'Sara Saad')]").isEmpty())
                .andExpect(jsonPath("$[?(@.fullName == 'Nora Ahmed')]").exists())
                .andExpect(jsonPath("$[?(@.fullName == 'Reem Khalid')]").exists());
        mvc.perform(get("/api/requests/admin/assignees").session(login("nora.ahmed@example.com").session()))
                .andExpect(status().isForbidden());
        Long id = requestId();
        Long nora = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
        String currentStatus = jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, id);
        mvc.perform(patch("/api/requests/admin/{id}", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"status\":\"" + currentStatus + "\",\"assignedToId\":" + nora + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedToId").value(nora));
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, id)).isEqualTo(nora);

        Long adminId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='sara.saad@example.com'", Long.class);
        mvc.perform(patch("/api/requests/admin/{id}", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"status\":\"" + currentStatus + "\",\"assignedToId\":" + adminId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Assignee must be an active Employee."));
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, id)).isEqualTo(nora);

        Long guestRequest = jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,guest_name,guest_email)
                VALUES ('Guest assignee restriction','Details',(SELECT type_id FROM request_types LIMIT 1),
                        'MEDIUM','NEW','External Guest','external.guest@example.com')
                RETURNING request_id
                """, Long.class);
        mvc.perform(patch("/api/requests/admin/{id}", guestRequest).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"status\":\"NEW\",\"assignedToId\":" + adminId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Assignee must be an active Employee."));
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, guestRequest)).isNull();

        Long inactive = jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password_hash,role,is_active)
                VALUES ('Inactive Employee','inactive.employee@example.com',
                        (SELECT password_hash FROM users WHERE email='nora.ahmed@example.com'),'EMPLOYEE',false)
                RETURNING user_id
                """, Long.class);
        String originalStatus = jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, id);
        Long histories = jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, id);
        for (Long invalidId : new Long[] { 999999999L, inactive }) {
            mvc.perform(patch("/api/requests/admin/{id}", id).session(admin.session()).cookie(admin.csrf())
                    .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                            .content("{\"status\":\"COMPLETED\",\"assignedToId\":" + invalidId + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isString());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, id)).isEqualTo(originalStatus);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, id)).isEqualTo(histories);
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, id)).isEqualTo(nora);

        Session requester = login("nora.ahmed@example.com");
        mvc.perform(patch("/api/requests/admin/{id}", id).session(requester.session()).cookie(requester.csrf())
                        .header("X-XSRF-TOKEN", requester.csrf().getValue()).contentType("application/json")
                        .content("{\"status\":\"" + currentStatus + "\",\"assignedToId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicCommentsAndInternalNotesUseAdminIdentityAndRequesterVisibilityRules() throws Exception {
        Long id = requestId(); Session admin = login("sara.saad@example.com");
        mvc.perform(post("/api/requests/admin/{id}/comments", id).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"text\":\"  Public admin reply  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("Public admin reply")).andExpect(jsonPath("$.authorRole").value("ADMIN"));
        mvc.perform(post("/api/requests/admin/{id}/internal-notes", id).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"text\":\"  Private admin note  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("Private admin note"));
        assertThat(jdbc.queryForObject("SELECT user_id FROM comments WHERE comment_text='Public admin reply'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT is_internal FROM comments WHERE comment_text='Private admin note'", Boolean.class)).isTrue();
        mvc.perform(get("/api/requests/{id}", id).session(login("nora.ahmed@example.com").session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments[?(@.text == 'Public admin reply')]").exists())
                .andExpect(jsonPath("$.comments[?(@.text == 'Private admin note')]").isEmpty());
        Session requester = login("nora.ahmed@example.com");
        mvc.perform(post("/api/requests/admin/{id}/internal-notes", id).session(requester.session()).cookie(requester.csrf()).header("X-XSRF-TOKEN", requester.csrf().getValue()).contentType("application/json").content("{\"text\":\"No\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedWorkflowInputUnknownRequestsAndUnauthenticatedAssigneesAreHandledConsistently() throws Exception {
        Session admin = login("sara.saad@example.com");
        Long id = requestId();

        for (String body : new String[] {
                "{}",
                "{\"status\":\"NOT_A_STATUS\"}",
                "{\"status\":\"NEW\",\"assignedToId\":\"not-a-number\"}"
        }) {
            mvc.perform(patch("/api/requests/admin/{id}", id).session(admin.session()).cookie(admin.csrf())
                            .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isString());
        }

        mvc.perform(get("/api/requests/admin/not-a-number").session(admin.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'id'."));
        mvc.perform(get("/api/requests/admin/{id}", 999999999L).session(admin.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Request not found."));
        mvc.perform(get("/api/requests/admin/assignees"))
                .andExpect(status().isUnauthorized());
    }

    private Long requestId() { return jdbc.queryForObject("SELECT request_id FROM requests WHERE title='Laptop Issue' ORDER BY request_id LIMIT 1", Long.class); }
    private Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN"); assertThat(csrf).isNotNull();
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }
    private record Session(MockHttpSession session, Cookie csrf) { }
}
