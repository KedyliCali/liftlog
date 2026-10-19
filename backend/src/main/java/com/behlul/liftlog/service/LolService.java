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
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Follows League players for the current user. Riot data is cached and refreshed lazily: a player is
 * re-fetched when someone looks at them and the copy is older than {@link #STALE_AFTER}. When Riot cannot
 * answer, the cached copy is shown instead of an error.
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
    static final int MAX_TRACKED = 20;
    static final String SOLO_QUEUE = "RANKED_SOLO_5x5";

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
        LolPlatform platform = LolPlatform.parse(request.platform()).orElseThrow();
        int hash = request.riotId().lastIndexOf('#');
        String gameName = request.riotId().substring(0, hash).trim();
        String tagLine = request.riotId().substring(hash + 1).trim();

        Long userId = currentUser.currentUserId();
        if (playerRepository.countTrackedBy(userId) >= MAX_TRACKED) {
            throw new ConflictException("You can follow at most " + MAX_TRACKED + " players");
        }
        RiotClient.Account account = riot.findAccount(platform, gameName, tagLine)
                .orElseThrow(() -> new RiotIdNotFoundException(gameName + "#" + tagLine, platform.name()));

        LolPlayer player = playerRepository.findById(account.puuid())
                .orElseGet(() -> new LolPlayer(account.puuid(), platform.name(), account.gameName(), account.tagLine()));
        player.rename(account.gameName(), account.tagLine());
        // Stored before the refresh: its match rows reference the player
        playerRepository.saveAndFlush(player);
        // The account exists (account-v1 is regional) but has no profile on the chosen server: same answer as a
        // missing Riot ID, and the exception rolls the new row back
        if (isStale(player) && !refresh(player)) {
            throw new RiotIdNotFoundException(gameName + "#" + tagLine, platform.name());
        }
        playerRepository.track(userId, player.getPuuid());
        return toResponse(player);
    }

    /** The follow list, refreshing stale players while the request budget allows. */
    @Transactional
    public List<LolPlayerResponse> list() {
        List<LolPlayer> players = playerRepository.findTrackedBy(currentUser.currentUserId());
        for (LolPlayer player : players) {
            refreshQuietly(player);
        }
        return players.stream().map(this::toResponse).toList();
    }

    @Transactional
    public LolPlayerDetailResponse detail(String puuid) {
        LolPlayer player = loadTracked(puuid);
        refreshQuietly(player);
        List<LolMatchPlayer> matches = recentMatches(puuid);
        return new LolPlayerDetailResponse(toResponse(player, matches), LolStats.summarize(matches),
                matches.stream().map(LolMatchResponse::from).toList());
    }

    /** Pull-to-refresh: reports Riot problems, unlike the automatic refreshes. */
    @Transactional
    public LolPlayerDetailResponse refreshNow(String puuid) {
        LolPlayer player = loadTracked(puuid);
        boolean recentlyRefreshed = player.getRefreshedAt() != null
                && player.getRefreshedAt().isAfter(now().minus(MIN_MANUAL_REFRESH));
        if (!recentlyRefreshed && !refresh(player)) {
            throw new RiotUnavailableException(Reason.RIOT_ERROR,
                    "Riot has no League profile for this player on " + player.getPlatform());
        }
        List<LolMatchPlayer> matches = recentMatches(puuid);
        return new LolPlayerDetailResponse(toResponse(player, matches), LolStats.summarize(matches),
                matches.stream().map(LolMatchResponse::from).toList());
    }

    @Transactional
    public void untrack(String puuid) {
        if (playerRepository.untrack(currentUser.currentUserId(), puuid) == 0) {
            throw new ResourceNotFoundException("Followed player", puuid);
        }
    }

    private LolPlayer loadTracked(String puuid) {
        // Someone else's follow looks exactly like a player that does not exist
        if (!playerRepository.isTrackedBy(currentUser.currentUserId(), puuid)) {
            throw new ResourceNotFoundException("Followed player", puuid);
        }
        return playerRepository.findById(puuid).orElseThrow(() -> new ResourceNotFoundException("Followed player", puuid));
    }

    private void refreshQuietly(LolPlayer player) {
        if (!isStale(player) || limiter.remaining() < REQUESTS_PER_REFRESH) {
            return;
        }
        try {
            if (!refresh(player)) {
                log.info("Riot has no League profile for {} on {}; showing cached data", player.getPuuid(),
                        player.getPlatform());
            }
        } catch (RiotUnavailableException e) {
            log.info("Showing cached League data for {}: {}", player.getPuuid(), e.getMessage());
        }
    }

    /** False when Riot has no League profile for the player on their server; nothing is changed then. */
    private boolean refresh(LolPlayer player) {
        LolPlatform platform = LolPlatform.parse(player.getPlatform()).orElseThrow();
        Optional<Summoner> found = riot.summoner(platform, player.getPuuid());
        if (found.isEmpty()) {
            return false;
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
                .forEach(id -> storeMatch(riot.match(platform, id), player.getPuuid()));
        player.markRefreshed(now());
        playerRepository.save(player);
        return true;
    }

    private void storeMatch(Match match, String puuid) {
        match.participants().stream().filter(p -> puuid.equals(p.puuid())).findFirst().ifPresent(p ->
                matchRepository.save(new LolMatchPlayer(match.matchId(), puuid, match.queueId(),
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(match.gameEndTimestamp()), ZoneOffset.UTC),
                        (int) match.gameDuration(), p.championName(), emptyToNull(p.teamPosition()), p.kills(), p.deaths(),
                        p.assists(), p.totalMinionsKilled() + p.neutralMinionsKilled(), p.totalDamageDealtToChampions(),
                        p.win())));
    }

    private List<LolMatchPlayer> recentMatches(String puuid) {
        return matchRepository.findByPuuidOrderByEndedAtDesc(puuid, PageRequest.of(0, MATCHES_SHOWN));
    }

    private boolean isStale(LolPlayer player) {
        return player.getRefreshedAt() == null || player.getRefreshedAt().isBefore(now().minus(STALE_AFTER));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LolPlayerResponse toResponse(LolPlayer player) {
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
