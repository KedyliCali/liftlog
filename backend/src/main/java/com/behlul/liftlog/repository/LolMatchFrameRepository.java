package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolMatchFrame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LolMatchFrameRepository extends JpaRepository<LolMatchFrame, LolMatchFrame.Key> {

    List<LolMatchFrame> findByMatchIdOrderByMinuteAscParticipantIdAsc(String matchId);

    boolean existsByMatchId(String matchId);
}
