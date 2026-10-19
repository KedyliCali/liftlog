package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LolMatchRepository extends JpaRepository<LolMatch, String> {

    /**
     * Stores the match unless it is already there; 1 when this call stored it. Two followed players from the same
     * game can be refreshed at the same moment: the second insert waits for the first transaction and then does
     * nothing, instead of failing (and rolling back) the second refresh on the primary key.
     */
    @Modifying
    @Query(value = """
            insert into lol_matches (match_id, platform, queue_id, game_mode, game_version, ended_at, duration_s)
            values (:matchId, :platform, :queueId, :gameMode, :gameVersion, :endedAt, :durationS)
            on conflict (match_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("matchId") String matchId, @Param("platform") String platform,
                       @Param("queueId") int queueId, @Param("gameMode") String gameMode,
                       @Param("gameVersion") String gameVersion, @Param("endedAt") LocalDateTime endedAt,
                       @Param("durationS") int durationS);

    /**
     * Claims the one download of the match's timeline; 1 for the caller that gets it. The row stays locked until
     * the claiming transaction ends, so a second viewer waits, gets 0 and then reads the stored frames; if the
     * download fails, the rollback releases the claim for the next viewer.
     */
    @Modifying
    @Query(value = "update lol_matches set timeline_fetched = true where match_id = :matchId and timeline_fetched = false",
            nativeQuery = true)
    int claimTimeline(@Param("matchId") String matchId);
}
