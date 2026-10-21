package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolLiveDtos.GroupLiveMember;
import com.behlul.liftlog.dto.LolLiveDtos.LolLiveGame;
import com.behlul.liftlog.entity.LolGroupMember;
import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.repository.LolGroupMemberRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.riot.LolPlatform;
import com.behlul.liftlog.riot.RiotClient;
import com.behlul.liftlog.riot.RiotClient.LiveGame;
import com.behlul.liftlog.riot.RiotClient.LiveParticipant;
import com.behlul.liftlog.riot.RiotClient.LivePerks;
import com.behlul.liftlog.riot.RiotRateLimiter;
import com.behlul.liftlog.riot.RiotUnavailableException;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LolLiveServiceTest {

    private static final long USER_ID = 7L;

    @Mock private LolService lolService;
    @Mock private LolPlayerRepository playerRepository;
    @Mock private LolGroupMemberRepository memberRepository;
    @Mock private RiotClient riot;
    @Mock private RiotRateLimiter limiter;
    @Mock private CurrentUserProvider currentUser;

    /** A clock the test moves forward. */
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-21T18:00:00Z"));
    private LolLiveService service;

    @BeforeEach
    void setUp() {
        service = new LolLiveService(lolService, playerRepository, memberRepository, riot, limiter, currentUser, clock);
        lenient().when(currentUser.currentUserId()).thenReturn(USER_ID);
        lenient().when(limiter.remaining()).thenReturn(90);
        lenient().when(playerRepository.findVisiblePuuids(USER_ID)).thenReturn(Set.of("a"));
    }

    private static LolPlayer player(String puuid) {
        LolPlayer player = new LolPlayer(puuid, "TR1", puuid, "TR1");
        player.updateSoloRank("GOLD", "II", 40, 10, 9);
        return player;
    }

    /** a and b on blue, eight strangers. */
    private static LiveGame gameWith(String... puuids) {
        List<LiveParticipant> participants = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String puuid = i < puuids.length ? puuids[i] : "stranger-" + i;
            participants.add(new LiveParticipant(puuid, "Name" + i + "#TR1", i < 5 ? 100 : 200, 100 + i, 4, 14, false,
                    new LivePerks(List.of(8112, 8126), 8100, 8300)));
        }
        return new LiveGame(99, "CLASSIC", 420, 1_760_000_000_000L, 300, participants,
                List.of(new RiotClient.LiveBan(157, 100, 1)));
    }

    @Test
    void aLiveGameIsMappedWithKnownRanksOnlyAndCachedForAMinute() {
        when(lolService.loadVisible("a")).thenReturn(player("a"));
        when(riot.activeGame(LolPlatform.TR1, "a")).thenReturn(Optional.of(gameWith("a", "b")));
        when(playerRepository.findAllById(any())).thenReturn(List.of(player("a")));

        LolLiveGame game = service.liveGame("a").orElseThrow();
        assertThat(game.queueId()).isEqualTo(420);
        assertThat(game.fetchedAt()).isEqualTo(clock.millis());
        assertThat(game.participants()).hasSize(10);
        var me = game.participants().getFirst();
        assertThat(me.riotName()).isEqualTo("Name0");
        assertThat(me.riotTag()).isEqualTo("TR1");
        assertThat(me.spells()).containsExactly(4, 14);
        assertThat(me.keystone()).isEqualTo(8112);
        assertThat(me.rank().tier()).isEqualTo("GOLD");
        assertThat(me.tracked()).isTrue();
        // Not in the cache: no rank, and no Riot request to find one
        assertThat(game.participants().get(1).rank()).isNull();
        assertThat(game.participants().get(1).tracked()).isFalse();
        assertThat(game.bans()).extracting(b -> b.championId()).containsExactly(157);

        clock.advance(Duration.ofSeconds(59));
        service.liveGame("a");
        verify(riot, times(1)).activeGame(any(), anyString());

        clock.advance(Duration.ofSeconds(2));
        service.liveGame("a");
        verify(riot, times(2)).activeGame(any(), anyString());
    }

    @Test
    void notInAGameIsEmptyAndAlsoCached() {
        when(lolService.loadVisible("a")).thenReturn(player("a"));
        when(riot.activeGame(LolPlatform.TR1, "a")).thenReturn(Optional.empty());

        assertThat(service.liveGame("a")).isEmpty();
        assertThat(service.liveGame("a")).isEmpty();
        verify(riot, times(1)).activeGame(any(), anyString());
    }

    @Test
    void aLowBudgetServesAnOlderAnswerOrFails() {
        when(lolService.loadVisible("a")).thenReturn(player("a"));
        when(riot.activeGame(LolPlatform.TR1, "a")).thenReturn(Optional.empty());
        service.liveGame("a");
        clock.advance(Duration.ofMinutes(5));
        when(limiter.remaining()).thenReturn(LolService.RESERVED_FOR_ACTIONS);

        // Five minutes old, but better than nothing while Riot cannot be asked
        assertThat(service.liveGame("a")).isEmpty();
        clock.advance(Duration.ofMinutes(6));
        assertThatThrownBy(() -> service.liveGame("a")).isInstanceOf(RiotUnavailableException.class);
        verify(riot, times(1)).activeGame(any(), anyString());
    }

    private void groupOf(String... puuids) {
        LolGroupMember me = new LolGroupMember(USER_ID, 1L, LocalDateTime.of(2026, 10, 1, 12, 0));
        List<LolGroupMember> members = new ArrayList<>();
        for (int i = 0; i < puuids.length; i++) {
            LolGroupMember member = i == 0 ? me : new LolGroupMember(USER_ID + i, 1L, LocalDateTime.of(2026, 10, 1, 12, i));
            if (puuids[i] != null) {
                member.linkAccount(puuids[i]);
            }
            members.add(member);
        }
        when(memberRepository.findById(USER_ID)).thenReturn(Optional.of(me));
        when(memberRepository.findByGroupIdOrderByJoinedAtAscUserIdAsc(1L)).thenReturn(members);
        List<LolPlayer> players = new ArrayList<>();
        for (String puuid : puuids) {
            if (puuid != null) {
                players.add(player(puuid));
            }
        }
        when(playerRepository.findAllById(any())).thenReturn(players);
    }

    @Test
    void membersPlayingTogetherCostOneRequestAndTheBoardIsCachedTwoMinutes() {
        groupOf("a", "b", "c", null);
        when(riot.activeGame(LolPlatform.TR1, "a")).thenReturn(Optional.of(gameWith("a", "b")));
        when(riot.activeGame(LolPlatform.TR1, "c")).thenReturn(Optional.empty());

        List<GroupLiveMember> members = service.groupLive().orElseThrow().members();

        // The member without an account is left out
        assertThat(members).extracting(GroupLiveMember::puuid).containsExactly("a", "b", "c");
        assertThat(members).extracting(GroupLiveMember::inGame).containsExactly(true, true, false);
        assertThat(members.get(1).championId()).isEqualTo(101);
        assertThat(members.get(0).queueId()).isEqualTo(420);
        // b was found in a's game: no request of their own
        verify(riot, never()).activeGame(LolPlatform.TR1, "b");

        clock.advance(Duration.ofSeconds(110));
        service.groupLive();
        verify(riot, times(2)).activeGame(any(), anyString());
    }

    @Test
    void aLowBudgetLeavesMembersUnknown() {
        groupOf("a", "b");
        when(limiter.remaining()).thenReturn(LolLiveService.LIVE_RESERVE);

        assertThat(service.groupLive().orElseThrow().members()).extracting(GroupLiveMember::inGame)
                .containsExactly(null, null);
        verify(riot, never()).activeGame(any(), anyString());
    }

    @Test
    void notInAGroupIsEmpty() {
        when(memberRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.groupLive()).isEmpty();
        verify(riot, never()).activeGame(any(), anyString());
        verify(memberRepository, never()).findByGroupIdOrderByJoinedAtAscUserIdAsc(anyLong());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
