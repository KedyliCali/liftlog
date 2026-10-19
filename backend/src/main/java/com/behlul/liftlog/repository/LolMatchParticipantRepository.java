package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolMatchParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LolMatchParticipantRepository extends JpaRepository<LolMatchParticipant, LolMatchParticipant.Key> {

    List<LolMatchParticipant> findByMatchIdOrderByParticipantIdAsc(String matchId);

    List<LolMatchParticipant> findByMatchIdIn(Collection<String> matchIds);

    /** One player's own line in each of these matches; the match list reads their side (team) from it. */
    List<LolMatchParticipant> findByPuuidAndMatchIdIn(String puuid, Collection<String> matchIds);

    /** How often two of these players were on the same team, and how often that team won. */
    interface PairCount {
        String getPuuidA();

        String getPuuidB();

        long getGames();

        long getWins();
    }

    /**
     * Every pair of the given players who played together on one team in a stored match. Each pair once, the
     * smaller puuid first.
     */
    @Query(value = """
            select a.puuid as puuidA, b.puuid as puuidB, count(*) as games,
                   count(*) filter (where a.win) as wins
            from lol_match_participants a
            join lol_match_participants b
              on b.match_id = a.match_id and b.team_id = a.team_id and b.puuid > a.puuid
            where a.puuid in (:puuids) and b.puuid in (:puuids)
            group by a.puuid, b.puuid
            """, nativeQuery = true)
    List<PairCount> countPairs(@Param("puuids") Collection<String> puuids);
}
