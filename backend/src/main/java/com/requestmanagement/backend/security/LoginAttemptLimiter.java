package com.requestmanagement.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Login throttling keyed by the normalized email address (not by client address, which is the
 * proxy's address behind Next.js). After {@code maxFailures} failed attempts the account's email is
 * locked for {@code lockout}; failures older than {@code lockout} are forgotten and a successful
 * login clears the history. While locked, every attempt (even with the right password) is answered
 * with the usual generic 401.
 *
 * <p>In-memory: counters reset when the backend restarts and are not shared between instances
 * (see documentation/known-limitations.md).
 */
@Component
public class LoginAttemptLimiter {
    static final int MAX_TRACKED_ACCOUNTS = 50_000;

    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    @Autowired
    public LoginAttemptLimiter(@Value("${masar.security.login.max-failures:5}") int maxFailures,
                               @Value("${masar.security.login.lockout:15m}") Duration lockout) {
        this(maxFailures, lockout, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxFailures, Duration lockout, Clock clock) {
        if (maxFailures < 1) throw new IllegalArgumentException("masar.security.login.max-failures must be at least 1.");
        if (lockout.isNegative() || lockout.isZero()) throw new IllegalArgumentException("masar.security.login.lockout must be positive.");
        this.maxFailures = maxFailures;
        this.lockout = lockout;
        this.clock = clock;
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(String email) {
        Attempts current = attempts.get(normalize(email));
        return current != null && current.lockedUntil() != null && clock.instant().isBefore(current.lockedUntil());
    }

    /** Records a failed attempt; returns true when this failure locked the account. */
    public boolean recordFailure(String email) {
        Instant now = clock.instant();
        cleanup(now);
        Attempts updated = attempts.compute(normalize(email), (key, current) -> {
            if (current != null && current.lockedUntil() != null && now.isBefore(current.lockedUntil())) return current;
            boolean fresh = current == null
                    || (current.lockedUntil() != null && !now.isBefore(current.lockedUntil()))
                    || (current.lockedUntil() == null && !now.isBefore(current.lastFailure().plus(lockout)));
            int failures = (fresh ? 0 : current.failures()) + 1;
            if (failures >= maxFailures) return new Attempts(0, now, now.plus(lockout));
            return new Attempts(failures, now, null);
        });
        return updated.lockedUntil() != null && updated.lastFailure().equals(now) && updated.failures() == 0;
    }

    public void recordSuccess(String email) {
        attempts.remove(normalize(email));
    }

    private void cleanup(Instant now) {
        if ((operations.incrementAndGet() & 255) != 0 && attempts.size() < MAX_TRACKED_ACCOUNTS) return;
        attempts.entrySet().removeIf(entry -> isStale(entry.getValue(), now));
    }

    private boolean isStale(Attempts value, Instant now) {
        Instant until = value.lockedUntil() != null ? value.lockedUntil() : value.lastFailure().plus(lockout);
        return !now.isBefore(until);
    }

    private record Attempts(int failures, Instant lastFailure, Instant lockedUntil) { }
}
