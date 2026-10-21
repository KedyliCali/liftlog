package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolChampionMastery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LolChampionMasteryRepository extends JpaRepository<LolChampionMastery, LolChampionMastery.Key> {

    List<LolChampionMastery> findByPuuidOrderByChampionPointsDesc(String puuid);

    // Not clearing the persistence context: the refresh that calls this still holds the player it is updating.
    // Mastery rows are never loaded during a refresh, so none can be left stale in it
    @Modifying(flushAutomatically = true)
    @Query("delete from LolChampionMastery m where m.puuid = :puuid")
    int deleteByPuuid(@Param("puuid") String puuid);
}
