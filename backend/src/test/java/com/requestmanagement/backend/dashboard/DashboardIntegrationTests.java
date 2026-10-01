package com.requestmanagement.backend.dashboard;

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
class DashboardIntegrationTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminReceivesCorrectMetricsAndAllStatusesIncludingZeroCounts() throws Exception {
        clearRequests();
        addRequest("Dashboard New", "NEW", LocalDateTime.now().minusMinutes(4));
        addRequest("Dashboard In Progress", "IN_PROGRESS", LocalDateTime.now().minusMinutes(3));
        addRequest("Dashboard Completed", "COMPLETED", LocalDateTime.now().minusMinutes(1));
        addRequest("Dashboard Rejected", "REJECTED", LocalDateTime.now());

        mvc.perform(get("/api/dashboard").session(login("sara.saad@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(4))
                .andExpect(jsonPath("$.openRequests").value(2))
                .andExpect(jsonPath("$.completedRequests").value(1))
                .andExpect(jsonPath("$.requestsByStatus.length()").value(5))
                .andExpect(jsonPath("$.requestsByStatus[0].status").value("NEW"))
                .andExpect(jsonPath("$.requestsByStatus[0].count").value(1))
                .andExpect(jsonPath("$.requestsByStatus[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.requestsByStatus[1].count").value(1))
                .andExpect(jsonPath("$.requestsByStatus[2].status").value("WAITING_USER"))
                .andExpect(jsonPath("$.requestsByStatus[2].count").value(0))
                .andExpect(jsonPath("$.requestsByStatus[3].status").value("COMPLETED"))
                .andExpect(jsonPath("$.requestsByStatus[3].count").value(1))
                .andExpect(jsonPath("$.requestsByStatus[4].status").value("REJECTED"))
                .andExpect(jsonPath("$.requestsByStatus[4].count").value(1));
    }

    @Test
    void latestRequestsAreLimitedOrderedAndExposeOnlySafeSummaryFields() throws Exception {
        clearRequests();
        LocalDateTime base = LocalDateTime.now().plusDays(1);
        addRequest("Dashboard Older 1", "NEW", base.minusMinutes(2));
        addRequest("Dashboard Older 2", "NEW", base.minusMinutes(1));
        addRequest("Dashboard Latest 1", "IN_PROGRESS", base);
        Long latestTieId = addRequest("Dashboard Latest 2", "WAITING_USER", base);
        addRequest("Dashboard Older 3", "COMPLETED", base.minusMinutes(3));
        addRequest("Dashboard Older 4", "REJECTED", base.minusMinutes(4));
        addRequest("Dashboard Older 5", "NEW", base.minusMinutes(5));

        mvc.perform(get("/api/dashboard").session(login("sara.saad@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestRequests.length()").value(5))
                .andExpect(jsonPath("$.latestRequests[0].id").value(latestTieId))
                .andExpect(jsonPath("$.latestRequests[0].title").value("Dashboard Latest 2"))
                .andExpect(jsonPath("$.latestRequests[1].title").value("Dashboard Latest 1"))
                .andExpect(jsonPath("$.latestRequests[2].title").value("Dashboard Older 2"))
                .andExpect(jsonPath("$.latestRequests[3].title").value("Dashboard Older 1"))
                .andExpect(jsonPath("$.latestRequests[4].title").value("Dashboard Older 3"))
                .andExpect(jsonPath("$.latestRequests[0].typeName").isString())
                .andExpect(jsonPath("$.latestRequests[0].priority").value("MEDIUM"))
                .andExpect(jsonPath("$.latestRequests[0].status").value("WAITING_USER"))
                .andExpect(jsonPath("$.latestRequests[0].createdAt").isString())
                .andExpect(jsonPath("$.latestRequests[0].description").doesNotExist())
                .andExpect(jsonPath("$.latestRequests[0].assignedToId").doesNotExist())
                .andExpect(jsonPath("$.latestRequests[0].comments").doesNotExist());
    }

    @Test
    void dashboardRequiresAdminRole() throws Exception {
        mvc.perform(get("/api/dashboard").session(login("sara.saad@example.com")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").session(login("nora.ahmed@example.com")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    private void clearRequests() {
        jdbc.update("DELETE FROM status_history");
        jdbc.update("DELETE FROM comments");
        jdbc.update("DELETE FROM requests");
    }

    private Long addRequest(String title, String requestStatus, LocalDateTime createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title, description, type_id, priority, status, created_by, created_at, updated_at)
                VALUES (?, 'Dashboard test details',
                        (SELECT type_id FROM request_types ORDER BY type_id LIMIT 1),
                        'MEDIUM', ?,
                        (SELECT user_id FROM users WHERE email='nora.ahmed@example.com'), ?, ?)
                RETURNING request_id
                """, Long.class, title, requestStatus, createdAt, createdAt);
    }

    private MockHttpSession login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
