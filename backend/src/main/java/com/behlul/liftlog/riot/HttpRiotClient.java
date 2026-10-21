package com.behlul.liftlog.riot;

import com.behlul.liftlog.riot.RiotUnavailableException.Reason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Calls the real Riot API. Every request takes a slot from the rate limiter first. */
public class HttpRiotClient implements RiotClient {

    private final RestClient restClient;
    private final RiotRateLimiter limiter;

    public HttpRiotClient(RestClient.Builder builder, String apiKey, RiotRateLimiter limiter) {
        // The key travels only in this header, server to Riot; it never reaches the phone
        this.restClient = builder.defaultHeader("X-Riot-Token", apiKey).build();
        this.limiter = limiter;
    }

    @Override
    public Optional<Account> findAccount(LolPlatform platform, String gameName, String tagLine) {
        return find(() -> restClient.get()
                .uri("https://{region}.api.riotgames.com/riot/account/v1/accounts/by-riot-id/{name}/{tag}",
                        platform.accountRegion(), gameName, tagLine)
                .retrieve().body(Account.class));
    }

    @Override
    public Optional<Summoner> summoner(LolPlatform platform, String puuid) {
        // A Riot ID picked with the wrong server has an account (account-v1 is regional) but no profile here
        return find(() -> restClient.get()
                .uri("https://{host}.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/{puuid}", platform.host(), puuid)
                .retrieve().body(Summoner.class));
    }

    @Override
    public List<LeagueEntry> leagueEntries(LolPlatform platform, String puuid) {
        LeagueEntry[] entries = call(() -> restClient.get()
                .uri("https://{host}.api.riotgames.com/lol/league/v4/entries/by-puuid/{puuid}", platform.host(), puuid)
                .retrieve().body(LeagueEntry[].class));
        return entries == null ? List.of() : List.of(entries);
    }

    @Override
    public List<String> recentMatchIds(LolPlatform platform, String puuid, int count) {
        String[] ids = call(() -> restClient.get()
                .uri("https://{region}.api.riotgames.com/lol/match/v5/matches/by-puuid/{puuid}/ids?start=0&count={count}",
                        platform.matchRegion(), puuid, count)
                .retrieve().body(String[].class));
        return ids == null ? List.of() : List.of(ids);
    }

    @Override
    public Optional<Match> match(LolPlatform platform, String matchId) {
        // A 404 is an answer ("that match is gone"). toEntity rather than body: find() would also turn a missing
        // body into empty, but a 200 without a match is Riot failing, not the match being gone
        Optional<ResponseEntity<MatchDto>> found = find(() -> restClient.get()
                .uri("https://{region}.api.riotgames.com/lol/match/v5/matches/{id}", platform.matchRegion(), matchId)
                .retrieve().toEntity(MatchDto.class));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MatchDto dto = found.get().getBody();
        if (dto == null || dto.info() == null) {
            throw new RiotUnavailableException(Reason.RIOT_ERROR, "Riot returned an empty match " + matchId);
        }
        MatchDto.Info info = dto.info();
        return Optional.of(new Match(dto.metadata().matchId(), info.queueId(), info.gameEndTimestamp(),
                info.gameDuration(), info.gameMode(), info.participants(), info.gameVersion(),
                info.teams() == null ? List.of() : info.teams()));
    }

    @Override
    public Optional<Timeline> timeline(LolPlatform platform, String matchId) {
        // Same rules as match(): a 404 means Riot no longer has it, a 200 without frames is Riot failing
        Optional<ResponseEntity<TimelineDto>> found = find(() -> restClient.get()
                .uri("https://{region}.api.riotgames.com/lol/match/v5/matches/{id}/timeline", platform.matchRegion(), matchId)
                .retrieve().toEntity(TimelineDto.class));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TimelineDto dto = found.get().getBody();
        if (dto == null || dto.info() == null || dto.info().frames() == null) {
            throw new RiotUnavailableException(Reason.RIOT_ERROR, "Riot returned an empty timeline " + matchId);
        }
        return Optional.of(new Timeline(matchId, dto.info().frames()));
    }

    @Override
    public List<ChampionMastery> championMasteries(LolPlatform platform, String puuid) {
        // A puuid Riot does not know on this server is a 404: no mastery to show, not a failure
        Optional<ChampionMastery[]> found = find(() -> restClient.get()
                .uri("https://{host}.api.riotgames.com/lol/champion-mastery/v5/champion-masteries/by-puuid/{puuid}",
                        platform.host(), puuid)
                .retrieve().body(ChampionMastery[].class));
        return found.map(List::of).orElse(List.of());
    }

    @Override
    public Optional<LiveGame> activeGame(LolPlatform platform, String puuid) {
        // A 404 is the everyday answer here: "not in a game right now"
        return find(() -> restClient.get()
                .uri("https://{host}.api.riotgames.com/lol/spectator/v5/active-games/by-summoner/{puuid}",
                        platform.host(), puuid)
                .retrieve().body(LiveGame.class))
                .map(game -> new LiveGame(game.gameId(), game.gameMode(), game.gameQueueConfigId(), game.gameStartTime(),
                        game.gameLength(), game.participants() == null ? List.of() : game.participants(),
                        game.bannedChampions() == null ? List.of() : game.bannedChampions()));
    }

    /** Riot nests the match id under "metadata" and everything else under "info". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MatchDto(Metadata metadata, Info info) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Metadata(String matchId) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Info(int queueId, long gameEndTimestamp, long gameDuration, String gameMode,
                            List<Participant> participants, String gameVersion, List<Team> teams) {
        }
    }

    /** The timeline nests its frames under "info" too. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TimelineDto(Info info) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Info(List<Frame> frames) {
        }
    }

    /** For lookups, where a 404 is an answer ("no such account or profile"), not a failure. */
    private <T> Optional<T> find(Supplier<T> request) {
        return call(() -> {
            try {
                return Optional.ofNullable(request.get());
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    private <T> T call(Supplier<T> request) {
        limiter.acquire();
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                throw new RiotUnavailableException(Reason.KEY_REJECTED, "Riot rejected the API key (expired or invalid)");
            }
            if (status.value() == 429) {
                throw new RiotUnavailableException(Reason.RATE_LIMITED, "Riot rate limit reached, try again shortly");
            }
            throw new RiotUnavailableException(Reason.RIOT_ERROR, "Riot request failed with " + status.value());
        } catch (RestClientException e) {
            throw new RiotUnavailableException(Reason.RIOT_ERROR, "Riot is not reachable right now");
        }
    }
}
