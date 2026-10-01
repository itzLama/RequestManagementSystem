package com.requestmanagement.backend.employee;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class EmployeeManagementIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void adminListsAndViewsEmployeesWithoutAdminOrPasswords() throws Exception {
        Session admin = login("sara.saad@example.com");
        Long noraId = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
        mvc.perform(get("/api/employees").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'nora.ahmed@example.com')]").exists())
                .andExpect(jsonPath("$[?(@.email == 'sara.saad@example.com')]").isEmpty())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/employees/{id}", noraId).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(noraId))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/employees/{id}", 1).session(admin.session())).andExpect(status().isNotFound());
    }

    @Test
    void adminCreatesNormalizedActiveEmployeeWithBcryptPassword() throws Exception {
        Session admin = login("sara.saad@example.com");
        mvc.perform(post("/api/employees").session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"fullName\":\"  Amal Saleh  \",\"email\":\"  AMAL.SALEH@EXAMPLE.COM  \",\"initialPassword\":\"Temporary123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fullName").value("Amal Saleh"))
                .andExpect(jsonPath("$.email").value("amal.saleh@example.com"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email='amal.saleh@example.com'", String.class);
        assertThat(hash).startsWith("$2").isNotEqualTo("Temporary123");
        assertThat(passwordEncoder.matches("Temporary123", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email='amal.saleh@example.com'", String.class)).isEqualTo("EMPLOYEE");
    }

    @Test
    void updatePreservesProtectedFieldsAndDeactivationIsIdempotent() throws Exception {
        Session admin = login("sara.saad@example.com");
        Long id = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id=?", String.class, id);
        mvc.perform(put("/api/employees/{id}", id).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"fullName\":\"  Nora A.  \",\"email\":\"NORA.NEW@EXAMPLE.COM\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Nora A."));
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE user_id=?", String.class, id)).isEqualTo("EMPLOYEE");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id=?", String.class, id)).isEqualTo(hash);
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/api/employees/{id}/deactivate", id).session(admin.session()).cookie(admin.csrf())
                            .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        }
    }

    @Test
    void validationDuplicatesMissingTargetsAndDeleteAreRejected() throws Exception {
        Session admin = login("sara.saad@example.com");
        for (String body : new String[]{
                "{\"fullName\":\"\",\"email\":\"bad\",\"initialPassword\":\"short\"}",
                "{\"fullName\":\"Duplicate\",\"email\":\"nora.ahmed@example.com\",\"initialPassword\":\"Temporary123\"}"
        }) {
            mvc.perform(post("/api/employees").session(admin.session()).cookie(admin.csrf())
                            .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content(body))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(400, 409));
        }
        mvc.perform(get("/api/employees/{id}", 999999999).session(admin.session())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/employees/{id}", 2).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void employeeAndUnauthenticatedUsersAreRejectedAndCsrfIsRequired() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/employees").session(login("nora.ahmed@example.com").session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/employees").session(login("sara.saad@example.com").session())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
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
