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

    /** Which of these stored matches predate the detail columns, so a refresh can re-download them. */
    @Query("select m.matchId from LolMatchPlayer m where m.puuid = :puuid and m.matchId in :matchIds and m.goldEarned is null")
    Set<String> findIdsWithoutDetails(@Param("puuid") String puuid, @Param("matchIds") Collection<String> matchIds);
}
