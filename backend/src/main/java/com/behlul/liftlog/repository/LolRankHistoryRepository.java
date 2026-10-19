package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolRankHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LolRankHistoryRepository extends JpaRepository<LolRankHistory, LolRankHistory.Key> {

    /** Oldest first, the order the chart draws them in. */
    List<LolRankHistory> findByPuuidOrderByRecordedAtAsc(String puuid);

    Optional<LolRankHistory> findFirstByPuuidOrderByRecordedAtDesc(String puuid);

    /**
     * Deletes everything older than the player's newest {@code keep} rows. With fewer rows the subquery finds
     * nothing, the comparison is null and nothing is deleted.
     */
    @Modifying
    @Query(value = """
            delete from lol_rank_history
            where puuid = :puuid and recorded_at < (select recorded_at from lol_rank_history where puuid = :puuid
                                                   order by recorded_at desc offset :keep - 1 limit 1)
            """, nativeQuery = true)
    int deleteAllButNewest(@Param("puuid") String puuid, @Param("keep") int keep);
}
