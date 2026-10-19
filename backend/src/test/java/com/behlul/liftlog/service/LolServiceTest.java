package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.TrackPlayerRequest;
import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.exception.RiotIdNotFoundException;
import com.behlul.liftlog.repository.LolMatchPlayerRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.riot.FakeRiotClient;
import com.behlul.liftlog.riot.LolPlatform;
import com.behlul.liftlog.riot.RiotClient;
import com.behlul.liftlog.riot.RiotRateLimiter;
import com.behlul.liftlog.riot.RiotUnavailableException;
import com.behlul.liftlog.riot.RiotUnavailableException.Reason;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The refresh rules with Riot replaced by the fake client. A spy, so single answers (like "no profile on this
 * server", which the fake never gives on its own) can be swapped in per test.
 */
@ExtendWith(MockitoExtension.class)
class LolServiceTest {

    private static final long USER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 18, 0);

    @Mock private LolPlayerRepository playerRepository;
    @Mock private LolMatchPlayerRepository matchRepository;
    @Mock private RiotRateLimiter limiter;
    @Mock private CurrentUserProvider currentUser;

    private final RiotClient riot = spy(new FakeRiotClient());
    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private LolService service;

    @BeforeEach
    void setUp() {
        service = new LolService(playerRepository, matchRepository, riot, limiter, currentUser, clock);
        lenient().when(currentUser.currentUserId()).thenReturn(USER_ID);
    }

    /** A player whose data is {@code minutesOld} old, or never refreshed when null. */
    private static LolPlayer player(String puuid, Integer minutesOld) {
        LolPlayer player = new LolPlayer(puuid, "TR1", puuid, "TR1");
        if (minutesOld != null) {
            player.markRefreshed(NOW.minusMinutes(minutesOld));
        }
        return player;
    }

    private void everyClaimSucceeds() {
        when(playerRepository.claimForRefresh(anyString(), any())).thenAnswer(call -> Optional.of(call.getArgument(0)));
    }

    @Test
    void wrongServerIsRiotIdNotFound() {
        when(playerRepository.findById(anyString())).thenReturn(Optional.empty());
        everyClaimSucceeds();
        doReturn(Optional.empty()).when(riot).summoner(any(), anyString());

        assertThatThrownBy(() -> service.track(new TrackPlayerRequest("Wrong Server#TR1", "EUW1")))
                .isInstanceOf(RiotIdNotFoundException.class)
                .hasMessage("Riot ID Wrong Server#TR1 was not found on EUW1");
        verify(playerRepository, never()).track(anyLong(), anyString());
    }

    @Test
    void manualRefreshWithoutProfileIsRiotError() {
        LolPlayer moved = player("moved", 30);
        when(playerRepository.isVisibleTo(USER_ID, "moved")).thenReturn(true);
        when(playerRepository.findById("moved")).thenReturn(Optional.of(moved));
        everyClaimSucceeds();
        doReturn(Optional.empty()).when(riot).summoner(any(), anyString());

        assertThatThrownBy(() -> service.refreshNow("moved"))
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.RIOT_ERROR));
        assertThat(moved.getRefreshedAt()).isEqualTo(NOW.minusMinutes(30));
    }

    @Test
    void refreshesAtMostFourStalestFirst() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<LolPlayer> players = List.of(player("b", 30), player("fresh", 2), player("a", 60), player("never", null),
                player("d", 15), player("c", 20), player("e", 11));

        service.refreshSomeStale(players);

        InOrder order = inOrder(riot);
        for (String puuid : List.of("never", "a", "b", "c")) {
            order.verify(riot).summoner(LolPlatform.TR1, puuid);
        }
        verify(riot, times(4)).summoner(any(), anyString());
        verify(playerRepository, never()).claimForRefresh(eq("fresh"), any());
    }

    @Test
    void skipsPlayersAnotherRequestHoldsAndKeepsGoing() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        // Another viewer is refreshing the stalest player right now
        when(playerRepository.claimForRefresh(eq("never"), any())).thenReturn(Optional.empty());
        List<LolPlayer> players = List.of(player("never", null), player("a", 60), player("b", 50), player("c", 40),
                player("d", 30), player("e", 20));

        service.refreshSomeStale(players);

        verify(riot, never()).summoner(LolPlatform.TR1, "never");
        for (String puuid : List.of("a", "b", "c", "d")) {
            verify(riot).summoner(LolPlatform.TR1, puuid);
        }
        verify(riot, never()).summoner(LolPlatform.TR1, "e");
    }

    @Test
    void keepsAReserveForActions() {
        List<LolPlayer> players = List.of(player("a", 60));
        // 26 left: one more refresh (13) could leave too little for a follow or link (14)
        when(limiter.remaining()).thenReturn(LolService.REQUESTS_PER_REFRESH + LolService.RESERVED_FOR_ACTIONS - 1);
        service.refreshSomeStale(players);
        verify(playerRepository, never()).claimForRefresh(anyString(), any());
        verify(riot, never()).summoner(any(), anyString());

        when(limiter.remaining()).thenReturn(LolService.REQUESTS_PER_REFRESH + LolService.RESERVED_FOR_ACTIONS);
        everyClaimSucceeds();
        service.refreshSomeStale(players);
        verify(riot).summoner(LolPlatform.TR1, "a");
    }

    @Test
    void stopsAtTheFirstRiotError() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        doThrow(new RiotUnavailableException(Reason.RATE_LIMITED, "budget used up"))
                .when(riot).summoner(LolPlatform.TR1, "a");

        service.refreshSomeStale(List.of(player("a", 60), player("b", 50), player("c", 40)));

        verify(riot, times(1)).summoner(any(), anyString());
    }

    @Test
    void staleFirstOrdersNeverRefreshedThenOldest() {
        List<LolPlayer> players = new ArrayList<>(List.of(player("recent", 11), player("fresh", 9), player("never", null),
                player("old", 120)));

        assertThat(LolService.staleFirst(players, NOW.minusMinutes(10)))
                .extracting(LolPlayer::getPuuid).containsExactly("never", "old", "recent");
    }
}
