package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolMatchTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LolMatchTeamRepository extends JpaRepository<LolMatchTeam, LolMatchTeam.Key> {

    List<LolMatchTeam> findByMatchIdOrderByTeamIdAsc(String matchId);

    List<LolMatchTeam> findByMatchIdIn(Collection<String> matchIds);
}
