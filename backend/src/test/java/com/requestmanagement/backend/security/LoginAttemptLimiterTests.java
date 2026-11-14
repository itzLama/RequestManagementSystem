package com.requestmanagement.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTests {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-15T08:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(5, Duration.ofMinutes(15), clock);

    @Test
    void locksAfterFiveFailuresForFifteenMinutes() {
        for (int i = 0; i < 4; i++) assertThat(limiter.recordFailure("user@example.com")).isFalse();
        assertThat(limiter.isLocked("user@example.com")).isFalse();
        assertThat(limiter.recordFailure(" USER@example.com ")).isTrue();
        assertThat(limiter.isLocked("user@example.com")).isTrue();

        // Failures while locked do not extend or reset the lock.
        clock.advance(Duration.ofMinutes(10));
        limiter.recordFailure("user@example.com");
        assertThat(limiter.isLocked("user@example.com")).isTrue();
        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.isLocked("user@example.com")).isFalse();

        // After the cooldown the count starts again from zero.
        for (int i = 0; i < 4; i++) limiter.recordFailure("user@example.com");
        assertThat(limiter.isLocked("user@example.com")).isFalse();
    }

    @Test
    void oldFailuresExpireAndSuccessClearsTheHistory() {
        for (int i = 0; i < 4; i++) limiter.recordFailure("a@example.com");
        clock.advance(Duration.ofMinutes(16));
        limiter.recordFailure("a@example.com");
        assertThat(limiter.isLocked("a@example.com")).isFalse();

        for (int i = 0; i < 3; i++) limiter.recordFailure("b@example.com");
        limiter.recordSuccess("B@example.com");
        for (int i = 0; i < 4; i++) limiter.recordFailure("b@example.com");
        assertThat(limiter.isLocked("b@example.com")).isFalse();
        assertThat(limiter.isLocked("other@example.com")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
