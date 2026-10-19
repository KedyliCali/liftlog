package com.behlul.liftlog.service;

import com.behlul.liftlog.entity.WebAuthnCeremony;
import com.behlul.liftlog.entity.WebAuthnCeremony.Kind;
import com.behlul.liftlog.exception.InvalidPasskeyException;
import com.behlul.liftlog.repository.WebAuthnCeremonyRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasskeyCeremonyStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final WebAuthnCeremonyRepository repository = mock(WebAuthnCeremonyRepository.class);
    private final PasskeyCeremonyStore store = new PasskeyCeremonyStore(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void startSweepsExpiredRowsAndStoresFiveMinutes() {
        UUID id = store.start(Kind.LOGIN, null, "{}");

        verify(repository).deleteExpired(NOW_LOCAL);
        ArgumentCaptor<WebAuthnCeremony> saved = ArgumentCaptor.forClass(WebAuthnCeremony.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(id);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW_LOCAL.plusMinutes(5));
    }

    @Test
    void consumeDeletesAndReturnsTheRequest() {
        WebAuthnCeremony ceremony = ceremony(Kind.REGISTER, 7L, NOW_LOCAL.plusMinutes(1));
        when(repository.findByIdForUpdate(ceremony.getId())).thenReturn(Optional.of(ceremony));

        assertThat(store.consume(ceremony.getId(), Kind.REGISTER, 7L)).contains("{\"challenge\":1}");
        verify(repository).delete(ceremony);
    }

    @Test
    void anExpiredCeremonyIsDeletedButNotReturned() {
        WebAuthnCeremony ceremony = ceremony(Kind.LOGIN, null, NOW_LOCAL.minusSeconds(1));
        when(repository.findByIdForUpdate(ceremony.getId())).thenReturn(Optional.of(ceremony));

        assertThat(store.consume(ceremony.getId(), Kind.LOGIN, null)).isEmpty();
        verify(repository).delete(ceremony);
    }

    @Test
    void wrongKindOrAnotherUsersCeremonyIsRefusedAndLeftAlone() {
        WebAuthnCeremony ceremony = ceremony(Kind.REGISTER, 7L, NOW_LOCAL.plusMinutes(1));
        when(repository.findByIdForUpdate(ceremony.getId())).thenReturn(Optional.of(ceremony));

        assertThatThrownBy(() -> store.consume(ceremony.getId(), Kind.LOGIN, null))
                .isInstanceOf(InvalidPasskeyException.class);
        assertThatThrownBy(() -> store.consume(ceremony.getId(), Kind.REGISTER, 8L))
                .isInstanceOf(InvalidPasskeyException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void anUnknownCeremonyIsRefused() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.consume(id, Kind.LOGIN, null))
                .isInstanceOf(InvalidPasskeyException.class)
                .hasMessage(PasskeyCeremonyStore.UNKNOWN_CEREMONY);
    }

    private static WebAuthnCeremony ceremony(Kind kind, Long userId, LocalDateTime expiresAt) {
        return new WebAuthnCeremony(UUID.randomUUID(), kind, userId, "{\"challenge\":1}", expiresAt);
    }
}
