package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.GroupNameRequest;
import com.behlul.liftlog.dto.LolDtos.JoinGroupRequest;
import com.behlul.liftlog.dto.LolDtos.LolGroupMemberResponse;
import com.behlul.liftlog.dto.LolDtos.LolGroupResponse;
import com.behlul.liftlog.dto.LolDtos.LolPlayerResponse;
import com.behlul.liftlog.dto.LolDtos.TrackPlayerRequest;
import com.behlul.liftlog.entity.LolGroup;
import com.behlul.liftlog.entity.LolGroupMember;
import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.exception.ForbiddenException;
import com.behlul.liftlog.exception.LolGroupConflictException;
import com.behlul.liftlog.exception.LolGroupConflictException.Rule;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.LolGroupMemberRepository;
import com.behlul.liftlog.repository.LolGroupRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * League friend groups: LiftLog users who joined with the group's invite code, each linking the one Riot
 * account they play on. The board is built at read time from the shared player cache, so every member sees
 * the same board and nobody has to follow anybody.
 *
 * <p>Every change to a group's members locks the group row first (a row lock, FOR NO KEY UPDATE on PostgreSQL,
 * which conflicts with itself), so changes within a group run one at a time: the member cap and "delete the group
 * when the last member leaves" stay exact.
 */
@Service
public class LolGroupService {

    /** A board is never bigger than a full follow list. */
    static final int MAX_MEMBERS = LolService.MAX_TRACKED;
    private static final int NEW_CODE_ATTEMPTS = 5;

    private final LolGroupRepository groupRepository;
    private final LolGroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final LolPlayerRepository playerRepository;
    private final LolService lolService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public LolGroupService(LolGroupRepository groupRepository, LolGroupMemberRepository memberRepository,
                           UserRepository userRepository, LolPlayerRepository playerRepository, LolService lolService,
                           CurrentUserProvider currentUser, Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.lolService = lolService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Empty when the caller is in no group. Apart from linking, the only group call that spends Riot requests. */
    @Transactional
    public Optional<LolGroupResponse> myGroup() {
        return memberRepository.findById(currentUser.currentUserId())
                .flatMap(member -> groupRepository.findById(member.getGroupId()))
                .map(group -> toResponse(group, true));
    }

    @Transactional
    public LolGroupResponse create(GroupNameRequest request) {
        Long me = currentUser.currentUserId();
        if (memberRepository.existsById(me)) {
            throw alreadyInGroup();
        }
        LolGroup group = groupRepository.save(new LolGroup(request.name().trim(), newInviteCode()));
        memberRepository.save(new LolGroupMember(me, group.getId(), now()));
        return toResponse(group, false);
    }

    /** Joining the group you are already in again returns it, so a repeated tap is harmless. */
    @Transactional
    public LolGroupResponse join(JoinGroupRequest request) {
        // Unknown, rotated and malformed codes all get the same answer
        LolGroup group = InviteCode.normalize(request.code())
                .flatMap(groupRepository::findLockedByInviteCode)
                .orElseThrow(() -> new ResourceNotFoundException("Invite code", request.code().trim()));
        Long me = currentUser.currentUserId();
        Optional<LolGroupMember> mine = memberRepository.findById(me);
        if (mine.isPresent()) {
            if (mine.get().getGroupId().equals(group.getId())) {
                return toResponse(group, false);
            }
            throw alreadyInGroup();
        }
        if (memberRepository.countByGroupId(group.getId()) >= MAX_MEMBERS) {
            throw new LolGroupConflictException(Rule.GROUP_FULL, "This group is full (" + MAX_MEMBERS + " members)");
        }
        memberRepository.save(new LolGroupMember(me, group.getId(), now()));
        return toResponse(group, false);
    }

    @Transactional
    public LolGroupResponse rename(GroupNameRequest request) {
        LolGroup group = lockMyGroupAsOwner();
        group.rename(request.name().trim());
        return toResponse(group, false);
    }

    /** The old code stops working when this commits; members stay in the group. */
    @Transactional
    public LolGroupResponse rotateInviteCode() {
        LolGroup group = lockMyGroupAsOwner();
        group.replaceInviteCode(newInviteCode());
        return toResponse(group, false);
    }

    /**
     * Links or replaces the caller's own Riot account. The group lock is held across the Riot lookup (about a
     * second), so the owner cannot remove this member halfway and two members cannot claim one account at once.
     */
    @Transactional
    public LolGroupResponse linkAccount(TrackPlayerRequest request) {
        Long me = currentUser.currentUserId();
        LolGroupMember member = membership(me);
        LolGroup group = lockGroupOf(member);
        // Asked again under the lock and before any Riot request: the owner may have removed us just now
        if (!memberRepository.existsByUserIdAndGroupId(me, group.getId())) {
            throw notInGroup(me);
        }
        LolPlayer player = lolService.findAndCache(request);
        if (memberRepository.existsByGroupIdAndPuuidAndUserIdNot(group.getId(), player.getPuuid(), me)) {
            throw new LolGroupConflictException(Rule.ACCOUNT_TAKEN, "Another member already linked this Riot account");
        }
        member.linkAccount(player.getPuuid());
        return toResponse(group, false);
    }

    /** The next-oldest member becomes owner by itself; the last member out deletes the group and its code. */
    @Transactional
    public void leave() {
        Long me = currentUser.currentUserId();
        LolGroup group = lockGroupOf(membership(me));
        if (memberRepository.deleteFromGroup(me, group.getId()) == 0) {
            return; // the owner removed us in the meantime
        }
        if (memberRepository.countByGroupId(group.getId()) == 0) {
            groupRepository.delete(group);
        }
    }

    /** The owner removes someone, and the invite code changes, because the removed member still knows it. */
    @Transactional
    public void removeMember(Long userId) {
        Long me = currentUser.currentUserId();
        LolGroup group = lockMyGroupAsOwner();
        if (userId.equals(me)) {
            throw new LolGroupConflictException(Rule.CANNOT_REMOVE_SELF, "Leave the group instead of removing yourself");
        }
        // Someone in another group and a user that does not exist get the same answer
        if (memberRepository.deleteFromGroup(userId, group.getId()) == 0) {
            throw new ResourceNotFoundException("Group member", userId);
        }
        group.replaceInviteCode(newInviteCode());
    }

    private LolGroupMember membership(Long me) {
        return memberRepository.findById(me).orElseThrow(() -> notInGroup(me));
    }

    /** Row lock (FOR NO KEY UPDATE) on the member's group; 404 when it was deleted in the meantime. */
    private LolGroup lockGroupOf(LolGroupMember member) {
        return groupRepository.findLockedById(member.getGroupId()).orElseThrow(() -> notInGroup(member.getUserId()));
    }

    private LolGroup lockMyGroupAsOwner() {
        Long me = currentUser.currentUserId();
        LolGroup group = lockGroupOf(membership(me));
        // The owner is computed from join order under the lock, so it cannot change while we act
        Long owner = memberRepository.findFirstByGroupIdOrderByJoinedAtAscUserIdAsc(group.getId())
                .map(LolGroupMember::getUserId).orElse(null);
        if (!me.equals(owner)) {
            throw new ForbiddenException("Only the group owner can do that");
        }
        return group;
    }

    /**
     * A fresh code nobody uses. A clash is practically impossible (8.2e14 codes); the UNIQUE constraint is the
     * backstop and would turn one into a 409.
     */
    private String newInviteCode() {
        for (int i = 0; i < NEW_CODE_ATTEMPTS; i++) {
            String code = InviteCode.generate(random);
            if (!groupRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not find a free invite code");
    }

    private LolGroupResponse toResponse(LolGroup group, boolean refreshStale) {
        Long me = currentUser.currentUserId();
        List<LolGroupMember> members = memberRepository.findByGroupIdOrderByJoinedAtAscUserIdAsc(group.getId());
        List<LolPlayer> players = playerRepository.findAllById(
                members.stream().map(LolGroupMember::getPuuid).filter(Objects::nonNull).toList());
        if (refreshStale) {
            lolService.refreshSomeStale(players);
        }
        // findAllById does not keep order, so players are looked up by puuid
        Map<String, LolPlayerResponse> playerByPuuid = new HashMap<>();
        for (LolPlayer player : players) {
            playerByPuuid.put(player.getPuuid(), lolService.toResponse(player));
        }
        // A loop instead of Collectors.toMap, which rejects the null display names
        Map<Long, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(members.stream().map(LolGroupMember::getUserId).toList())) {
            names.put(user.getId(), user.getDisplayName());
        }
        Long owner = members.isEmpty() ? null : members.getFirst().getUserId();
        return new LolGroupResponse(group.getName(), group.getInviteCode(), MAX_MEMBERS, members.stream()
                .map(m -> new LolGroupMemberResponse(m.getUserId(), names.get(m.getUserId()), m.getUserId().equals(me),
                        m.getUserId().equals(owner), m.getJoinedAt(),
                        m.getPuuid() == null ? null : playerByPuuid.get(m.getPuuid())))
                .toList());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static LolGroupConflictException alreadyInGroup() {
        return new LolGroupConflictException(Rule.ALREADY_IN_GROUP, "You are already in a League group; leave it first");
    }

    private static ResourceNotFoundException notInGroup(Long userId) {
        return new ResourceNotFoundException("League group membership of user", userId);
    }
}
