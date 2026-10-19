package com.behlul.liftlog.riot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;
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

    /** Per-minute frames of a match; empty when Riot no longer has it. Asked for only when a match page opens. */
    Optional<Timeline> timeline(LolPlatform platform, String matchId);

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
                 List<Participant> participants, String gameVersion, List<Team> teams) {
    }

    /** One side's result, bans and objectives; teamId 100 is blue, 200 red. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Team(int teamId, boolean win, List<Ban> bans, Objectives objectives) {
    }

    /** A champion key (numeric id), -1 for a skipped ban. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ban(Integer championId) {
    }

    /** Each one can be missing (atakhan only exists since 2025, horde since 2024); missing reads as none. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Objectives(Objective baron, Objective champion, Objective dragon, Objective horde, Objective inhibitor,
                      Objective riftHerald, Objective tower, Objective atakhan) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Objective(Boolean first, Integer kills) {
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
                       Integer summoner2Id, Challenges challenges,
                       // The whole-scoreboard numbers, added for full matches; nullable for the same reason
                       Integer participantId, String riotIdGameName, String riotIdTagline, Integer teamId,
                       Integer damageDealtToObjectives, Integer damageDealtToBuildings, Integer totalHeal,
                       Integer totalDamageShieldedOnTeammates, Integer timeCCingOthers, Integer totalTimeSpentDead,
                       Integer wardsKilled, Integer visionWardsBoughtInGame, Integer doubleKills, Integer tripleKills,
                       Integer quadraKills, Integer pentaKills, Boolean firstBloodKill, Boolean firstBloodAssist,
                       Perks perks) {

        /** The seven slots, the last being the trinket; a missing slot is empty (0), as Riot writes an empty one. */
        public List<Integer> items() {
            return Stream.of(item0, item1, item2, item3, item4, item5, item6).map(id -> id == null ? 0 : id).toList();
        }
    }

    /**
     * Riot's computed extras; absent in some modes, and each value can be missing on its own. The two fractions
     * are 0..1.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Challenges(Double killParticipation, Double teamDamagePercentage, Integer soloKills) {
    }

    /** Runes: styles[0] is the primary tree (its first selection is the keystone), styles[1] the secondary. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Perks(List<PerkStyle> styles) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PerkStyle(Integer style, List<PerkSelection> selections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PerkSelection(Integer perk) {
    }

    /** Oldest frame first; one a minute, the last one at the end of the game. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Timeline(String matchId, List<Frame> frames) {
    }

    /** @param participantFrames keyed by participant id as text ("1".."10"), as Riot sends it */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Frame(long timestamp, Map<String, ParticipantFrame> participantFrames) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ParticipantFrame(Integer totalGold, Integer xp, Integer minionsKilled, Integer jungleMinionsKilled,
                            Integer level) {
    }
}
