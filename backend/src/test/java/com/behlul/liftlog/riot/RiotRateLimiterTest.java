package com.behlul.liftlog.riot;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiotRateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        long millis = 1_000_000;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    @Test
    void stopsBeforeThePerSecondLimit() {
        ManualClock clock = new ManualClock();
        RiotRateLimiter limiter = new RiotRateLimiter(clock);
        for (int i = 0; i < RiotRateLimiter.PER_SECOND; i++) {
            limiter.acquire();
        }
        assertThatThrownBy(limiter::acquire).isInstanceOf(RiotUnavailableException.class);

        clock.millis += 1_000;
        limiter.acquire();
    }

    @Test
    void stopsBeforeTheTwoMinuteLimitAndRecovers() {
        ManualClock clock = new ManualClock();
        RiotRateLimiter limiter = new RiotRateLimiter(clock);
        for (int i = 0; i < RiotRateLimiter.PER_TWO_MINUTES; i++) {
            limiter.acquire();
            clock.millis += 1_000; // spread out so the per-second window never fills
        }
        assertThat(limiter.remaining()).isZero();
        assertThatThrownBy(limiter::acquire)
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(RiotUnavailableException.Reason.RATE_LIMITED));

        clock.millis += 120_000;
        assertThat(limiter.remaining()).isEqualTo(RiotRateLimiter.PER_TWO_MINUTES);
    }
}
