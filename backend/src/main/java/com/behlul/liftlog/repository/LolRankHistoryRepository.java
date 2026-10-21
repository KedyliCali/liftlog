package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolRankHistory;
import com.behlul.liftlog.service.LolRankQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LolRankHistoryRepository extends JpaRepository<LolRankHistory, LolRankHistory.Key> {

    /** Oldest first, the order the chart draws them in. */
    List<LolRankHistory> findByPuuidAndQueueOrderByRecordedAtAsc(String puuid, LolRankQueue queue);

    Optional<LolRankHistory> findFirstByPuuidAndQueueOrderByRecordedAtDesc(String puuid, LolRankQueue queue);

    /** The snapshots since the season started, for the season peak. */
    List<LolRankHistory> findByPuuidAndQueueAndRecordedAtGreaterThanEqual(String puuid, LolRankQueue queue,
                                                                         LocalDateTime since);

    /**
     * Deletes everything older than the player's newest {@code keep} rows of that queue. With fewer rows the
     * subquery finds nothing, the comparison is null and nothing is deleted.
     */
    @Modifying
    @Query(value = """
            delete from lol_rank_history
            where puuid = :puuid and queue = :queue
              and recorded_at < (select recorded_at from lol_rank_history where puuid = :puuid and queue = :queue
                                 order by recorded_at desc offset :keep - 1 limit 1)
            """, nativeQuery = true)
    int deleteAllButNewest(@Param("puuid") String puuid, @Param("queue") String queue, @Param("keep") int keep);
}
