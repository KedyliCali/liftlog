package com.behlul.liftlog.riot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
    record Match(String matchId, int queueId, long gameEndTimestamp, long gameDuration, String gameMode,
                 List<Participant> participants) {
    }

    /**
     * One player's line. The detail numbers (from goldEarned on) are nullable on purpose: Jackson refuses a match
     * with a missing primitive, and a mode that left one of them out must not stop the match from being stored
     * with the numbers it has.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Participant(String puuid, String championName, String teamPosition, int kills, int deaths, int assists,
                       int totalMinionsKilled, int neutralMinionsKilled, int totalDamageDealtToChampions, boolean win,
                       Integer goldEarned, Integer visionScore, Integer champLevel, Integer totalDamageTaken,
                       Integer largestMultiKill, Integer wardsPlaced, Integer item0, Integer item1, Integer item2,
                       Integer item3, Integer item4, Integer item5, Integer item6, Integer summoner1Id,
                       Integer summoner2Id, Challenges challenges) {

        /** The seven slots, the last being the trinket; a missing slot is empty (0), as Riot writes an empty one. */
        public List<Integer> items() {
            return Stream.of(item0, item1, item2, item3, item4, item5, item6).map(id -> id == null ? 0 : id).toList();
        }
    }

    /** Riot's computed extras; absent in some modes, and each value can be missing on its own. Both are 0..1. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Challenges(Double killParticipation, Double teamDamagePercentage) {
    }
}
