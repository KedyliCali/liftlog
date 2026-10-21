package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.TrackPlayerRequest;
import com.behlul.liftlog.entity.LolMatchPlayer;
import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.entity.LolRankHistory;
import com.behlul.liftlog.exception.RiotIdNotFoundException;
import com.behlul.liftlog.entity.LolChampionMastery;
import com.behlul.liftlog.repository.LolChampionMasteryRepository;
import com.behlul.liftlog.repository.LolMatchPlayerRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.repository.LolRankHistoryRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    @Mock private LolRankHistoryRepository rankHistoryRepository;
    @Mock private LolMatchStore matchStore;
    @Mock private RiotRateLimiter limiter;
    @Mock private CurrentUserProvider currentUser;
    @Mock private LolChampionMasteryRepository masteryRepository;

    private static final java.time.LocalDate SEASON_START = java.time.LocalDate.of(2026, 1, 8);

    private final RiotClient riot = spy(new FakeRiotClient());
    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private LolService service;

    @BeforeEach
    void setUp() {
        service = new LolService(playerRepository, matchRepository, rankHistoryRepository, matchStore, riot, limiter,
                currentUser, clock, masteryRepository, SEASON_START);
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

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"RATE_LIMITED", "KEY_REJECTED", "NO_KEY"})
    void stopsAtOnceWhenTheKeyOrBudgetFails(Reason reason) {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        doThrow(new RiotUnavailableException(reason, "every request would fail"))
                .when(riot).summoner(LolPlatform.TR1, "a");

        service.refreshSomeStale(List.of(player("a", 60), player("b", 50), player("c", 40)));

        verify(riot, times(1)).summoner(any(), anyString());
    }

    @Test
    void aRiotErrorForOnePlayerMovesOnToTheNext() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        doThrow(new RiotUnavailableException(Reason.RIOT_ERROR, "Riot request failed with 500"))
                .when(riot).summoner(LolPlatform.TR1, "a");
        List<LolPlayer> players = List.of(player("a", 60), player("b", 50), player("c", 40), player("d", 30),
                player("e", 20), player("f", 15));

        service.refreshSomeStale(players);

        // The failed attempt still counts, so three more players are refreshed, not four
        assertThat(players).filteredOn(p -> NOW.equals(p.getRefreshedAt()))
                .extracting(LolPlayer::getPuuid).containsExactly("b", "c", "d");
        verify(riot, never()).summoner(LolPlatform.TR1, "e");
    }

    @Test
    void aPlayerWithoutProfileCountsAsAnAttemptWithoutStoppingTheLoop() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        doReturn(Optional.empty()).when(riot).summoner(LolPlatform.TR1, "moved");
        List<LolPlayer> players = List.of(player("moved", 60), player("b", 50), player("c", 40), player("d", 30),
                player("e", 20));

        service.refreshSomeStale(players);

        assertThat(players).filteredOn(p -> NOW.equals(p.getRefreshedAt()))
                .extracting(LolPlayer::getPuuid).containsExactly("b", "c", "d");
        verify(riot, never()).summoner(LolPlatform.TR1, "e");
    }

    @Test
    void aBrokenMatchIsSkippedAndThePlayerIsStillRefreshed() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        LolPlayer player = player("a", 60);
        // The fake hands out the same ids on every call, and two of the new ones break
        List<String> ids = riot.recentMatchIds(LolPlatform.TR1, "a", LolService.MATCH_IDS_REQUESTED);
        String gone = ids.get(0);
        String failing = ids.get(1);
        doReturn(Optional.empty()).when(riot).match(LolPlatform.TR1, gone);
        doThrow(new RiotUnavailableException(Reason.RIOT_ERROR, "Riot request failed with 500"))
                .when(riot).match(LolPlatform.TR1, failing);

        service.refreshSomeStale(List.of(player));

        ArgumentCaptor<LolMatchPlayer> saved = ArgumentCaptor.forClass(LolMatchPlayer.class);
        verify(matchRepository, times(LolService.MAX_NEW_MATCHES_PER_REFRESH - 2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(LolMatchPlayer::getMatchId).doesNotContain(gone, failing);
        assertThat(player.getRefreshedAt()).isEqualTo(NOW);
        verify(playerRepository).save(player);
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"RATE_LIMITED", "KEY_REJECTED", "NO_KEY"})
    void aKeyOrBudgetErrorOnOneMatchStillStopsTheBackgroundRefresh(Reason reason) {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        LolPlayer first = player("a", 60);
        // Only RIOT_ERROR is about one match; the budget running out partway through must not be skipped like it
        String failing = riot.recentMatchIds(LolPlatform.TR1, "a", LolService.MATCH_IDS_REQUESTED).get(2);
        doThrow(new RiotUnavailableException(reason, "every request would fail"))
                .when(riot).match(LolPlatform.TR1, failing);

        service.refreshSomeStale(List.of(first, player("b", 50), player("c", 40)));

        assertThat(first.getRefreshedAt()).isEqualTo(NOW.minusMinutes(60));
        verify(playerRepository, never()).save(any(LolPlayer.class));
        verify(riot, never()).summoner(LolPlatform.TR1, "b");
        verify(riot, never()).summoner(LolPlatform.TR1, "c");
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"RATE_LIMITED", "KEY_REJECTED", "NO_KEY"})
    void aKeyOrBudgetErrorOnOneMatchFailsTheManualRefresh(Reason reason) {
        LolPlayer player = player("a", 30);
        when(playerRepository.isVisibleTo(USER_ID, "a")).thenReturn(true);
        when(playerRepository.findById("a")).thenReturn(Optional.of(player));
        everyClaimSucceeds();
        String failing = riot.recentMatchIds(LolPlatform.TR1, "a", LolService.MATCH_IDS_REQUESTED).get(2);
        doThrow(new RiotUnavailableException(reason, "every request would fail"))
                .when(riot).match(LolPlatform.TR1, failing);

        // Reported as is (a 503 with this reason), not hidden behind a refresh that is missing matches
        assertThatThrownBy(() -> service.refreshNow("a"))
                .isInstanceOfSatisfying(RiotUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(reason));
        assertThat(player.getRefreshedAt()).isEqualTo(NOW.minusMinutes(30));
    }

    /** Like the real claim: only a row that is stale or was never refreshed can be claimed. */
    private void claimsFollowTheRow(LolPlayer player) {
        when(playerRepository.claimForRefresh(eq(player.getPuuid()), any())).thenAnswer(call -> {
            LocalDateTime staleBefore = call.getArgument(1);
            boolean stale = player.getRefreshedAt() == null || player.getRefreshedAt().isBefore(staleBefore);
            return stale ? Optional.of(player.getPuuid()) : Optional.empty();
        });
    }

    @Test
    void aCachedPlayerAskedForOnAnotherServerMovesThere() {
        // Fresh, so without the move the claim would skip them and they would stay on EUW1
        LolPlayer cached = new LolPlayer("moved", "EUW1", "Moved", "TAG");
        cached.markRefreshed(NOW.minusMinutes(2));
        when(playerRepository.findById(anyString())).thenReturn(Optional.of(cached));
        claimsFollowTheRow(cached);

        LolPlayer player = service.findAndCache(new TrackPlayerRequest("Moved#TAG", "TR1"));

        assertThat(player.getPlatform()).isEqualTo("TR1");
        assertThat(player.getRefreshedAt()).isEqualTo(NOW);
        verify(riot).summoner(LolPlatform.TR1, "moved");
        verify(riot, never()).summoner(eq(LolPlatform.EUW1), anyString());
    }

    @Test
    void aCachedPlayerAskedForOnAServerWithoutTheirProfileIsNotFound() {
        LolPlayer cached = new LolPlayer("stays", "TR1", "Stays", "TAG");
        cached.markRefreshed(NOW.minusMinutes(2));
        when(playerRepository.findById(anyString())).thenReturn(Optional.of(cached));
        claimsFollowTheRow(cached);
        doReturn(Optional.empty()).when(riot).summoner(LolPlatform.EUW1, "stays");

        // In the app this exception also rolls the transaction back, so the row stays on TR1
        assertThatThrownBy(() -> service.findAndCache(new TrackPlayerRequest("Stays#TAG", "EUW1")))
                .isInstanceOf(RiotIdNotFoundException.class)
                .hasMessage("Riot ID Stays#TAG was not found on EUW1");
        verify(riot).summoner(LolPlatform.EUW1, "stays");
    }

    /** A stored line from before the detail columns: every detail is null. */
    private static LolMatchPlayer oldRow(String matchId, String puuid) {
        return new LolMatchPlayer(matchId, puuid, 420, NOW.minusDays(1), 1_800, "Ahri", "MIDDLE", 5, 3, 7, 180, 20_000,
                true);
    }

    @Test
    void oldRowsWithoutDetailsAreRedownloadedWithWhatIsLeftOfTheBudget() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        LolPlayer player = player("a", 60);
        List<String> ids = riot.recentMatchIds(LolPlatform.TR1, "a", LolService.MATCH_IDS_REQUESTED);
        // Five new matches on top; the fifteen below are stored, all from before the details
        List<String> storedIds = ids.subList(5, ids.size());
        when(matchRepository.findStoredIds(eq("a"), any())).thenReturn(Set.copyOf(storedIds));
        when(matchRepository.findIdsWithoutDetails(eq("a"), any())).thenReturn(Set.copyOf(storedIds));
        Map<String, LolMatchPlayer> rows = new HashMap<>();
        storedIds.forEach(id -> rows.put(id, oldRow(id, "a")));
        when(matchRepository.findById(any())).thenAnswer(call ->
                Optional.ofNullable(rows.get(call.<LolMatchPlayer.Key>getArgument(0).matchId())));

        service.refreshSomeStale(List.of(player));

        // New matches first, then the newest old rows, ten downloads in all
        InOrder order = inOrder(riot);
        ids.subList(0, LolService.MAX_NEW_MATCHES_PER_REFRESH).forEach(id -> order.verify(riot).match(LolPlatform.TR1, id));
        verify(riot, times(LolService.MAX_NEW_MATCHES_PER_REFRESH)).match(any(), anyString());

        ArgumentCaptor<LolMatchPlayer> saved = ArgumentCaptor.forClass(LolMatchPlayer.class);
        verify(matchRepository, times(5)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(LolMatchPlayer::getMatchId).containsExactlyElementsOf(ids.subList(0, 5));
        assertThat(saved.getAllValues()).allSatisfy(m -> assertThat(m.getGoldEarned()).isNotNull());

        // The re-downloaded rows are updated in place, keeping their stored numbers; the rest wait for next time
        for (String id : ids.subList(5, 10)) {
            LolMatchPlayer row = rows.get(id);
            assertThat(row.getGoldEarned()).isNotNull();
            assertThat(row.getItems()).matches("\\d+(,\\d+){6}");
            assertThat(row.getSummonerSpells()).startsWith("4,");
            assertThat(row.getKillParticipation()).isBetween(new java.math.BigDecimal("0.3"), new java.math.BigDecimal("0.8"));
            assertThat(row.getGameMode()).isEqualTo("CLASSIC");
            assertThat(row.getKills()).isEqualTo(5);
        }
        assertThat(rows.get(ids.get(10)).getGoldEarned()).isNull();
        assertThat(player.getRefreshedAt()).isEqualTo(NOW);

        // Every downloaded match, new or re-downloaded, is also stored whole
        ArgumentCaptor<RiotClient.Match> whole = ArgumentCaptor.forClass(RiotClient.Match.class);
        verify(matchStore, times(LolService.MAX_NEW_MATCHES_PER_REFRESH)).storeIfAbsent(whole.capture(), eq(LolPlatform.TR1));
        assertThat(whole.getAllValues()).extracting(RiotClient.Match::matchId)
                .containsExactlyInAnyOrderElementsOf(ids.subList(0, LolService.MAX_NEW_MATCHES_PER_REFRESH));
    }

    @Test
    void asksForAHundredMatchIdsButDownloadsAtMostTenPerRefresh() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();

        service.refreshSomeStale(List.of(player("a", 60)));

        verify(riot).recentMatchIds(LolPlatform.TR1, "a", 100);
        verify(riot, times(LolService.MAX_NEW_MATCHES_PER_REFRESH)).match(any(), anyString());
        verify(matchStore, times(LolService.MAX_NEW_MATCHES_PER_REFRESH)).storeIfAbsent(any(), eq(LolPlatform.TR1));
    }

    @Test
    void noBackfillWhenNewMatchesUseTheWholeBudget() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<String> ids = riot.recentMatchIds(LolPlatform.TR1, "a", LolService.MATCH_IDS_REQUESTED);
        when(matchRepository.findStoredIds(eq("a"), any())).thenReturn(Set.copyOf(ids.subList(10, ids.size())));

        service.refreshSomeStale(List.of(player("a", 60)));

        verify(matchRepository, never()).findIdsWithoutDetails(anyString(), any());
        verify(riot, times(LolService.MAX_NEW_MATCHES_PER_REFRESH)).match(any(), anyString());
    }

    /** The history table as the service sees it: what it saved, newest last, both queues together. */
    private List<LolRankHistory> rankHistoryKeptInMemory() {
        List<LolRankHistory> history = new ArrayList<>();
        lenient().when(rankHistoryRepository.findFirstByPuuidAndQueueOrderByRecordedAtDesc(eq("a"), any()))
                .thenAnswer(call -> history.stream().filter(h -> h.getQueue() == call.getArgument(1))
                        .reduce((first, second) -> second));
        lenient().when(rankHistoryRepository.save(any())).thenAnswer(call -> {
            history.add(call.getArgument(0));
            return call.getArgument(0);
        });
        return history;
    }

    private void refreshWithRank(LolPlayer player, RiotClient.LeagueEntry... entries) {
        player.markRefreshed(NOW.minusMinutes(60));
        doReturn(List.of(entries)).when(riot).leagueEntries(LolPlatform.TR1, "a");
        service.refreshSomeStale(List.of(player));
    }

    private static RiotClient.LeagueEntry solo(String tier, String division, int lp, int wins, int losses) {
        return new RiotClient.LeagueEntry("RANKED_SOLO_5x5", tier, division, lp, wins, losses);
    }

    @Test
    void rankHistoryGetsARowOnlyWhenTheRankMovesInEachQueue() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<LolRankHistory> history = rankHistoryKeptInMemory();
        LolPlayer player = player("a", 60);
        RiotClient.LeagueEntry flex = new RiotClient.LeagueEntry("RANKED_FLEX_SR", "GOLD", "I", 10, 5, 5);

        refreshWithRank(player, flex, solo("SILVER", "II", 50, 10, 8));
        assertThat(player.getFlexTier()).isEqualTo("GOLD");
        assertThat(player.getFlexLp()).isEqualTo(10);
        // Same tier, division and LP: a game that changed nothing visible (wins move with every game)
        refreshWithRank(player, solo("SILVER", "II", 50, 11, 9));
        assertThat(player.getFlexTier()).isNull();
        assertThat(history).hasSize(2);

        refreshWithRank(player, solo("SILVER", "II", 68, 12, 9));
        // Unranked in solo records nothing there; flex did not move, so it records nothing either
        refreshWithRank(player, flex);
        assertThat(player.getSoloTier()).isNull();

        assertThat(history).filteredOn(h -> h.getQueue() == LolRankQueue.SOLO)
                .extracting(LolRankHistory::getLp).containsExactly(50, 68);
        assertThat(history).filteredOn(h -> h.getQueue() == LolRankQueue.FLEX)
                .extracting(LolRankHistory::getTier).containsExactly("GOLD");
        assertThat(history.getFirst().getRecordedAt()).isEqualTo(NOW);
        verify(rankHistoryRepository, times(2)).deleteAllButNewest("a", "SOLO", LolService.RANK_HISTORY_KEPT);
        verify(rankHistoryRepository, times(1)).deleteAllButNewest("a", "FLEX", LolService.RANK_HISTORY_KEPT);
    }

    /** One stored solo game of the player's, the only one after the previous snapshot. */
    private LolMatchPlayer gameBetweenSnapshots(boolean win, LocalDateTime previousSnapshot, LolMatchPlayer... more) {
        LolMatchPlayer game = new LolMatchPlayer("TR1_9", "a", 420, NOW.minusMinutes(20), 1_800, "Ahri", "MIDDLE",
                5, 3, 7, 180, 20_000, win);
        List<LolMatchPlayer> found = new ArrayList<>(List.of(game));
        found.addAll(List.of(more));
        when(matchRepository.findByPuuidAndQueueIdAndEndedAtAfter("a", 420, previousSnapshot)).thenReturn(found);
        return game;
    }

    private LocalDateTime previousSoloSnapshot(List<LolRankHistory> history, String tier, String division, int lp, int wins,
                                               int losses) {
        LocalDateTime at = NOW.minusHours(2);
        history.add(new LolRankHistory("a", LolRankQueue.SOLO, at, tier, division, lp, wins, losses));
        return at;
    }

    @Test
    void theOneGameBetweenTwoSnapshotsGetsTheLpDifferenceAcrossADivision() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<LolRankHistory> history = rankHistoryKeptInMemory();
        LocalDateTime before = previousSoloSnapshot(history, "GOLD", "IV", 90, 10, 8);
        LolMatchPlayer game = gameBetweenSnapshots(true, before);

        // GOLD IV 90 → GOLD III 15 after one win: +25, the LP carried over the division line
        refreshWithRank(player("a", 60), solo("GOLD", "III", 15, 11, 8));

        assertThat(game.getLpChange()).isEqualTo(25);
    }

    @Test
    void aLossThatDemotesIsNegative() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<LolRankHistory> history = rankHistoryKeptInMemory();
        LocalDateTime before = previousSoloSnapshot(history, "PLATINUM", "IV", 10, 40, 40);
        LolMatchPlayer game = gameBetweenSnapshots(false, before);

        // PLATINUM IV 10 → GOLD I 85 after one loss: down a tier, −25
        refreshWithRank(player("a", 60), solo("GOLD", "I", 85, 40, 41));

        assertThat(game.getLpChange()).isEqualTo(-25);
    }

    @Test
    void twoGamesBetweenSnapshotsGiveNoLpChange() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<LolRankHistory> history = rankHistoryKeptInMemory();
        previousSoloSnapshot(history, "GOLD", "IV", 50, 10, 8);

        // Two more games counted (12 + 8 = 20 against 18): whatever moved cannot be split between them
        refreshWithRank(player("a", 60), solo("GOLD", "IV", 90, 12, 8));

        verify(matchRepository, never()).findByPuuidAndQueueIdAndEndedAtAfter(anyString(), anyInt(), any());
    }

    @Test
    void noLpChangeWithoutExactlyOneMatchingStoredGame() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<LolRankHistory> history = rankHistoryKeptInMemory();
        LocalDateTime before = previousSoloSnapshot(history, "GOLD", "IV", 50, 10, 8);
        LolMatchPlayer other = new LolMatchPlayer("TR1_8", "a", 420, NOW.minusMinutes(70), 1_800, "Lux", "MIDDLE",
                1, 1, 1, 100, 10_000, true);
        LolMatchPlayer game = gameBetweenSnapshots(true, before, other);

        // One game counted, but two stored after the snapshot: which one it was is unknown
        refreshWithRank(player("a", 60), solo("GOLD", "IV", 72, 11, 8));
        assertThat(game.getLpChange()).isNull();
        assertThat(other.getLpChange()).isNull();
    }

    @Test
    void noLpChangeWhenTheStoredResultContradictsTheCounts() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<LolRankHistory> history = rankHistoryKeptInMemory();
        LocalDateTime before = previousSoloSnapshot(history, "GOLD", "IV", 50, 10, 8);
        // The counts say a win, the stored game is a loss: some other game was counted
        LolMatchPlayer game = gameBetweenSnapshots(false, before);

        refreshWithRank(player("a", 60), solo("GOLD", "IV", 72, 11, 8));

        assertThat(game.getLpChange()).isNull();
    }

    @Test
    void masteryIsStoredOnTheFirstRefreshTopFiftyWithTotalsOverAll() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<RiotClient.ChampionMastery> sixty = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            sixty.add(new RiotClient.ChampionMastery(i + 1, 2, 1_000L * (i + 1), NOW.toInstant(ZoneOffset.UTC).toEpochMilli()));
        }
        doReturn(sixty).when(riot).championMasteries(LolPlatform.TR1, "a");
        LolPlayer player = player("a", 60);

        service.refreshSomeStale(List.of(player));

        org.mockito.InOrder order = inOrder(masteryRepository);
        order.verify(masteryRepository).deleteByPuuid("a");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LolChampionMastery>> saved = ArgumentCaptor.forClass(List.class);
        order.verify(masteryRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(LolService.MASTERY_KEPT);
        // Most points first: champion 60 had the most
        assertThat(saved.getValue().getFirst().getChampionId()).isEqualTo(60);
        assertThat(saved.getValue().getFirst().getLastPlayTime()).isEqualTo(NOW);
        assertThat(player.getMasteryScore()).isEqualTo(120);
        assertThat(player.getMasteryPoints()).isEqualTo(1_830_000L);
        assertThat(player.getMasteryRefreshedAt()).isEqualTo(NOW);
    }

    @Test
    void masteryIsSkippedWhenNothingWasPlayedSinceARecentFetch() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        List<String> ids = riot.recentMatchIds(LolPlatform.TR1, "a", LolService.MATCH_IDS_REQUESTED);
        when(matchRepository.findStoredIds(eq("a"), any())).thenReturn(Set.copyOf(ids));
        LolPlayer player = player("a", 60);
        player.updateMastery(100, 1_000_000L, NOW.minusHours(3));

        service.refreshSomeStale(List.of(player));
        verify(riot, never()).championMasteries(any(), anyString());

        // A day later it is asked for anyway
        player.updateMastery(100, 1_000_000L, NOW.minus(LolService.MASTERY_MAX_AGE).minusMinutes(1));
        player.markRefreshed(NOW.minusMinutes(60));
        service.refreshSomeStale(List.of(player));
        verify(riot).championMasteries(LolPlatform.TR1, "a");
    }

    @Test
    void aMasteryRiotErrorKeepsTheOldMasteryAndStillRefreshes() {
        when(limiter.remaining()).thenReturn(90);
        everyClaimSucceeds();
        doThrow(new RiotUnavailableException(Reason.RIOT_ERROR, "boom")).when(riot).championMasteries(any(), anyString());
        LolPlayer player = player("a", 60);

        service.refreshSomeStale(List.of(player));

        verify(masteryRepository, never()).deleteByPuuid(anyString());
        assertThat(player.getRefreshedAt()).isEqualTo(NOW);
        assertThat(player.getMasteryRefreshedAt()).isNull();
    }

    @Test
    void theSeasonPeakIsTheHighestSnapshotSinceTheSeasonStarted() {
        LocalDateTime start = SEASON_START.atStartOfDay();
        when(rankHistoryRepository.findByPuuidAndQueueAndRecordedAtGreaterThanEqual("a", LolRankQueue.SOLO, start))
                .thenReturn(List.of(
                        new LolRankHistory("a", LolRankQueue.SOLO, start.plusDays(1), "GOLD", "I", 80, 1, 0),
                        new LolRankHistory("a", LolRankQueue.SOLO, start.plusDays(2), "PLATINUM", "IV", 20, 2, 0),
                        new LolRankHistory("a", LolRankQueue.SOLO, start.plusDays(3), "GOLD", "I", 99, 2, 1),
                        new LolRankHistory("a", LolRankQueue.SOLO, start.plusDays(4), "PLATINUM", "IV", 20, 3, 1)));

        var peak = service.seasonPeak("a", LolRankQueue.SOLO);

        assertThat(peak.tier()).isEqualTo("PLATINUM");
        assertThat(peak.lp()).isEqualTo(20);
        // Reached twice: the first time counts
        assertThat(peak.recordedAt()).isEqualTo(start.plusDays(2));
        assertThat(service.seasonPeak("a", LolRankQueue.FLEX)).isNull();
    }

    @Test
    void rankHistoryOfAPlayerYouCannotSeeIsNotFound() {
        when(playerRepository.isVisibleTo(USER_ID, "hidden")).thenReturn(false);

        assertThatThrownBy(() -> service.rankHistory("hidden", LolRankQueue.SOLO))
                .isInstanceOf(com.behlul.liftlog.exception.ResourceNotFoundException.class);
        verify(rankHistoryRepository, never()).findByPuuidAndQueueOrderByRecordedAtAsc(anyString(), any());
    }

    @Test
    void staleFirstOrdersNeverRefreshedThenOldest() {
        List<LolPlayer> players = new ArrayList<>(List.of(player("recent", 11), player("fresh", 9), player("never", null),
                player("old", 120)));

        assertThat(LolService.staleFirst(players, NOW.minusMinutes(10)))
                .extracting(LolPlayer::getPuuid).containsExactly("never", "old", "recent");
    }
}
