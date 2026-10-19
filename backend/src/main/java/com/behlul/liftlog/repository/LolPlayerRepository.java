package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Players plus the follow list. The follow table is just (user, player) pairs, so it is reached with
 * plain SQL here instead of getting an entity of its own.
 */
public interface LolPlayerRepository extends JpaRepository<LolPlayer, String> {

    @Query(value = """
            select p.* from lol_players p
            join lol_tracked_players t on t.puuid = p.puuid
            where t.user_id = :userId
            order by t.added_at
            """, nativeQuery = true)
    List<LolPlayer> findTrackedBy(@Param("userId") Long userId);

    @Query(value = "select count(*) > 0 from lol_tracked_players where user_id = :userId and puuid = :puuid", nativeQuery = true)
    boolean isTrackedBy(@Param("userId") Long userId, @Param("puuid") String puuid);

    /**
     * Locks a stale player's row until this transaction ends, so only one request at a time refreshes them.
     * SKIP LOCKED: a row another request is refreshing is skipped, never waited for. NO KEY UPDATE: the key
     * never changes, so following this player or storing their matches elsewhere (foreign-key checks take KEY
     * SHARE) is not blocked. Must run inside a transaction, otherwise the lock ends immediately.
     */
    @Query(value = """
            select puuid from lol_players
            where puuid = :puuid and (refreshed_at is null or refreshed_at < :staleBefore)
            for no key update skip locked
            """, nativeQuery = true)
    Optional<String> claimForRefresh(@Param("puuid") String puuid, @Param("staleBefore") LocalDateTime staleBefore);

    @Query(value = "select count(*) from lol_tracked_players where user_id = :userId", nativeQuery = true)
    int countTrackedBy(@Param("userId") Long userId);

    @Modifying
    @Query(value = "insert into lol_tracked_players (user_id, puuid) values (:userId, :puuid) on conflict do nothing", nativeQuery = true)
    void track(@Param("userId") Long userId, @Param("puuid") String puuid);

    @Modifying
    @Query(value = "delete from lol_tracked_players where user_id = :userId and puuid = :puuid", nativeQuery = true)
    int untrack(@Param("userId") Long userId, @Param("puuid") String puuid);
}
