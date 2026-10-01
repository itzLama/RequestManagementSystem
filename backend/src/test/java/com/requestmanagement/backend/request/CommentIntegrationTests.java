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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void requesterCommentIsPersistedAsVisibleAndPrincipalAuthored() throws Exception {
        Long id = requestId("Laptop Issue");
        Session session = login("nora.ahmed@example.com");
        MvcResult result = mvc.perform(post("/api/requests/{id}/comments", id)
                        .session(session.session()).cookie(session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType("application/json").content("{\"text\":\"  Hello there  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Hello there"))
                .andExpect(jsonPath("$.authorName").value("Nora Ahmed"))
                .andExpect(jsonPath("$.authorRole").value("EMPLOYEE"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.internal").doesNotExist()).andReturn();
        Long commentId = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT is_internal FROM comments WHERE comment_id=?", Boolean.class, commentId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT user_id FROM comments WHERE comment_id=?", Long.class, commentId))
                .isEqualTo(jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class));
    }

    @Test
    void requesterCommentReopensCompletedAndRejectedRequestsWithPersistedHistory() throws Exception {
        Session requester = login("nora.ahmed@example.com");
        Long requesterId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
        Long assigneeId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nouf.khaled@example.com'", Long.class);

        for (String previousStatus : new String[] { "COMPLETED", "REJECTED" }) {
            Long id = addRequest("Requester " + previousStatus, previousStatus, requesterId, assigneeId);
            postRequesterComment(id, requester, "The issue is still happening");

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE request_id=? AND comment_text='The issue is still happening'", Long.class, id)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, id)).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, id)).isEqualTo(assigneeId);
            assertThat(jdbc.queryForObject("SELECT old_status FROM status_history WHERE request_id=?", String.class, id)).isEqualTo(previousStatus);
            assertThat(jdbc.queryForObject("SELECT new_status FROM status_history WHERE request_id=?", String.class, id)).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("SELECT changed_by FROM status_history WHERE request_id=?", Long.class, id)).isEqualTo(requesterId);
            assertThat(jdbc.queryForObject("SELECT change_note FROM status_history WHERE request_id=?", String.class, id)).isEqualTo("Reopened after requester comment");
            assertThat(jdbc.queryForObject("SELECT changed_at IS NOT NULL FROM status_history WHERE request_id=?", Boolean.class, id)).isTrue();
        }
    }

    @Test
    void requesterCommentPreservesNewInProgressAndWaitingUserWithoutHistory() throws Exception {
        Session requester = login("nora.ahmed@example.com");
        Long requesterId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);

        for (String statusValue : new String[] { "NEW", "IN_PROGRESS", "WAITING_USER" }) {
            Long id = addRequest("Unchanged " + statusValue, statusValue, requesterId, null);
            postRequesterComment(id, requester, "Status should stay unchanged");

            assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, id)).isEqualTo(statusValue);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE request_id=?", Long.class, id)).isEqualTo(1L);
        }
    }

    @Test
    void adminPublicCommentsAndInternalNotesDoNotReopenClosedRequests() throws Exception {
        Session admin = login("sara.saad@example.com");
        Long requesterId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);

        for (String statusValue : new String[] { "COMPLETED", "REJECTED" }) {
            Long publicRequest = addRequest("Admin public " + statusValue, statusValue, requesterId, null);
            postAdminEntry(publicRequest, admin, "comments", "Administrative reply");
            assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, publicRequest)).isEqualTo(statusValue);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, publicRequest)).isZero();

            Long internalRequest = addRequest("Admin internal " + statusValue, statusValue, requesterId, null);
            postAdminEntry(internalRequest, admin, "internal-notes", "Administrative note");
            assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, internalRequest)).isEqualTo(statusValue);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, internalRequest)).isZero();
        }
    }

    @Test
    void validationOwnershipAuthenticationAndCsrfAreEnforced() throws Exception {
        Long id = requestId("Laptop Issue");
        Session nora = login("nora.ahmed@example.com");
        mvc.perform(post("/api/requests/{id}/comments", id).session(nora.session()).contentType("application/json").content("{\"text\":\"Hello\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/{id}/comments", id).session(nora.session()).cookie(nora.csrf()).header("X-XSRF-TOKEN", "invalid")
                        .contentType("application/json").content("{\"text\":\"Hello\"}"))
                .andExpect(status().isForbidden());
        for (String body : new String[] { "{\"text\":\"   \"}", "{\"text\":\"\"}" }) {
            mvc.perform(post("/api/requests/{id}/comments", id).session(nora.session()).cookie(nora.csrf())
                            .header("X-XSRF-TOKEN", nora.csrf().getValue()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isString());
        }
        Long otherRequest = addOtherRequest();
        for (Long target : new Long[] { 999999999L, otherRequest }) {
            mvc.perform(post("/api/requests/{id}/comments", target).session(nora.session()).cookie(nora.csrf())
                            .header("X-XSRF-TOKEN", nora.csrf().getValue()).contentType("application/json").content("{\"text\":\"Hello\"}"))
                    .andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE request_id=?", String.class, otherRequest)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE request_id=?", Long.class, otherRequest)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, otherRequest)).isZero();
        mvc.perform(post("/api/requests/{id}/comments", id).contentType("application/json").content("{\"text\":\"Hello\"}"))
                .andExpect(status().isForbidden());
        Session admin = login("sara.saad@example.com");
        mvc.perform(post("/api/requests/{id}/comments", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"text\":\"Hello\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void oversizedRequesterAndAdminEntriesReturnConsistentValidationErrors() throws Exception {
        Long id = requestId("Laptop Issue");
        String body = "{\"text\":\"" + "a".repeat(10001) + "\"}";
        Session requester = login("nora.ahmed@example.com");
        mvc.perform(post("/api/requests/{id}/comments", id).session(requester.session())
                        .cookie(requester.csrf()).header("X-XSRF-TOKEN", requester.csrf().getValue())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Comment must be at most 10000 characters."));

        Session admin = login("sara.saad@example.com");
        for (String endpoint : new String[] { "comments", "internal-notes" }) {
            mvc.perform(post("/api/requests/admin/{id}/{endpoint}", id, endpoint).session(admin.session())
                            .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Comment must be at most 10000 characters."));
        }
    }

    private Long requestId(String title) {
        return jdbc.queryForObject("SELECT request_id FROM requests WHERE title=? ORDER BY request_id LIMIT 1", Long.class, title);
    }

    private Long addOtherRequest() {
        return jdbc.queryForObject("""
                INSERT INTO requests (title, description, type_id, priority, status, created_by)
                VALUES ('Another request', 'Private', (SELECT type_id FROM request_types LIMIT 1), 'LOW', 'COMPLETED',
                        (SELECT user_id FROM users WHERE email='sara.saad@example.com')) RETURNING request_id
                """, Long.class);
    }

    private Long addRequest(String title, String statusValue, Long creatorId, Long assigneeId) {
        return jdbc.queryForObject("""
                INSERT INTO requests (title, description, type_id, priority, status, created_by, assigned_to)
                VALUES (?, 'Test details', (SELECT type_id FROM request_types LIMIT 1), 'MEDIUM', ?, ?, ?)
                RETURNING request_id
                """, Long.class, title, statusValue, creatorId, assigneeId);
    }

    private void postRequesterComment(Long requestId, Session session, String text) throws Exception {
        mvc.perform(post("/api/requests/{id}/comments", requestId)
                        .session(session.session()).cookie(session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType("application/json").content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isCreated());
    }

    private void postAdminEntry(Long requestId, Session session, String endpoint, String text) throws Exception {
        mvc.perform(post("/api/requests/admin/{id}/{endpoint}", requestId, endpoint)
                        .session(session.session()).cookie(session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType("application/json").content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isCreated());
    }

    private Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
