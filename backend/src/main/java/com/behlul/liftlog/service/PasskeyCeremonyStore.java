package com.behlul.liftlog.service;

import com.behlul.liftlog.entity.WebAuthnCeremony;
import com.behlul.liftlog.entity.WebAuthnCeremony.Kind;
import com.behlul.liftlog.exception.InvalidPasskeyException;
import com.behlul.liftlog.exception.TooManyRequestsException;
import com.behlul.liftlog.repository.WebAuthnCeremonyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Started passkey ceremonies. The stored request carries the challenge the authenticator must sign, so the
 * row is what makes a response fresh: it is single use and lives 5 minutes.
 */
@Component
public class PasskeyCeremonyStore {

    static final Duration LIFETIME = Duration.ofMinutes(5);
    static final String UNKNOWN_CEREMONY = "Passkey ceremony is unknown, expired or already used";

    private final WebAuthnCeremonyRepository repository;
    private final Clock clock;
    private final int maxLiveLoginCeremonies;

    public PasskeyCeremonyStore(WebAuthnCeremonyRepository repository, Clock clock,
                                @Value("${liftlog.passkey.max-live-login-ceremonies:500}") int maxLiveLoginCeremonies) {
        this.repository = repository;
        this.clock = clock;
        this.maxLiveLoginCeremonies = maxLiveLoginCeremonies;
    }

    /** @param expiresAt when the ceremony stops being accepted, for the client to show or plan around */
    public record Started(UUID id, Instant expiresAt) {
    }

    /**
     * Expired rows are swept here rather than by a scheduled job: every abandoned ceremony was started by
     * this method, so cleaning up on the next start keeps the table small without another moving part.
     *
     * <p>Sign-in options are anonymous, so anyone can ask for them; the per-client rate limit slows one client
     * down, and this global cap keeps many clients together from filling the table. The count and the insert
     * are not atomic, so concurrent requests may overshoot the cap slightly, which is harmless. The refusal
     * does not roll back the sweep.
     */
    @Transactional(noRollbackFor = TooManyRequestsException.class)
    public Started start(Kind kind, Long userId, String requestJson) {
        Instant now = clock.instant();
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        repository.deleteExpired(nowUtc);
        if (kind == Kind.LOGIN && repository.countByKindAndExpiresAtAfter(Kind.LOGIN, nowUtc) >= maxLiveLoginCeremonies) {
            throw new TooManyRequestsException("Too many passkey sign-ins in progress, try again in a few minutes");
        }
        UUID id = UUID.randomUUID();
        Instant expiresAt = now.plus(LIFETIME);
        repository.save(new WebAuthnCeremony(id, kind, userId, requestJson, LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)));
        return new Started(id, expiresAt);
    }

    /**
     * Deletes the ceremony and returns its request JSON, or empty when it had expired. Commits on its own
     * (callers are not transactional), so the ceremony is gone even when the verification that follows fails:
     * a failed attempt cannot be retried with the same challenge. Expiry is reported as a value rather than
     * an exception because an exception here would roll the delete back.
     *
     * <p>A ceremony of another kind or another user's registration is left alone and answered like an unknown
     * one, so a leaked id cannot be used to cancel someone else's ceremony or to learn that it exists.
     */
    @Transactional
    public Optional<String> consume(UUID id, Kind kind, Long userId) {
        WebAuthnCeremony ceremony = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new InvalidPasskeyException(UNKNOWN_CEREMONY));
        if (ceremony.getKind() != kind || !Objects.equals(ceremony.getUserId(), userId)) {
            throw new InvalidPasskeyException(UNKNOWN_CEREMONY);
        }
        repository.delete(ceremony);
        boolean expired = ceremony.getExpiresAt().isBefore(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return expired ? Optional.empty() : Optional.of(ceremony.getRequestJson());
    }
}
