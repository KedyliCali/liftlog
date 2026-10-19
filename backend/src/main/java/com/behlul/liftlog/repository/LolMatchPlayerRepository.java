package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolMatchPlayer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface LolMatchPlayerRepository extends JpaRepository<LolMatchPlayer, LolMatchPlayer.Key> {

    List<LolMatchPlayer> findByPuuidOrderByEndedAtDesc(String puuid, Pageable page);

    /** Which of these matches are already stored, so a refresh only downloads the new ones. */
    @Query("select m.matchId from LolMatchPlayer m where m.puuid = :puuid and m.matchId in :matchIds")
    Set<String> findStoredIds(@Param("puuid") String puuid, @Param("matchIds") Collection<String> matchIds);

    /**
     * Which of these stored matches a refresh should re-download: lines from before the detail columns, and
     * matches stored before whole matches were (no lol_matches row yet), so they gain teammates and opponents.
     */
    @Query("""
            select m.matchId from LolMatchPlayer m where m.puuid = :puuid and m.matchId in :matchIds
            and (m.goldEarned is null or not exists (select 1 from LolMatch x where x.matchId = m.matchId))
            """)
    Set<String> findIdsWithoutDetails(@Param("puuid") String puuid, @Param("matchIds") Collection<String> matchIds);
}
