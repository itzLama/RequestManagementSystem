package com.requestmanagement.backend.guesttracking;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limits unauthenticated verification-code requests (each one creates a challenge row and may send an
 * email) per client address: {@code masar.guest-tracking.code-requests-per-window} per 10 minutes.
 * The per Tracking Number + email limit (5 per hour) stays in the database.
 *
 * <p>In-memory and keyed by the connecting address: behind the Next.js proxy that address is shared,
 * so the limit currently applies to all Guests together (see documentation/known-limitations.md).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class GuestCodeRequestRateLimitFilter extends OncePerRequestFilter {
    static final String PATH = "/api/guest/tracking/request-code";
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final int maxRequests;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    @Autowired
    public GuestCodeRequestRateLimitFilter(@Value("${masar.guest-tracking.code-requests-per-window:30}") int maxRequests) {
        this(maxRequests, Clock.systemUTC());
    }

    GuestCodeRequestRateLimitFilter(int maxRequests, Clock clock) {
        if (maxRequests < 1) throw new IllegalArgumentException("masar.guest-tracking.code-requests-per-window must be at least 1.");
        this.maxRequests = maxRequests;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!allow(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many verification code requests. Please try again later.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    boolean allow(String clientAddress) {
        Instant now = clock.instant();
        Window result = windows.compute(clientAddress == null ? "" : clientAddress, (key, current) ->
                current == null || !now.isBefore(current.startedAt().plus(WINDOW))
                        ? new Window(now, 1) : new Window(current.startedAt(), current.count() + 1));
        if ((operations.incrementAndGet() & 255) == 0) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
        }
        return result.count() <= maxRequests;
    }

    private record Window(Instant startedAt, int count) { }
}
