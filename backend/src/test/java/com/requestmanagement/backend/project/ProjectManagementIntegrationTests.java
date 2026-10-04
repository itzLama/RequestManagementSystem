package com.requestmanagement.backend.project;

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
class ProjectManagementIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void adminCreatesListsViewsAndUpdatesActiveProject() throws Exception {
        Session admin = login("sara.saad@example.com");
        MvcResult created = mvc.perform(post("/api/projects").session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"  HR Portal Upgrade  \",\"description\":\"  Improve the portal  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("HR Portal Upgrade"))
                .andExpect(jsonPath("$.description").value("Improve the portal"))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
        long id = Long.parseLong(com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString());
        mvc.perform(get("/api/projects").session(admin.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
        mvc.perform(get("/api/projects/{id}", id).session(admin.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("HR Portal Upgrade"));
        mvc.perform(put("/api/projects/{id}", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"HR Platform\",\"description\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("HR Platform"))
                .andExpect(jsonPath("$.description").doesNotExist()).andExpect(jsonPath("$.status").value("ACTIVE"));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT project_name FROM projects WHERE project_id=?", String.class, id))
                .isEqualTo("HR Platform");
    }

    @Test
    void archiveIsFinalAndIdempotentWithoutChangingTimestampAgain() throws Exception {
        long id = insertProject("Archive test");
        Session admin = login("sara.saad@example.com");
        mvc.perform(patch("/api/projects/{id}/archive", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        var archivedAt = jdbc.queryForObject("SELECT updated_at FROM projects WHERE project_id=?", java.time.LocalDateTime.class, id);
        mvc.perform(patch("/api/projects/{id}/archive", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        assertThat(jdbc.queryForObject("SELECT updated_at FROM projects WHERE project_id=?", java.time.LocalDateTime.class, id))
                .isEqualTo(archivedAt);
        mvc.perform(put("/api/projects/{id}", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"Changed\",\"description\":null}"))
                .andExpect(status().isConflict());
    }

    @Test
    void validationMissingProjectsAndDeleteAreRejected() throws Exception {
        Session admin = login("sara.saad@example.com");
        for (String body : new String[]{"{\"name\":\"   \"}", "{\"name\":\"Valid\",\"description\":\"" + "x".repeat(2001) + "\"}"}) {
            mvc.perform(post("/api/projects").session(admin.session()).cookie(admin.csrf())
                            .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        }
        mvc.perform(get("/api/projects/{id}", 999999999).session(admin.session())).andExpect(status().isNotFound());
        long id = insertProject("No delete");
        mvc.perform(delete("/api/projects/{id}", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void projectManagementIsAdminOnlyAndMutationsRequireCsrf() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects").session(login("nora.ahmed@example.com").session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/projects").session(login("sara.saad@example.com").session())
                        .contentType("application/json").content("{\"name\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    private long insertProject(String name) {
        return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id", Long.class, name);
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
