package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.LolMatchDtos.LolMatchDetail;
import com.behlul.liftlog.dto.LolMatchDtos.LolTimeline;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.service.LolMatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stored League matches. Public game data: any signed-in user can open any stored match. */
@RestController
@RequestMapping("/api/v1/lol/matches")
public class LolMatchController {

    private final LolMatchService matchService;

    public LolMatchController(LolMatchService matchService) {
        this.matchService = matchService;
    }

    /** The full scoreboard; 404 when the match is not stored whole. */
    @GetMapping("/{matchId}")
    public LolMatchDetail detail(@PathVariable String matchId) {
        return matchService.matchDetail(matchId);
    }

    /**
     * Gold graph and lane differences; downloaded from Riot on the first request. 503 with the usual Riot reason
     * when Riot is unavailable, 404 when the match is not stored or Riot no longer has its timeline.
     */
    @GetMapping("/{matchId}/timeline")
    public LolTimeline timeline(@PathVariable String matchId) {
        // Thrown here, after the service's transaction committed, so "Riot no longer has it" stays remembered
        return matchService.timeline(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Timeline of League match", matchId));
    }
}
