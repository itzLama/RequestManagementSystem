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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyRequestsIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void requesterOnlySeesOwnRequestsAndSecondRequesterIsIsolated() throws Exception {
        Long secondId = addRequester("second.requester@example.com");
        addRequest(secondId, "Second user's request", LocalDateTime.now());
        Long noraCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM requests r JOIN users u ON r.created_by = u.user_id
                WHERE u.email = 'nora.ahmed@example.com'
                """, Long.class);

        mockMvc.perform(get("/api/requests/mine").session(login("nora.ahmed@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(noraCount))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].title").isString())
                .andExpect(jsonPath("$.content[0].typeName").isString())
                .andExpect(jsonPath("$.content[0].priority").isString())
                .andExpect(jsonPath("$.content[0].status").isString())
                .andExpect(jsonPath("$.content[0].createdAt").isString())
                .andExpect(jsonPath("$.content[0].description").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/requests/mine?userId=2&createdBy=2")
                        .session(login("second.requester@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Second user's request"));
    }

    @Test
    void pagesUseDatabasePaginationAndDeterministicNewestFirstOrder() throws Exception {
        Long requesterId = addRequester("paging.requester@example.com");
        LocalDateTime sameTime = LocalDateTime.now().plusDays(1);
        Long firstId = addRequest(requesterId, "Same time first", sameTime);
        Long secondId = addRequest(requesterId, "Same time second", sameTime);
        Long olderId = addRequest(requesterId, "Earlier", sameTime.minusMinutes(1));
        MockHttpSession session = login("paging.requester@example.com");

        mockMvc.perform(get("/api/requests/mine?page=0&size=2").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(secondId))
                .andExpect(jsonPath("$.content[1].id").value(firstId));

        mockMvc.perform(get("/api/requests/mine?page=1&size=2").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(olderId))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void emptyRequesterGetsEmptyPageAndDefaults() throws Exception {
        addRequester("empty.requester@example.com");
        mockMvc.perform(get("/api/requests/mine").session(login("empty.requester@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void unauthorizedAndInvalidPaginationAreRejected() throws Exception {
        mockMvc.perform(get("/api/requests/mine"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/requests/mine").session(login("sara.saad@example.com")))
                .andExpect(status().isForbidden());

        MockHttpSession session = login("nora.ahmed@example.com");
        for (String query : new String[] { "page=-1", "size=0", "size=51", "page=abc" }) {
            mockMvc.perform(get("/api/requests/mine?" + query).session(session))
                    .andExpect(status().isBadRequest());
        }
    }

    private Long addRequester(String email) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (full_name, email, password_hash, role)
                VALUES ('Test Requester', ?, (SELECT password_hash FROM users WHERE email = 'nora.ahmed@example.com'), 'REQUESTER')
                RETURNING user_id
                """, Long.class, email);
    }

    private Long addRequest(Long creatorId, String title, LocalDateTime createdAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO requests (title, description, type_id, priority, status, created_by, created_at, updated_at)
                VALUES (?, 'Test description', (SELECT type_id FROM request_types ORDER BY type_id LIMIT 1),
                        'MEDIUM', 'NEW', ?, ?, ?)
                RETURNING request_id
                """, Long.class, title, creatorId, createdAt, createdAt);
    }

    private MockHttpSession login(String email) throws Exception {
        Cookie csrf = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
