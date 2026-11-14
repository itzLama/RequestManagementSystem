package com.requestmanagement.backend.auth;

import com.requestmanagement.backend.TestEmployeeFixtureSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login throttling keyed by normalized email: 5 failures lock the account for 15 minutes with the
 * same generic 401, a success resets the counter, inactive accounts look like wrong passwords, and
 * over-long passwords are rejected without hashing. Each test uses its own throwaway accounts so the
 * shared seed users are never locked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginThrottlingIntegrationTests {
    private static final String INVALID = "Invalid email or password.";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void removeUsers() {
        users.forEach(id -> jdbc.update("DELETE FROM users WHERE user_id=?", id));
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheRightPasswordWithTheSameMessage() throws Exception {
        String email = employee();
        for (int i = 0; i < 5; i++) login(email, "wrong-password-" + i).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID));

        login(email, "Password123").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(INVALID));
        // Normalized key: other casing hits the same lock.
        login(email.toUpperCase(), "Password123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID));

        // Other accounts are unaffected.
        login(employee(), "Password123").andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCount() throws Exception {
        String email = employee();
        for (int i = 0; i < 4; i++) login(email, "wrong").andExpect(status().isUnauthorized());
        login(email, "Password123").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) login(email, "wrong").andExpect(status().isUnauthorized());
        login(email, "Password123").andExpect(status().isOk());
    }

    @Test
    void inactiveAccountsAndOverlongPasswordsGetTheGenericAnswer() throws Exception {
        String inactive = employee();
        jdbc.update("UPDATE users SET is_active=false WHERE email=?", inactive);
        login(inactive, "Password123").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(INVALID));
        login(inactive, "anything").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(INVALID));

        String active = employee();
        login(active, "x".repeat(73)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(INVALID));
        login(active, "Password123").andExpect(status().isOk());
    }

    private String employee() {
        var fixture = TestEmployeeFixtureSupport.createActiveEmployee(jdbc, "Throttle Test");
        users.add(fixture.id());
        return fixture.email();
    }

    private ResultActions login(String email, String password) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        return mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
