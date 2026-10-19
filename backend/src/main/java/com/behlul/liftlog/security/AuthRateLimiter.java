package com.behlul.liftlog.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-window request counter per client, in memory. Guards the anonymous auth endpoints, where each request
 * costs a BCrypt check or a database row: it slows down password guessing and flooding from one client.
 *
 * <p>In memory on purpose: one instance runs today, and a restart only resets the counters, which is harmless
 * for a limit this coarse. Memory is bounded: idle clients are swept out, and when the table is full of
 * active clients a new client is refused rather than letting the table grow without end.
 */
@Component
public class AuthRateLimiter {

    /** Sweep idle clients every this many requests even when the table is not full, so it stays small. */
    private static final int SWEEP_EVERY = 1000;

    private final int limit;
    private final long windowMillis;
    private final int maxClients;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    @Autowired
    public AuthRateLimiter(@Value("${liftlog.rate-limit.auth.requests-per-minute:30}") int requestsPerMinute,
                           @Value("${liftlog.rate-limit.auth.max-clients:10000}") int maxClients, Clock clock) {
        this(requestsPerMinute, Duration.ofMinutes(1), maxClients, clock);
    }

    AuthRateLimiter(int limit, Duration window, int maxClients, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.maxClients = maxClients;
        this.clock = clock;
    }

    /** Counts one request from this client; false when it is over the limit for the current window. */
    public boolean tryAcquire(String client) {
        long now = clock.millis();
        if (calls.incrementAndGet() % SWEEP_EVERY == 0) {
            evictIdle(now);
        }
        if (!windows.containsKey(client) && windows.size() >= maxClients) {
            evictIdle(now);
            if (windows.size() >= maxClients) {
                return false;
            }
        }
        // compute is atomic per key, so concurrent requests from one client are all counted
        Window window = windows.compute(client, (key, current) ->
                current == null || now - current.start() >= windowMillis ? new Window(now, 1) : current.next());
        return window.count() <= limit;
    }

    int trackedClients() {
        return windows.size();
    }

    /** A client whose window has ended would start from zero anyway, so forgetting it changes nothing. */
    private void evictIdle(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= windowMillis);
    }

    private record Window(long start, int count) {
        Window next() {
            return new Window(start, count + 1);
        }
    }
}
