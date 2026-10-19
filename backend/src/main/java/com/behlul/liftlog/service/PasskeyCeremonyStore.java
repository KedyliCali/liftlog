package com.behlul.liftlog.service;

import com.behlul.liftlog.entity.WebAuthnCeremony;
import com.behlul.liftlog.entity.WebAuthnCeremony.Kind;
import com.behlul.liftlog.exception.InvalidPasskeyException;
import com.behlul.liftlog.repository.WebAuthnCeremonyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
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

    public PasskeyCeremonyStore(WebAuthnCeremonyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Expired rows are swept here rather than by a scheduled job: every abandoned ceremony was started by
     * this method, so cleaning up on the next start keeps the table small without another moving part.
     */
    @Transactional
    public UUID start(Kind kind, Long userId, String requestJson) {
        LocalDateTime now = LocalDateTime.now(clock);
        repository.deleteExpired(now);
        UUID id = UUID.randomUUID();
        repository.save(new WebAuthnCeremony(id, kind, userId, requestJson, now.plus(LIFETIME)));
        return id;
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
        boolean expired = ceremony.getExpiresAt().isBefore(LocalDateTime.now(clock));
        return expired ? Optional.empty() : Optional.of(ceremony.getRequestJson());
    }
}
