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
                .andExpect(jsonPath("$.authorRole").value("REQUESTER"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.internal").doesNotExist()).andReturn();
        Long commentId = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT is_internal FROM comments WHERE comment_id=?", Boolean.class, commentId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT user_id FROM comments WHERE comment_id=?", Long.class, commentId))
                .isEqualTo(jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class));
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
                    .andExpect(status().isBadRequest());
        }
        for (Long target : new Long[] { 999999999L, addOtherRequest() }) {
            mvc.perform(post("/api/requests/{id}/comments", target).session(nora.session()).cookie(nora.csrf())
                            .header("X-XSRF-TOKEN", nora.csrf().getValue()).contentType("application/json").content("{\"text\":\"Hello\"}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/requests/{id}/comments", id).contentType("application/json").content("{\"text\":\"Hello\"}"))
                .andExpect(status().isForbidden());
        Session admin = login("sara.saad@example.com");
        mvc.perform(post("/api/requests/{id}/comments", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content("{\"text\":\"Hello\"}"))
                .andExpect(status().isForbidden());
    }

    private Long requestId(String title) {
        return jdbc.queryForObject("SELECT request_id FROM requests WHERE title=? ORDER BY request_id LIMIT 1", Long.class, title);
    }

    private Long addOtherRequest() {
        return jdbc.queryForObject("""
                INSERT INTO requests (title, description, type_id, priority, status, created_by)
                VALUES ('Another request', 'Private', (SELECT type_id FROM request_types LIMIT 1), 'LOW', 'NEW',
                        (SELECT user_id FROM users WHERE email='sara.saad@example.com')) RETURNING request_id
                """, Long.class);
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
