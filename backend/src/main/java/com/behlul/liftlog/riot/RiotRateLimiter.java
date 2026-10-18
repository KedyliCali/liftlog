package com.behlul.liftlog.riot;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps requests under a personal key's limits (20 per second, 100 per two minutes) with a margin, so a
 * burst of refreshes is cut short here instead of earning a 429 penalty from Riot.
 */
public class RiotRateLimiter {

    static final int PER_SECOND = 18;
    static final int PER_TWO_MINUTES = 90;

    private final Clock clock;
    private final Deque<Long> sent = new ArrayDeque<>();

    public RiotRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Takes one request slot, or throws when either window is full. */
    public synchronized void acquire() {
        long now = clock.millis();
        while (!sent.isEmpty() && now - sent.peekFirst() >= 120_000) {
            sent.pollFirst();
        }
        long lastSecond = sent.stream().filter(t -> now - t < 1_000).count();
        if (sent.size() >= PER_TWO_MINUTES || lastSecond >= PER_SECOND) {
            throw new RiotUnavailableException(RiotUnavailableException.Reason.RATE_LIMITED,
                    "Riot request budget used up, try again in a minute");
        }
        sent.addLast(now);
    }

    /** Requests still allowed in the two-minute window; refreshes skip players when this runs low. */
    public synchronized int remaining() {
        long now = clock.millis();
        long inWindow = sent.stream().filter(t -> now - t < 120_000).count();
        return (int) Math.max(0, PER_TWO_MINUTES - inWindow);
    }
}
