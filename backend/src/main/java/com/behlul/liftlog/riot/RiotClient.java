package com.behlul.liftlog.riot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Optional;

/**
 * The few Riot API calls the tracker needs, as plain records. The service only sees this interface, so
 * tests and local development swap in {@link FakeRiotClient} without touching any HTTP code.
 */
public interface RiotClient {

    Optional<Account> findAccount(LolPlatform platform, String gameName, String tagLine);

    /** Empty when the account has no League profile on that server. */
    Optional<Summoner> summoner(LolPlatform platform, String puuid);

    List<LeagueEntry> leagueEntries(LolPlatform platform, String puuid);

    /** Newest first. */
    List<String> recentMatchIds(LolPlatform platform, String puuid, int count);

    /** Empty when Riot no longer has the match, although its match list may still name it. */
    Optional<Match> match(LolPlatform platform, String matchId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(String puuid, String gameName, String tagLine) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Summoner(int profileIconId, long summonerLevel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LeagueEntry(String queueType, String tier, String rank, int leaguePoints, int wins, int losses) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Match(String matchId, int queueId, long gameEndTimestamp, long gameDuration, List<Participant> participants) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Participant(String puuid, String championName, String teamPosition, int kills, int deaths, int assists,
                       int totalMinionsKilled, int neutralMinionsKilled, int totalDamageDealtToChampions, boolean win) {
    }
}
