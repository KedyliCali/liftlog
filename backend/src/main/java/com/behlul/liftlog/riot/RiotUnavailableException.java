package com.behlul.liftlog.riot;

/**
 * Riot cannot answer right now: no key configured, the key was rejected, the rate limit is used up, or
 * Riot itself is down. Reads fall back to cached data; actions that need fresh data report it.
 */
public class RiotUnavailableException extends RuntimeException {

    public enum Reason { NO_KEY, KEY_REJECTED, RATE_LIMITED, RIOT_ERROR }

    private final Reason reason;

    public RiotUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
