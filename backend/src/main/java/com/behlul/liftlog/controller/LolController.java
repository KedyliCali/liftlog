package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.LolDtos.LolPlayerDetailResponse;
import com.behlul.liftlog.dto.LolDtos.LolPlayerResponse;
import com.behlul.liftlog.dto.LolDtos.RankHistoryPoint;
import com.behlul.liftlog.dto.LolDtos.TrackPlayerRequest;
import com.behlul.liftlog.service.LolService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lol/players")
public class LolController {

    private final LolService lolService;

    public LolController(LolService lolService) {
        this.lolService = lolService;
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

    /** Solo/duo rank points for the LP chart, oldest first; a 404 for players the detail page would hide. */
    @GetMapping("/{puuid}/rank-history")
    public List<RankHistoryPoint> rankHistory(@PathVariable String puuid) {
        return lolService.rankHistory(puuid);
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
