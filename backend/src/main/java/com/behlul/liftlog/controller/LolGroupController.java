package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.LolDtos.GroupNameRequest;
import com.behlul.liftlog.dto.LolDtos.JoinGroupRequest;
import com.behlul.liftlog.dto.LolDtos.LolGroupResponse;
import com.behlul.liftlog.dto.LolDtos.TrackPlayerRequest;
import com.behlul.liftlog.dto.LolMatchDtos.GroupDuo;
import com.behlul.liftlog.dto.LolLiveDtos.LolGroupLive;
import com.behlul.liftlog.service.LolGroupService;
import com.behlul.liftlog.service.LolLiveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The caller's League group. A singular resource: a user is in at most one group, so group ids never
 * appear in URLs and cannot be guessed or enumerated.
 */
@RestController
@RequestMapping("/api/v1/lol/group")
public class LolGroupController {

    private final LolGroupService groupService;
    private final LolLiveService liveService;

    public LolGroupController(LolGroupService groupService, LolLiveService liveService) {
        this.groupService = groupService;
        this.liveService = liveService;
    }

    /** 204 when the caller is in no group, which is a normal state rather than an error. */
    @GetMapping
    public ResponseEntity<LolGroupResponse> myGroup() {
        return groupService.myGroup().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Members who played together on one team in stored matches, most games first; 204 outside a group. */
    @GetMapping("/duos")
    public ResponseEntity<List<GroupDuo>> duos() {
        return groupService.duos().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Which linked members are in a game right now (cached about two minutes, looked up within the Riot budget);
     * 204 outside a group.
     */
    @GetMapping("/live")
    public ResponseEntity<LolGroupLive> live() {
        return liveService.groupLive().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LolGroupResponse create(@Valid @RequestBody GroupNameRequest request) {
        return groupService.create(request);
    }

    @PostMapping("/join")
    public LolGroupResponse join(@Valid @RequestBody JoinGroupRequest request) {
        return groupService.join(request);
    }

    @PutMapping
    public LolGroupResponse rename(@Valid @RequestBody GroupNameRequest request) {
        return groupService.rename(request);
    }

    @PostMapping("/invite-code")
    public LolGroupResponse rotateInviteCode() {
        return groupService.rotateInviteCode();
    }

    @PutMapping("/me/account")
    public LolGroupResponse linkAccount(@Valid @RequestBody TrackPlayerRequest request) {
        return groupService.linkAccount(request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave() {
        groupService.leave();
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long userId) {
        groupService.removeMember(userId);
    }
}
