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
class CreateRequestIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void requesterCreatesRequestWithServerDerivedCreatorAndInitialState() throws Exception {
        Login login = login("nora.ahmed@example.com");
        Long typeId = activeTypeId();
        create(login, json("  New laptop  ", "  Needs repair  ", typeId, "HIGH"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New laptop"))
                .andExpect(jsonPath("$.description").value("Needs repair"))
                .andExpect(jsonPath("$.typeId").value(typeId))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdByUserId").value(2))
                .andExpect(jsonPath("$.assignedToUserId").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.id").exists());

        var row = jdbcTemplate.queryForMap(
                "SELECT created_by, status, assigned_to FROM requests WHERE title = ?", "New laptop");
        assertThat(((Number) row.get("created_by")).longValue()).isEqualTo(2L);
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("assigned_to")).isNull();
    }

    @Test
    void rejectsInvalidFieldsAndUntrustedCreatorField() throws Exception {
        Login login = login("nora.ahmed@example.com");
        Long typeId = activeTypeId();
        create(login, json(" ", "Description", typeId, "LOW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Title is required."));
        create(login, json("x".repeat(201), "Description", typeId, "LOW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Title must be at most 200 characters."));
        create(login, json("Title", " ", typeId, "LOW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Description is required."));
        create(login, json("Title", "Description", -1L, "LOW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request type ID must be positive."));
        create(login, json("Title", "Description", typeId, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Priority is required."));

        String extraCreator = """
                {"title":"Title","description":"Description","typeId":%d,"priority":"LOW","createdBy":1}
                """.formatted(typeId);
        create(login, extraCreator)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByUserId").value(2));
    }

    @Test
    void rejectsNonexistentAndInactiveRequestTypes() throws Exception {
        Login login = login("nora.ahmed@example.com");
        create(login, json("Title", "Description", Long.MAX_VALUE, "LOW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request type does not exist."));

        Long typeId = activeTypeId();
        jdbcTemplate.update("UPDATE request_types SET is_active = false WHERE type_id = ?", typeId);
        create(login, json("Title", "Description", typeId, "LOW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request type is inactive."));
    }

    @Test
    void unauthenticatedAndAdminUsersCannotCreate() throws Exception {
        Cookie csrf = csrf(null);
        mockMvc.perform(post("/api/requests")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content(json("Title", "Description", activeTypeId(), "LOW")))
                .andExpect(status().isUnauthorized());

        Login admin = login("sara.saad@example.com");
        create(admin, json("Title", "Description", activeTypeId(), "LOW"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfIsRequiredAndInvalidTokensAreRejected() throws Exception {
        Login login = login("nora.ahmed@example.com");
        String body = json("Title", "Description", activeTypeId(), "LOW");
        mockMvc.perform(post("/api/requests")
                        .session(login.session())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/requests")
                        .session(login.session())
                        .cookie(login.csrf())
                        .header("X-XSRF-TOKEN", "invalid")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions create(Login login, String body) throws Exception {
        return mockMvc.perform(post("/api/requests")
                .session(login.session())
                .cookie(login.csrf())
                .header("X-XSRF-TOKEN", login.csrf().getValue())
                .contentType("application/json")
                .content(body));
    }

    private Login login(String email) throws Exception {
        Cookie csrf = csrf(null);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        return new Login(session, csrf(session));
    }

    private Cookie csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        Cookie cookie = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private Long activeTypeId() {
        return jdbcTemplate.queryForObject(
                "SELECT type_id FROM request_types WHERE is_active = true ORDER BY type_id LIMIT 1", Long.class);
    }

    private String json(String title, String description, Long typeId, String priority) {
        return "{\"title\":\"" + title + "\",\"description\":\"" + description
                + "\",\"typeId\":" + typeId + ",\"priority\":"
                + (priority == null ? "null" : "\"" + priority + "\"") + "}";
    }

    private record Login(MockHttpSession session, Cookie csrf) { }
}
