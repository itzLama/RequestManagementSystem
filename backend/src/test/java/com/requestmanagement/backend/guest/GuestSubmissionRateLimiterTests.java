package com.requestmanagement.backend.guest;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import static org.assertj.core.api.Assertions.assertThat;

class GuestSubmissionRateLimiterTests {
    @Test
    void limitResetsAfterWindowWithoutSleeping() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-08T00:00:00Z"));
        GuestSubmissionRateLimiter limiter = new GuestSubmissionRateLimiter(clock);
        for (int index = 0; index < 5; index++) assertThat(limiter.allow("client")).isTrue();
        assertThat(limiter.allow("client")).isFalse();
        clock.instant = clock.instant.plus(GuestSubmissionRateLimiter.WINDOW);
        assertThat(limiter.allow("client")).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
