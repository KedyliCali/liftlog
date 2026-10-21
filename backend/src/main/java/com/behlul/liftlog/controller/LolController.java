package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.LolDtos.LolPlayerDetailResponse;
import com.behlul.liftlog.dto.LolDtos.LolPlayerResponse;
import com.behlul.liftlog.dto.LolDtos.RankHistoryPoint;
import com.behlul.liftlog.dto.LolDtos.TrackPlayerRequest;
import com.behlul.liftlog.dto.LolDtos.LolMasteryResponse;
import com.behlul.liftlog.dto.LolLiveDtos.LolLiveGame;
import com.behlul.liftlog.dto.LolMatchDtos.LolDeepStats;
import com.behlul.liftlog.service.LolLiveService;
import com.behlul.liftlog.service.LolRankQueue;
import org.springframework.http.ResponseEntity;
import com.behlul.liftlog.service.LolMatchService;
import com.behlul.liftlog.service.LolQueueFilter;
import com.behlul.liftlog.service.LolService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lol/players")
public class LolController {

    private final LolService lolService;
    private final LolMatchService matchService;
    private final LolLiveService liveService;

    public LolController(LolService lolService, LolMatchService matchService, LolLiveService liveService) {
        this.lolService = lolService;
        this.matchService = matchService;
        this.liveService = liveService;
    }

    @GetMapping
    public List<LolPlayerResponse> list() {
        return lolService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LolPlayerResponse track(@Valid @RequestBody TrackPlayerRequest request) {
        return lolService.track(request);
    }

    @GetMapping("/{puuid}")
    public LolPlayerDetailResponse detail(@PathVariable String puuid) {
        return lolService.detail(puuid);
    }

    /**
     * Rank points of one ranked queue (SOLO, the default, or FLEX) for the LP chart, oldest first; a 404 for players
     * the detail page would hide.
     */
    @GetMapping("/{puuid}/rank-history")
    public List<RankHistoryPoint> rankHistory(@PathVariable String puuid,
                                              @RequestParam(defaultValue = "SOLO") LolRankQueue queue) {
        return lolService.rankHistory(puuid, queue);
    }

    /** Stored champion mastery (top champions and totals); never calls Riot. */
    @GetMapping("/{puuid}/mastery")
    public LolMasteryResponse mastery(@PathVariable String puuid) {
        return lolService.mastery(puuid);
    }

    /**
     * The game the player is in right now, or 204 when they are not in one. Cached for a minute, so polling it does
     * not spend Riot requests; 503 when Riot cannot be asked (budget used up) and nothing is cached.
     */
    @GetMapping("/{puuid}/live")
    public ResponseEntity<LolLiveGame> live(@PathVariable String puuid) {
        return liveService.liveGame(puuid).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Detailed stats over the newest {@code games} stored games in the queue; never calls Riot. 400 for a queue,
     * window or offset outside the allowed values, 404 for players the detail page would hide.
     *
     * @param tzOffsetMinutes the viewer's offset from UTC (180 for Türkiye), for the time-of-day and weekday charts
     */
    @GetMapping("/{puuid}/stats")
    public LolDeepStats stats(@PathVariable String puuid,
                              @RequestParam(defaultValue = "ALL") LolQueueFilter queue,
                              @RequestParam(defaultValue = "20") int games,
                              @RequestParam(defaultValue = "0") int tzOffsetMinutes) {
        return matchService.deepStats(puuid, queue, games, tzOffsetMinutes);
    }

    @PostMapping("/{puuid}/refresh")
    public LolPlayerDetailResponse refresh(@PathVariable String puuid) {
        return lolService.refreshNow(puuid);
    }

    @DeleteMapping("/{puuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untrack(@PathVariable String puuid) {
        lolService.untrack(puuid);
    }
}
