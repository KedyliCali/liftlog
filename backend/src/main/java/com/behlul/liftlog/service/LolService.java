package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.LolMatchResponse;
import com.behlul.liftlog.dto.LolDtos.LolPlayerDetailResponse;
import com.behlul.liftlog.dto.LolDtos.LolPlayerResponse;
import com.behlul.liftlog.dto.LolDtos.RankResponse;
import com.behlul.liftlog.dto.LolDtos.TrackPlayerRequest;
import com.behlul.liftlog.entity.LolMatchPlayer;
import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.exception.ConflictException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.exception.RiotIdNotFoundException;
import com.behlul.liftlog.repository.LolMatchPlayerRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.riot.LolPlatform;
import com.behlul.liftlog.riot.RiotClient;
import com.behlul.liftlog.riot.RiotClient.LeagueEntry;
import com.behlul.liftlog.riot.RiotClient.Match;
import com.behlul.liftlog.riot.RiotClient.Summoner;
import com.behlul.liftlog.riot.RiotRateLimiter;
import com.behlul.liftlog.riot.RiotUnavailableException;
import com.behlul.liftlog.riot.RiotUnavailableException.Reason;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Follows League players for the current user. Riot data is cached and refreshed lazily: a player is
 * re-fetched when someone looks at them and the copy is older than {@link #STALE_AFTER}. When Riot cannot
 * answer, the cached copy is shown instead of an error.
 *
 * <p>Every refresh first claims the player's row (see {@link LolPlayerRepository#claimForRefresh}), so two
 * people looking at the same player at once never refresh them twice.
 */
@Service
public class LolService {

    private static final Logger log = LoggerFactory.getLogger(LolService.class);

    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    /** A manual refresh within this time of the last one is ignored rather than spending requests. */
    static final Duration MIN_MANUAL_REFRESH = Duration.ofMinutes(1);
    static final int MATCHES_SHOWN = 20;
    /** Caps one refresh at about a dozen requests, so one player cannot use up the shared budget. */
    static final int MAX_NEW_MATCHES_PER_REFRESH = 10;
    /** Requests a full refresh may need (profile, rank, match list and new matches), checked before starting. */
    static final int REQUESTS_PER_REFRESH = 3 + MAX_NEW_MATCHES_PER_REFRESH;
    /**
     * One page view makes at most this many refresh attempts, stalest first; the rest show cached data. Every
     * attempt that reached Riot counts, including one that found no profile or failed, since each spent requests.
     */
    static final int MAX_REFRESHES_PER_VIEW = 4;
    /** Left for actions after background refreshes: one account lookup plus one refresh (a follow or a link). */
    static final int RESERVED_FOR_ACTIONS = 1 + REQUESTS_PER_REFRESH;
    static final int MAX_TRACKED = 20;
    static final String SOLO_QUEUE = "RANKED_SOLO_5x5";

    private enum RefreshOutcome {
        /** Fresh already, or another request is refreshing the player right now. */
        SKIPPED,
        REFRESHED,
        /** Riot has no League profile for the player on their server; nothing was changed. */
        NO_PROFILE
    }

    private final LolPlayerRepository playerRepository;
    private final LolMatchPlayerRepository matchRepository;
    private final RiotClient riot;
    private final RiotRateLimiter limiter;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public LolService(LolPlayerRepository playerRepository, LolMatchPlayerRepository matchRepository, RiotClient riot,
                      RiotRateLimiter limiter, CurrentUserProvider currentUser, Clock clock) {
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.riot = riot;
        this.limiter = limiter;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Looks the Riot ID up and follows it. Needs Riot to answer, since there is nothing cached yet. */
    @Transactional
    public LolPlayerResponse track(TrackPlayerRequest request) {
        Long userId = currentUser.currentUserId();
        if (playerRepository.countTrackedBy(userId) >= MAX_TRACKED) {
            throw new ConflictException("You can follow at most " + MAX_TRACKED + " players");
        }
        LolPlayer player = findAndCache(request);
        playerRepository.track(userId, player.getPuuid());
        return toResponse(player);
    }

    /**
     * Looks a Riot ID up and stores the player in the shared cache, refreshing them if stale. Runs in the
     * caller's transaction, so a failure also undoes the caller's own changes (a follow or a group link).
     */
    @Transactional
    public LolPlayer findAndCache(TrackPlayerRequest request) {
        LolPlatform platform = LolPlatform.parse(request.platform()).orElseThrow();
        int hash = request.riotId().lastIndexOf('#');
        String gameName = request.riotId().substring(0, hash).trim();
        String tagLine = request.riotId().substring(hash + 1).trim();

        RiotClient.Account account = riot.findAccount(platform, gameName, tagLine)
                .orElseThrow(() -> new RiotIdNotFoundException(gameName + "#" + tagLine, platform.name()));

        LolPlayer player = playerRepository.findById(account.puuid())
                .orElseGet(() -> new LolPlayer(account.puuid(), platform.name(), account.gameName(), account.tagLine()));
        player.rename(account.gameName(), account.tagLine());
        if (!player.getPlatform().equals(platform.name())) {
            // A PUUID stays the same across a server transfer, so a cached player asked for on another server has
            // most likely moved there. Moving also makes them stale, so the claim below succeeds and the refresh
            // checks the requested server; if they have no profile there, the 404 below rolls the move back
            player.moveTo(platform.name());
        }
        // Stored before the refresh: its match rows reference the player
        playerRepository.saveAndFlush(player);
        // The account exists (account-v1 is regional) but has no profile on the chosen server: same answer as a
        // missing Riot ID, and the exception rolls the new row (or the move) back
        if (refreshIfStale(player, STALE_AFTER) == RefreshOutcome.NO_PROFILE) {
            throw new RiotIdNotFoundException(gameName + "#" + tagLine, platform.name());
        }
        return player;
    }

    /** The follow list, refreshing its stalest players while the request budget allows. */
    @Transactional
    public List<LolPlayerResponse> list() {
        List<LolPlayer> players = playerRepository.findTrackedBy(currentUser.currentUserId());
        refreshSomeStale(players);
        return players.stream().map(this::toResponse).toList();
    }

    @Transactional
    public LolPlayerDetailResponse detail(String puuid) {
        LolPlayer player = loadVisible(puuid);
        refreshSomeStale(List.of(player));
        return detailOf(player);
    }

    /** Pull-to-refresh: reports Riot problems, unlike the automatic refreshes. */
    @Transactional
    public LolPlayerDetailResponse refreshNow(String puuid) {
        LolPlayer player = loadVisible(puuid);
        if (refreshIfStale(player, MIN_MANUAL_REFRESH) == RefreshOutcome.NO_PROFILE) {
            throw new RiotUnavailableException(Reason.RIOT_ERROR,
                    "Riot has no League profile for this player on " + player.getPlatform());
        }
        return detailOf(player);
    }

    @Transactional
    public void untrack(String puuid) {
        if (playerRepository.untrack(currentUser.currentUserId(), puuid) == 0) {
            throw new ResourceNotFoundException("Followed player", puuid);
        }
    }

    /**
     * Background refresh for a page view: stalest players first, at most {@link #MAX_REFRESHES_PER_VIEW} refresh
     * attempts, and never below the reserve for actions. A refresh, a player with no profile on their server and
     * a Riot error for one player each count as an attempt; a player another request holds does not. Riot
     * problems are logged and the cached data is shown.
     */
    public void refreshSomeStale(Collection<LolPlayer> players) {
        int attempts = 0;
        for (LolPlayer player : staleFirst(players, now().minus(STALE_AFTER))) {
            if (attempts == MAX_REFRESHES_PER_VIEW || limiter.remaining() < REQUESTS_PER_REFRESH + RESERVED_FOR_ACTIONS) {
                return;
            }
            try {
                switch (refreshIfStale(player, STALE_AFTER)) {
                    case REFRESHED -> attempts++;
                    case NO_PROFILE -> {
                        attempts++;
                        log.info("Riot has no League profile for {} on {}; showing cached data",
                                player.getPuuid(), player.getPlatform());
                    }
                    // Another viewer holds this player; theirs is refreshed by them, so move on to the next one
                    case SKIPPED -> { }
                }
            } catch (RiotUnavailableException e) {
                if (e.reason() != Reason.RIOT_ERROR) {
                    // Key missing or rejected, or budget gone: every remaining player would fail the same way
                    log.info("Showing cached League data: {}", e.getMessage());
                    return;
                }
                // A Riot error can be about this player alone, so the next one still gets its turn
                attempts++;
                log.info("Could not refresh {}; showing cached data: {}", player.getPuuid(), e.getMessage());
            }
        }
    }

    /** Stale players only, never-refreshed ones first, then the oldest data. */
    static List<LolPlayer> staleFirst(Collection<LolPlayer> players, LocalDateTime staleBefore) {
        return players.stream()
                .filter(p -> p.getRefreshedAt() == null || p.getRefreshedAt().isBefore(staleBefore))
                .sorted(Comparator.comparing(LolPlayer::getRefreshedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private LolPlayer loadVisible(String puuid) {
        // A player only someone else follows looks exactly like a player that does not exist
        if (!playerRepository.isVisibleTo(currentUser.currentUserId(), puuid)) {
            throw new ResourceNotFoundException("League player", puuid);
        }
        return playerRepository.findById(puuid).orElseThrow(() -> new ResourceNotFoundException("League player", puuid));
    }

    /**
     * Refreshes the player when their data is older than {@code olderThan}. The claim locks their row until
     * this transaction ends, so only its holder downloads and stores their matches.
     */
    private RefreshOutcome refreshIfStale(LolPlayer player, Duration olderThan) {
        if (playerRepository.claimForRefresh(player.getPuuid(), now().minus(olderThan)).isEmpty()) {
            return RefreshOutcome.SKIPPED;
        }
        LolPlatform platform = LolPlatform.parse(player.getPlatform()).orElseThrow();
        Optional<Summoner> found = riot.summoner(platform, player.getPuuid());
        if (found.isEmpty()) {
            return RefreshOutcome.NO_PROFILE;
        }
        player.updateProfile(found.get().profileIconId(), found.get().summonerLevel());

        LeagueEntry solo = riot.leagueEntries(platform, player.getPuuid()).stream()
                .filter(e -> SOLO_QUEUE.equals(e.queueType())).findFirst().orElse(null);
        if (solo == null) {
            player.updateSoloRank(null, null, null, null, null);
        } else {
            player.updateSoloRank(solo.tier(), solo.rank(), solo.leaguePoints(), solo.wins(), solo.losses());
        }

        List<String> ids = riot.recentMatchIds(platform, player.getPuuid(), MATCHES_SHOWN);
        Set<String> stored = ids.isEmpty() ? Set.of() : matchRepository.findStoredIds(player.getPuuid(), ids);
        ids.stream().filter(id -> !stored.contains(id)).limit(MAX_NEW_MATCHES_PER_REFRESH)
                .forEach(id -> fetchMatch(platform, id).ifPresent(match -> storeMatch(match, player.getPuuid())));
        player.markRefreshed(now());
        playerRepository.save(player);
        return RefreshOutcome.REFRESHED;
    }

    /**
     * Empty when Riot no longer has the match or fails on this one match. Skipping it instead of failing the
     * refresh matters: a player whose refresh always failed would never be marked refreshed, so they would stay
     * first in line on every view. The skipped match is not stored, so the next refresh asks for it again.
     * Key and budget problems still propagate, since every other request would fail the same way.
     */
    private Optional<Match> fetchMatch(LolPlatform platform, String matchId) {
        try {
            Optional<Match> match = riot.match(platform, matchId);
            if (match.isEmpty()) {
                log.info("Riot no longer has match {}; skipping it", matchId);
            }
            return match;
        } catch (RiotUnavailableException e) {
            if (e.reason() != Reason.RIOT_ERROR) {
                throw e;
            }
            log.info("Skipping match {} for now: {}", matchId, e.getMessage());
            return Optional.empty();
        }
    }

    private void storeMatch(Match match, String puuid) {
        match.participants().stream().filter(p -> puuid.equals(p.puuid())).findFirst().ifPresent(p ->
                matchRepository.save(new LolMatchPlayer(match.matchId(), puuid, match.queueId(),
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(match.gameEndTimestamp()), ZoneOffset.UTC),
                        (int) match.gameDuration(), p.championName(), emptyToNull(p.teamPosition()), p.kills(), p.deaths(),
                        p.assists(), p.totalMinionsKilled() + p.neutralMinionsKilled(), p.totalDamageDealtToChampions(),
                        p.win())));
    }

    private LolPlayerDetailResponse detailOf(LolPlayer player) {
        List<LolMatchPlayer> matches = recentMatches(player.getPuuid());
        return new LolPlayerDetailResponse(toResponse(player, matches), LolStats.summarize(matches),
                matches.stream().map(LolMatchResponse::from).toList());
    }

    private List<LolMatchPlayer> recentMatches(String puuid) {
        return matchRepository.findByPuuidOrderByEndedAtDesc(puuid, PageRequest.of(0, MATCHES_SHOWN));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LolPlayerResponse toResponse(LolPlayer player) {
        return toResponse(player, recentMatches(player.getPuuid()));
    }

    private static LolPlayerResponse toResponse(LolPlayer player, List<LolMatchPlayer> matches) {
        RankResponse rank = player.getSoloTier() == null ? null : new RankResponse(player.getSoloTier(),
                player.getSoloDivision(), player.getSoloLp(), player.getSoloWins(), player.getSoloLosses());
        int wins = (int) matches.stream().filter(LolMatchPlayer::isWin).count();
        return new LolPlayerResponse(player.getPuuid(), player.getGameName(), player.getTagLine(), player.getPlatform(),
                player.getProfileIconId(), player.getSummonerLevel(), rank, matches.size(), wins, player.getRefreshedAt());
    }

    private static String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
