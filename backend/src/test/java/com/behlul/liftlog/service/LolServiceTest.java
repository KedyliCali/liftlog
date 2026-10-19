package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.TrackPlayerRequest;
import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.exception.RiotIdNotFoundException;
import com.behlul.liftlog.repository.LolMatchPlayerRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.riot.FakeRiotClient;
import com.behlul.liftlog.riot.RiotClient;
import com.behlul.liftlog.riot.RiotRateLimiter;
import com.behlul.liftlog.riot.RiotUnavailableException;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The refresh rules with Riot replaced by the fake client. A spy, so single answers (like "no profile on this
 * server", which the fake never gives on its own) can be swapped in per test.
 */
@ExtendWith(MockitoExtension.class)
class LolServiceTest {

    private static final long USER_ID = 7L;

    @Mock private LolPlayerRepository playerRepository;
    @Mock private LolMatchPlayerRepository matchRepository;
    @Mock private RiotRateLimiter limiter;
    @Mock private CurrentUserProvider currentUser;

    private final RiotClient riot = spy(new FakeRiotClient());
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneOffset.UTC);
    private LolService service;

    @BeforeEach
    void setUp() {
        service = new LolService(playerRepository, matchRepository, riot, limiter, currentUser, clock);
        when(currentUser.currentUserId()).thenReturn(USER_ID);
    }

    @Test
    void wrongServerIsRiotIdNotFound() {
        when(playerRepository.findById(anyString())).thenReturn(Optional.empty());
        doReturn(Optional.empty()).when(riot).summoner(any(), anyString());

        assertThatThrownBy(() -> service.track(new TrackPlayerRequest("Wrong Server#TR1", "EUW1")))
                .isInstanceOf(RiotIdNotFoundException.class)
                .hasMessage("Riot ID Wrong Server#TR1 was not found on EUW1");
        verify(playerRepository, never()).track(anyLong(), anyString());
    }

    @Test
    void manualRefreshWithoutProfileIsRiotError() {
        LolPlayer moved = new LolPlayer("moved", "TR1", "Moved", "TR1");
        when(playerRepository.isTrackedBy(USER_ID, "moved")).thenReturn(true);
        when(playerRepository.findById("moved")).thenReturn(Optional.of(moved));
        doReturn(Optional.empty()).when(riot).summoner(any(), anyString());

        assertThatThrownBy(() -> service.refreshNow("moved"))
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(RiotUnavailableException.Reason.RIOT_ERROR));
        assertThat(moved.getRefreshedAt()).isNull();
    }
}
