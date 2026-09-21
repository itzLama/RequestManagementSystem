package com.requestmanagement.backend.requesttype;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestTypeIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void requesterReceivesOnlyActiveTypesInNameOrder() throws Exception {
        MockHttpSession session = login("nora.ahmed@example.com");
        Long inactiveId = jdbcTemplate.queryForObject(
                "SELECT type_id FROM request_types WHERE is_active = true ORDER BY type_name LIMIT 1", Long.class);
        jdbcTemplate.update("UPDATE request_types SET is_active = false WHERE type_id = ?", inactiveId);

        MvcResult result = mockMvc.perform(get("/api/request-types").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].name").isString())
                .andExpect(jsonPath("$[0].active").doesNotExist())
                .andReturn();

        List<String> expectedNames = jdbcTemplate.queryForList(
                "SELECT type_name FROM request_types WHERE is_active = true ORDER BY type_name", String.class);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"id\":" + inactiveId + ",");
        int previousIndex = -1;
        for (String name : expectedNames) {
            int index = body.indexOf("\"name\":\"" + name + "\"");
            assertThat(index).isGreaterThan(previousIndex);
            previousIndex = index;
        }
        assertThat(body.split("\"name\":", -1)).hasSize(expectedNames.size() + 1);
    }

    @Test
    void adminCanReadAndUnauthenticatedUserCannot() throws Exception {
        mockMvc.perform(get("/api/request-types").session(login("sara.saad@example.com")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/request-types"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String email) throws Exception {
        Cookie csrf = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
