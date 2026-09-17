package com.requestmanagement.backend.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTests {

    private static final String LOGIN_JSON = """
            {"email":"%s","password":"%s"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminLoginRestoresSessionAndLogoutInvalidatesIt() throws Exception {
        Csrf csrf = getCsrfToken(null);
        MvcResult login = login("sara.saad@example.com", "Password123", csrf)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        SecurityContext context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN")
                .doesNotContain("ROLE_REQUESTER");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Sara Saad"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        Csrf authenticatedCsrf = getCsrfToken(session);
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(authenticatedCsrf.cookie())
                        .header("X-XSRF-TOKEN", authenticatedCsrf.token()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requesterLoginCreatesRequesterAuthority() throws Exception {
        Csrf csrf = getCsrfToken(null);
        MvcResult login = login("nora.ahmed@example.com", "Password123", csrf)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REQUESTER"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        SecurityContext context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getAuthorities())
                .extracting("authority")
                .contains("ROLE_REQUESTER")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void invalidCredentialsReturnUnauthorized() throws Exception {
        Csrf csrf = getCsrfToken(null);
        login("sara.saad@example.com", "wrong-password", csrf)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void missingOrInvalidCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(LOGIN_JSON.formatted("sara.saad@example.com", "Password123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid or missing CSRF token."));

        Csrf csrf = getCsrfToken(null);
        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", "invalid-token")
                        .contentType("application/json")
                        .content(LOGIN_JSON.formatted("sara.saad@example.com", "Password123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid or missing CSRF token."));
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email,
            String password,
            Csrf csrf
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .contentType("application/json")
                .content(LOGIN_JSON.formatted(email, password)));
    }

    private Csrf getCsrfToken(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) {
            request.session(session);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new Csrf(cookie.getValue(), cookie);
    }

    private record Csrf(String token, Cookie cookie) {
    }
}
