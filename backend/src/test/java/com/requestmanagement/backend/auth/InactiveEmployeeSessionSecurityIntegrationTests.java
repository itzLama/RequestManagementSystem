package com.requestmanagement.backend.auth;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InactiveEmployeeSessionSecurityIntegrationTests {
    private static final String PASSWORD = "Temporary123";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void deactivationInvalidatesExistingSessionsAcrossProtectedEndpoints() throws Exception {
        Employee employee = createEmployee("Deactivated Session Employee");
        Session requestTypesSession = login(employee.email(), PASSWORD);
        Session requestsSession = login(employee.email(), PASSWORD);

        mvc.perform(get("/api/request-types").session(requestTypesSession.session()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/requests/mine?page=0&size=10").session(requestsSession.session()))
                .andExpect(status().isOk());

        Session admin = login("sara.saad@example.com", "Password123");
        mvc.perform(patch("/api/employees/{id}/deactivate", employee.id())
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header("X-XSRF-TOKEN", admin.csrf().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/request-types").session(requestTypesSession.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));
        mvc.perform(get("/api/requests/mine?page=0&size=10").session(requestsSession.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));

        assertThat(requestTypesSession.session().isInvalid()).isTrue();
        assertThat(requestsSession.session().isInvalid()).isTrue();
        mvc.perform(get("/api/request-types")).andExpect(status().isUnauthorized());

        Csrf csrf = csrf(null);
        loginRequest(employee.email(), PASSWORD, csrf)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is inactive."));
    }

    @Test
    void missingAccountInvalidatesItsExistingSession() throws Exception {
        Employee employee = createEmployee("Deleted Session Employee");
        Session session = login(employee.email(), PASSWORD);

        assertThat(jdbc.update("DELETE FROM users WHERE user_id=?", employee.id())).isOne();

        mvc.perform(get("/api/request-types").session(session.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));
        assertThat(session.session().isInvalid()).isTrue();
    }

    @Test
    void activeSessionsRoleAuthorizationAndCsrfRemainEnforced() throws Exception {
        Employee employee = createEmployee("Active Session Employee");
        Session employeeSession = login(employee.email(), PASSWORD);
        Session adminSession = login("sara.saad@example.com", "Password123");

        mvc.perform(get("/api/request-types").session(employeeSession.session()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/employees").session(adminSession.session()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/employees").session(employeeSession.session()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/requests").session(employeeSession.session())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid or missing CSRF token."));
    }

    @Test
    void publicAuthenticationCsrfAndGuestSubmissionRemainAvailable() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk());
        mvc.perform(get("/api/guest/request-types")).andExpect(status().isOk());

        long typeId = jdbc.queryForObject(
                "SELECT type_id FROM request_types WHERE is_active=true ORDER BY type_id LIMIT 1",
                Long.class);
        Csrf csrf = csrf(null);
        mvc.perform(post("/api/guest/requests/general")
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.240");
                            return request;
                        })
                        .contentType("application/json")
                        .content("""
                                {"guestName":"Phase 9 Guest","guestEmail":"phase9@example.com",
                                 "title":"Public submission remains available","description":"Security regression test",
                                 "typeId":%d,"priority":"LOW"}
                                """.formatted(typeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNumber());
    }

    private Employee createEmployee(String fullName) {
        String email = "phase9-" + UUID.randomUUID() + "@example.com";
        long id = jdbc.queryForObject("""
                INSERT INTO users(full_name, email, password_hash, role, is_active)
                VALUES (?, ?, ?, 'EMPLOYEE', true)
                RETURNING user_id
                """, Long.class, fullName, email, passwordEncoder.encode(PASSWORD));
        return new Employee(id, email);
    }

    private Session login(String email, String password) throws Exception {
        Csrf csrf = csrf(null);
        MvcResult result = loginRequest(email, password, csrf)
                .andExpect(status().isOk())
                .andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private org.springframework.test.web.servlet.ResultActions loginRequest(
            String email, String password, Csrf csrf
    ) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new Csrf(cookie.getValue(), cookie);
    }

    private record Employee(long id, String email) { }
    private record Csrf(String token, Cookie cookie) { }
    private record Session(MockHttpSession session, Csrf csrf) { }
}
