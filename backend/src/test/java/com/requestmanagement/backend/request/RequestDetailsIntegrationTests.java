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
class RequestDetailsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void detailsContainOnlyOwnVisiblePersistedData() throws Exception {
        Long id = requestId("Laptop Issue");
        jdbc.update("INSERT INTO status_history (request_id, old_status, new_status, changed_by, change_note, changed_at) VALUES (?, 'IN_PROGRESS', 'WAITING_USER', (SELECT user_id FROM users WHERE email='sara.saad@example.com'), 'Waiting for answer', CURRENT_TIMESTAMP + INTERVAL '1 day')", id);
        jdbc.update("INSERT INTO comments (request_id, user_id, comment_text, is_internal) VALUES (?, (SELECT user_id FROM users WHERE email='sara.saad@example.com'), 'Private note', true)", id);
        jdbc.update("INSERT INTO comments (request_id, user_id, comment_text, is_internal, created_at, updated_at) VALUES (?, (SELECT user_id FROM users WHERE email='nora.ahmed@example.com'), 'Later public note', false, CURRENT_TIMESTAMP + INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 day')", id);
        mvc.perform(get("/api/requests/{id}", id).session(login("nora.ahmed@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.requesterName").value("Nora Ahmed"))
                .andExpect(jsonPath("$.assignedToName").value("Nouf Khaled"))
                .andExpect(jsonPath("$.typeName").value("IT & Technical Support"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.description").isString())
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andExpect(jsonPath("$.timeline[0].newStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.timeline[0].changedByName").value("Sara Saad"))
                .andExpect(jsonPath("$.timeline[0].changeNote").value("Request reviewed and assigned."))
                .andExpect(jsonPath("$.timeline[1].newStatus").value("WAITING_USER"))
                .andExpect(jsonPath("$.comments.length()").value(3))
                .andExpect(jsonPath("$.comments[0].authorName").value("Sara Saad"))
                .andExpect(jsonPath("$.comments[0].authorRole").value("ADMIN"))
                .andExpect(jsonPath("$.comments[1].authorRole").value("REQUESTER"))
                .andExpect(jsonPath("$.comments[2].text").value("Later public note"))
                .andExpect(jsonPath("$.comments[0].internal").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get("/api/requests/{id}", requestId("System Access")).session(login("nora.ahmed@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.timeline.length()").value(0));
    }

    @Test
    void ownershipAndAuthorizationAreEnforced() throws Exception {
        Long id = requestId("Laptop Issue");
        Long otherId = addOtherRequest();
        MockHttpSession nora = login("nora.ahmed@example.com");
        mvc.perform(get("/api/requests/{id}", otherId).session(nora)).andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/{id}", 999999999L).session(nora)).andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/requests/{id}", id).session(login("sara.saad@example.com"))).andExpect(status().isForbidden());
    }

    private Long requestId(String title) {
        return jdbc.queryForObject("SELECT request_id FROM requests WHERE title = ? ORDER BY request_id LIMIT 1", Long.class, title);
    }

    private Long addOtherRequest() {
        return jdbc.queryForObject("""
                INSERT INTO requests (title, description, type_id, priority, status, created_by)
                VALUES ('Other request', 'Private', (SELECT type_id FROM request_types LIMIT 1), 'LOW', 'NEW',
                        (SELECT user_id FROM users WHERE email='sara.saad@example.com')) RETURNING request_id
                """, Long.class);
    }

    private MockHttpSession login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
