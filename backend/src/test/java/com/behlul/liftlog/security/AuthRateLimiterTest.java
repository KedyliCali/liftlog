package com.behlul.liftlog.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimiterTest {

    /** A clock the test moves by hand. */
    static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-10-19T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final ManualClock clock = new ManualClock();

    @Test
    void allowsTheLimitPerWindowThenRefusesUntilTheWindowEnds() {
        AuthRateLimiter limiter = new AuthRateLimiter(3, Duration.ofMinutes(1), 100, clock);
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        // Other clients have their own budget
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();

        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }

    @Test
    void memoryIsBoundedIdleClientsAreForgottenAndNewOnesRefusedWhenFull() {
        AuthRateLimiter limiter = new AuthRateLimiter(3, Duration.ofMinutes(1), 2, clock);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();
        // Full of active clients: a new one is refused instead of growing the table
        assertThat(limiter.tryAcquire("c")).isFalse();
        assertThat(limiter.trackedClients()).isEqualTo(2);
        // Known clients keep their budget
        assertThat(limiter.tryAcquire("a")).isTrue();

        clock.advance(Duration.ofMinutes(1));
        // a and b are idle now, so they are swept to make room
        assertThat(limiter.tryAcquire("c")).isTrue();
        assertThat(limiter.trackedClients()).isEqualTo(1);
    }
}
