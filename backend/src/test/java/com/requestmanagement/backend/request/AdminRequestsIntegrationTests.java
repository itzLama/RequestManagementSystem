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
class AdminRequestsIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void adminCanRetrieveAllRequestsAndRequesterCannot() throws Exception {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM requests", Long.class);

        mockMvc.perform(get("/api/requests/admin").session(login("sara.saad@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].title").isString())
                .andExpect(jsonPath("$.content[0].requesterName").isString())
                .andExpect(jsonPath("$.content[0].typeName").isString())
                .andExpect(jsonPath("$.content[0].priority").isString())
                .andExpect(jsonPath("$.content[0].status").isString())
                .andExpect(jsonPath("$.content[0].createdAt").isString())
                .andExpect(jsonPath("$.content[0].description").doesNotExist());

        mockMvc.perform(get("/api/requests/admin").session(login("nora.ahmed@example.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/requests/admin/board").session(login("nora.ahmed@example.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/requests/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void filtersByTitleStatusTypeAndPriority() throws Exception {
        MockHttpSession admin = login("sara.saad@example.com");
        Long accessTypeId = jdbcTemplate.queryForObject(
                "SELECT type_id FROM request_types WHERE type_name = 'Access & Permissions'", Long.class);

        mockMvc.perform(get("/api/requests/admin?search=system acc").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("System Access"));
        mockMvc.perform(get("/api/requests/admin?status=IN_PROGRESS").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"));
        mockMvc.perform(get("/api/requests/admin?typeId=" + accessTypeId).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].typeName").value("Access & Permissions"));
        mockMvc.perform(get("/api/requests/admin?priority=LOW").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].priority").value("LOW"));
    }

    @Test
    void combinesFiltersAndPaginatesNewestFirst() throws Exception {
        MockHttpSession admin = login("sara.saad@example.com");
        Long facilitiesTypeId = jdbcTemplate.queryForObject(
                "SELECT type_id FROM request_types WHERE type_name = 'Facilities & Maintenance'", Long.class);

        mockMvc.perform(get("/api/requests/admin")
                        .param("search", "OFFICE")
                        .param("status", "WAITING_USER")
                        .param("typeId", facilitiesTypeId.toString())
                        .param("priority", "LOW")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Office Maintenance"));

        Long requesterId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = 'nora.ahmed@example.com'", Long.class);
        LocalDateTime now = LocalDateTime.now().plusDays(1);
        Long oldestId = addRequest(requesterId, "Admin paging oldest", now.minusMinutes(2));
        Long middleId = addRequest(requesterId, "Admin paging middle", now.minusMinutes(1));
        Long newestId = addRequest(requesterId, "Admin paging newest", now);

        mockMvc.perform(get("/api/requests/admin")
                        .param("search", "Admin paging")
                        .param("page", "0")
                        .param("size", "2")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newestId))
                .andExpect(jsonPath("$.content[1].id").value(middleId))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/requests/admin")
                        .param("search", "Admin paging")
                        .param("page", "1")
                        .param("size", "2")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(oldestId))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void adminBoardReturnsAllFilteredRequestsWithoutTablePagination() throws Exception {
        Long requesterId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = 'nora.ahmed@example.com'", Long.class);
        LocalDateTime base = LocalDateTime.now().plusDays(3);
        Long newestId = null;
        for (int index = 0; index < 12; index++) {
            newestId = addRequest(requesterId, "Kanban complete set " + index, base.plusMinutes(index));
        }

        mockMvc.perform(get("/api/requests/admin/board")
                        .param("search", "kanban COMPLETE")
                        .param("status", "NEW")
                        .param("priority", "MEDIUM")
                        .session(login("sara.saad@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].id").value(newestId))
                .andExpect(jsonPath("$[0].requesterName").value("Nora Ahmed"));
    }

    private Long addRequest(Long creatorId, String title, LocalDateTime createdAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO requests (title, description, type_id, priority, status, created_by, created_at, updated_at)
                VALUES (?, 'Admin list pagination test', (SELECT type_id FROM request_types ORDER BY type_id LIMIT 1),
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
