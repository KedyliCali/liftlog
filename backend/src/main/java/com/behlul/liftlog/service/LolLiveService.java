package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.RankResponse;
import com.behlul.liftlog.dto.LolLiveDtos.GroupLiveMember;
import com.behlul.liftlog.dto.LolLiveDtos.LiveBan;
import com.behlul.liftlog.dto.LolLiveDtos.LolGroupLive;
import com.behlul.liftlog.dto.LolLiveDtos.LolLiveGame;
import com.behlul.liftlog.entity.LolGroupMember;
import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.repository.LolGroupMemberRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.riot.LolPlatform;
import com.behlul.liftlog.riot.RiotClient;
import com.behlul.liftlog.riot.RiotClient.LiveGame;
import com.behlul.liftlog.riot.RiotClient.LiveParticipant;
import com.behlul.liftlog.riot.RiotRateLimiter;
import com.behlul.liftlog.riot.RiotUnavailableException;
import com.behlul.liftlog.riot.RiotUnavailableException.Reason;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Is this player in a game right now?", asked of Riot's spectator API on demand and cached in memory, since a live
 * game is only interesting for minutes and the shared budget is small. One answer about a game is stored for every
 * player in it, so group members playing together cost one request.
 *
 * <p>The cache lives in this instance's memory: a restart forgets it, which only costs one request per player.
 */
@Service
public class LolLiveService {

    private static final Logger log = LoggerFactory.getLogger(LolLiveService.class);

    /** The player page polls; within this, a repeated look is served from memory. */
    static final Duration PLAYER_MAX_AGE = Duration.ofSeconds(60);
    /** The group board is looked at less closely, so its answers may be a little older. */
    static final Duration GROUP_MAX_AGE = Duration.ofMinutes(2);
    /** When Riot cannot be asked, an answer up to this old is still better than none. */
    static final Duration STALE_MAX_AGE = Duration.ofMinutes(10);
    /** At most this many members are looked up for one board view; the rest wait for the next one. */
    static final int MAX_GROUP_LOOKUPS = 10;
    /**
     * A live lookup never takes the budget below what a refresh plus the actions reserve need, so polling live games
     * cannot starve refreshes, follows and links.
     */
    static final int LIVE_RESERVE = LolService.REQUESTS_PER_REFRESH + LolService.RESERVED_FOR_ACTIONS;
    /** Entries beyond this many trigger a sweep of expired ones, so the cache cannot grow without bound. */
    private static final int SWEEP_ABOVE = 500;

    /** @param game null when the player was not in a game */
    private record Cached(long fetchedAt, LiveGame game) {
    }

    private final Map<String, Cached> byPuuid = new ConcurrentHashMap<>();
    private final LolService lolService;
    private final LolPlayerRepository playerRepository;
    private final LolGroupMemberRepository memberRepository;
    private final RiotClient riot;
    private final RiotRateLimiter limiter;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public LolLiveService(LolService lolService, LolPlayerRepository playerRepository,
                          LolGroupMemberRepository memberRepository, RiotClient riot, RiotRateLimiter limiter,
                          CurrentUserProvider currentUser, Clock clock) {
        this.lolService = lolService;
        this.playerRepository = playerRepository;
        this.memberRepository = memberRepository;
        this.riot = riot;
        this.limiter = limiter;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * The player's live game, empty when they are not in one. At most one Riot request, and none when asked again
     * within {@link #PLAYER_MAX_AGE}. Throws (503) only when Riot cannot be asked and nothing recent is cached.
     */
    @Transactional(readOnly = true)
    public Optional<LolLiveGame> liveGame(String puuid) {
        LolPlayer player = lolService.loadVisible(puuid);
        Cached cached = fresh(puuid, PLAYER_MAX_AGE);
        if (cached == null) {
            if (limiter.remaining() <= LolService.RESERVED_FOR_ACTIONS) {
                cached = fresh(puuid, STALE_MAX_AGE);
                if (cached == null) {
                    throw new RiotUnavailableException(Reason.RATE_LIMITED,
                            "Riot request budget is low, try the live game again in a minute");
                }
            } else {
                cached = lookUp(LolPlatform.parse(player.getPlatform()).orElseThrow(), puuid);
            }
        }
        if (cached.game() == null) {
            return Optional.empty();
        }
        return Optional.of(toResponse(cached.game(), cached.fetchedAt()));
    }

    /**
     * Which linked members of the caller's group are in a game; empty when the caller is in no group. Members are
     * looked up only when their cached answer is older than {@link #GROUP_MAX_AGE}, stalest first, at most
     * {@link #MAX_GROUP_LOOKUPS} per view and never below {@link #LIVE_RESERVE}; the others are reported unknown.
     */
    @Transactional(readOnly = true)
    public Optional<LolGroupLive> groupLive() {
        return memberRepository.findById(currentUser.currentUserId()).map(me -> {
            List<LolGroupMember> linked = memberRepository.findByGroupIdOrderByJoinedAtAscUserIdAsc(me.getGroupId())
                    .stream().filter(m -> m.getPuuid() != null).toList();
            Map<String, LolPlayer> players = new HashMap<>();
            playerRepository.findAllById(linked.stream().map(LolGroupMember::getPuuid).toList())
                    .forEach(p -> players.put(p.getPuuid(), p));

            Map<String, Cached> answers = new HashMap<>();
            List<LolGroupMember> toAsk = new ArrayList<>();
            for (LolGroupMember member : linked) {
                Cached cached = fresh(member.getPuuid(), GROUP_MAX_AGE);
                if (cached != null) {
                    answers.put(member.getPuuid(), cached);
                } else if (players.containsKey(member.getPuuid())) {
                    toAsk.add(member);
                }
            }
            // Never asked first, then the oldest answers
            toAsk.sort(Comparator.comparingLong(m -> {
                Cached old = byPuuid.get(m.getPuuid());
                return old == null ? Long.MIN_VALUE : old.fetchedAt();
            }));
            int asked = 0;
            for (LolGroupMember member : toAsk) {
                // A member found in someone else's game moments ago needs no request of their own
                Cached cached = fresh(member.getPuuid(), GROUP_MAX_AGE);
                if (cached == null) {
                    if (asked == MAX_GROUP_LOOKUPS || limiter.remaining() <= LIVE_RESERVE) {
                        continue;
                    }
                    asked++;
                    try {
                        LolPlatform platform = LolPlatform.parse(players.get(member.getPuuid()).getPlatform()).orElseThrow();
                        cached = lookUp(platform, member.getPuuid());
                    } catch (RiotUnavailableException e) {
                        log.info("Live game of {} unknown for now: {}", member.getPuuid(), e.getMessage());
                        if (e.reason() != Reason.RIOT_ERROR) {
                            asked = MAX_GROUP_LOOKUPS; // key or budget: every other request would fail the same way
                        }
                        continue;
                    }
                }
                answers.put(member.getPuuid(), cached);
            }
            return new LolGroupLive(linked.stream().map(m -> groupMember(m, answers.get(m.getPuuid()))).toList());
        });
    }

    private static GroupLiveMember groupMember(LolGroupMember member, Cached answer) {
        if (answer == null) {
            return new GroupLiveMember(member.getUserId(), member.getPuuid(), null, null, null, null, null, null, null);
        }
        LiveGame game = answer.game();
        if (game == null) {
            return new GroupLiveMember(member.getUserId(), member.getPuuid(), false, null, null, null, null,
                    answer.fetchedAt(), null);
        }
        Integer championId = game.participants().stream().filter(p -> member.getPuuid().equals(p.puuid())).findFirst()
                .map(p -> (int) p.championId()).orElse(null);
        return new GroupLiveMember(member.getUserId(), member.getPuuid(), true, game.gameQueueConfigId(), game.gameMode(),
                game.gameStartTime(), game.gameLength(), answer.fetchedAt(), championId);
    }

    /** One spectator request; the answer is cached for the player and, when in a game, for everyone in it. */
    private Cached lookUp(LolPlatform platform, String puuid) {
        Optional<LiveGame> game = riot.activeGame(platform, puuid);
        Cached answer = new Cached(clock.millis(), game.orElse(null));
        sweepIfLarge();
        byPuuid.put(puuid, answer);
        game.ifPresent(g -> g.participants().stream()
                .map(LiveParticipant::puuid)
                .filter(p -> p != null && !p.isBlank())
                .forEach(p -> byPuuid.put(p, answer)));
        return answer;
    }

    private Cached fresh(String puuid, Duration maxAge) {
        Cached cached = byPuuid.get(puuid);
        return cached != null && clock.millis() - cached.fetchedAt() < maxAge.toMillis() ? cached : null;
    }

    private void sweepIfLarge() {
        if (byPuuid.size() > SWEEP_ABOVE) {
            long now = clock.millis();
            byPuuid.values().removeIf(c -> now - c.fetchedAt() >= STALE_MAX_AGE.toMillis());
        }
    }

    /** Ranks only from the player cache: asking Riot for ten ranks on every view would eat the budget. */
    private LolLiveGame toResponse(LiveGame game, long fetchedAt) {
        List<String> puuids = game.participants().stream().map(LiveParticipant::puuid).filter(p -> p != null && !p.isBlank())
                .toList();
        Map<String, RankResponse> ranks = new HashMap<>();
        for (LolPlayer known : playerRepository.findAllById(puuids)) {
            if (known.getSoloTier() != null) {
                ranks.put(known.getPuuid(), new RankResponse(known.getSoloTier(), known.getSoloDivision(),
                        known.getSoloLp(), known.getSoloWins(), known.getSoloLosses()));
            }
        }
        Set<String> visible = playerRepository.findVisiblePuuids(currentUser.currentUserId());
        List<LiveParticipant> participants = game.participants();
        return new LolLiveGame(game.gameId(), game.gameMode(), game.gameQueueConfigId(), game.gameStartTime(),
                game.gameLength(), fetchedAt,
                participants.stream().map(p -> {
                    String riotId = p.riotId() == null ? "" : p.riotId();
                    int hash = riotId.lastIndexOf('#');
                    String name = hash < 0 ? emptyToNull(riotId) : riotId.substring(0, hash);
                    String tag = hash < 0 ? null : riotId.substring(hash + 1);
                    List<Integer> spells = new ArrayList<>();
                    if (p.spell1Id() != null) {
                        spells.add(p.spell1Id());
                    }
                    if (p.spell2Id() != null) {
                        spells.add(p.spell2Id());
                    }
                    return new com.behlul.liftlog.dto.LolLiveDtos.LiveParticipant(p.puuid(), name, tag, p.teamId(),
                            (int) p.championId(), spells, p.keystone(), p.perks() == null ? null : p.perks().perkStyle(),
                            p.perks() == null ? null : p.perks().perkSubStyle(), ranks.get(p.puuid()),
                            p.puuid() != null && visible.contains(p.puuid()), Boolean.TRUE.equals(p.bot()));
                }).toList(),
                game.bannedChampions().stream().map(b -> new LiveBan(b.championId(), b.teamId())).toList());
    }

    private static String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
