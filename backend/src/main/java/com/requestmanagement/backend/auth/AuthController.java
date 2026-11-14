package com.requestmanagement.backend.auth;

import com.requestmanagement.backend.security.AuthenticatedUser;
import com.requestmanagement.backend.security.LoginAttemptLimiter;
import com.requestmanagement.backend.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    /** BCrypt only uses the first 72 bytes; longer passwords can never be valid here. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final LoginAttemptLimiter loginAttempts;

    @PostMapping("/login")
    public AuthUserResponse login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String email = loginRequest.email().trim();
        // Locked accounts get the same answer as a wrong password, even with the right password.
        if (loginAttempts.isLocked(email)) throw invalidCredentials();
        if (loginRequest.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            recordFailure(email);
            throw invalidCredentials();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, loginRequest.password())
            );
        } catch (AuthenticationException exception) {
            // Wrong password, unknown email and inactive accounts all look the same to the caller.
            recordFailure(email);
            throw invalidCredentials();
        }
        loginAttempts.recordSuccess(email);

        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);
        csrfTokenRepository.saveToken(null, request, response);

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        User user = authService.getActiveUser(principal.userId());
        return AuthUserResponse.from(user);
    }

    private void recordFailure(String email) {
        if (loginAttempts.recordFailure(email)) {
            log.warn("Sign-in temporarily locked for one account after repeated failed attempts.");
        }
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    @GetMapping("/me")
    public AuthUserResponse currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required."
            );
        }

        return AuthUserResponse.from(authService.getActiveUser(principal.userId()));
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return CsrfTokenResponse.from(csrfToken);
    }

}
