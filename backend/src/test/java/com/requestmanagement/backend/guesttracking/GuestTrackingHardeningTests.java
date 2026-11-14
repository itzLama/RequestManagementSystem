package com.requestmanagement.backend.guesttracking;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Hardening of Guest Tracking (no database needed). */
class GuestTrackingHardeningTests {

    @Test
    void theEnvExamplePlaceholderSecretIsRefused() {
        assertThatThrownBy(() -> new GuestTrackingCrypto("replace_with_a_long_random_secret"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("placeholder");
        assertThatThrownBy(() -> new GuestTrackingCrypto("REPLACE_WITH_SOMETHING_THAT_IS_LONG_ENOUGH_123"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GuestTrackingCrypto("too-short")).isInstanceOf(IllegalStateException.class);
        new GuestTrackingCrypto("a-real-random-secret-with-more-than-32-characters");
    }

    @Test
    void resendKeepsTheFailedAttemptBudgetOfTheChallenge() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 9, 0);
        GuestTrackingChallenge challenge = GuestTrackingChallenge.create(null, "lookup", "digest-1", now);
        for (int i = 0; i < 3; i++) challenge.fail();
        challenge.resend("digest-2", now.plusMinutes(2));
        assertThat(challenge.available(now.plusMinutes(2))).isTrue();
        challenge.fail();
        challenge.fail();
        // 5 wrong guesses in total, across both codes: the challenge is used up.
        assertThat(challenge.available(now.plusMinutes(3))).isFalse();
    }

    @Test
    void verificationCodeRequestsAreRateLimitedPerClientAddress() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-15T09:00:00Z"));
        GuestCodeRequestRateLimitFilter filter = new GuestCodeRequestRateLimitFilter(3, clock);
        for (int i = 0; i < 3; i++) assertThat(codeRequest(filter, "203.0.113.5").getStatus()).isEqualTo(200);
        MockHttpServletResponse blocked = codeRequest(filter, "203.0.113.5");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Too many verification code requests");
        assertThat(codeRequest(filter, "203.0.113.6").getStatus()).isEqualTo(200);

        clock.advance(Duration.ofMinutes(10));
        assertThat(codeRequest(filter, "203.0.113.5").getStatus()).isEqualTo(200);

        // Other tracking endpoints are not counted.
        MockHttpServletRequest verify = new MockHttpServletRequest("POST", "/api/guest/tracking/verify");
        verify.setRemoteAddr("203.0.113.5");
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(verify, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private static MockHttpServletResponse codeRequest(GuestCodeRequestRateLimitFilter filter, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", GuestCodeRequestRateLimitFilter.PATH);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
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
