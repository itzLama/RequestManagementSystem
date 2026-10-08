package com.requestmanagement.backend.guest;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GuestSubmissionRateLimiter {
    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong attempts = new AtomicLong();
    private final Clock clock;

    public GuestSubmissionRateLimiter() { this(Clock.systemUTC()); }
    GuestSubmissionRateLimiter(Clock clock) { this.clock = clock; }

    public boolean allow(String clientAddress) {
        Instant now = clock.instant();
        Window result = windows.compute(clientAddress, (key, current) -> current == null || !now.isBefore(current.startedAt().plus(WINDOW))
                ? new Window(now, 1) : new Window(current.startedAt(), current.attempts() + 1));
        if ((attempts.incrementAndGet() & 255) == 0) windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
        return result.attempts() <= MAX_ATTEMPTS;
    }

    private record Window(Instant startedAt, int attempts) { }
}
