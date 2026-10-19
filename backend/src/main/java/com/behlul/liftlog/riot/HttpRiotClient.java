package com.behlul.liftlog.riot;

import com.behlul.liftlog.riot.RiotUnavailableException.Reason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatusCode;
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
    public Match match(LolPlatform platform, String matchId) {
        MatchDto dto = call(() -> restClient.get()
                .uri("https://{region}.api.riotgames.com/lol/match/v5/matches/{id}", platform.matchRegion(), matchId)
                .retrieve().body(MatchDto.class));
        if (dto == null || dto.info() == null) {
            throw new RiotUnavailableException(Reason.RIOT_ERROR, "Riot returned an empty match " + matchId);
        }
        return new Match(dto.metadata().matchId(), dto.info().queueId(), dto.info().gameEndTimestamp(),
                dto.info().gameDuration(), dto.info().participants());
    }

    /** Riot nests the match id under "metadata" and everything else under "info". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MatchDto(Metadata metadata, Info info) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Metadata(String matchId) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)

        private record Info(int queueId, long gameEndTimestamp, long gameDuration, List<Participant> participants) {
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
