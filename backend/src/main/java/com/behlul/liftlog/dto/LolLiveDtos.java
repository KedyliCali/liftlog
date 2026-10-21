package com.behlul.liftlog.dto;

import com.behlul.liftlog.dto.LolDtos.RankResponse;

import java.util.List;

/** Live games (spectator-v5), as the player page and the group board show them. */
public final class LolLiveDtos {

    private LolLiveDtos() {
    }

    /**
     * @param queueId       420 solo, 440 flex, ...; null for custom games
     * @param gameStartTime epoch milliseconds; 0 while the game is still loading
     * @param gameLengthS   seconds since the start when Riot was asked
     * @param fetchedAt     epoch milliseconds when Riot was asked; the web ticks the game clock on from these
     */
    public record LolLiveGame(long gameId, String gameMode, Integer queueId, long gameStartTime, long gameLengthS,
                              long fetchedAt, List<LiveParticipant> participants, List<LiveBan> bans) {
    }

    /**
     * @param championId Data Dragon's numeric champion key
     * @param spells     two summoner spell ids
     * @param rank       solo/duo rank when the player is already in the cache (followed or linked by anyone); no
     *                   extra Riot requests are made for the others, so it is null for them
     * @param tracked    whether the viewer can open this player's page
     */
    public record LiveParticipant(String puuid, String riotName, String riotTag, int teamId, int championId,
                                  List<Integer> spells, Integer keystone, Integer primaryStyle, Integer secondaryStyle,
                                  RankResponse rank, boolean tracked, boolean bot) {
    }

    /** championId -1 is a skipped ban. */
    public record LiveBan(int championId, int teamId) {
    }

    /** Each linked member of the caller's group and whether they are in a game. */
    public record LolGroupLive(List<GroupLiveMember> members) {
    }

    /**
     * @param inGame     null when it is not known: not asked this time because the shared Riot budget ran low
     * @param championId what the member plays in that game; the rest of the game fields are null when not in one
     */
    public record GroupLiveMember(Long userId, String puuid, Boolean inGame, Integer queueId, String gameMode,
                                  Long gameStartTime, Long gameLengthS, Long fetchedAt, Integer championId) {
    }
}
