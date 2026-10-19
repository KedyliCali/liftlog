package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.GroupNameRequest;
import com.behlul.liftlog.dto.LolDtos.JoinGroupRequest;
import com.behlul.liftlog.dto.LolDtos.TrackPlayerRequest;
import com.behlul.liftlog.entity.LolGroup;
import com.behlul.liftlog.entity.LolGroupMember;
import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.exception.ForbiddenException;
import com.behlul.liftlog.exception.LolGroupConflictException;
import com.behlul.liftlog.exception.LolGroupConflictException.Rule;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.LolGroupMemberRepository;
import com.behlul.liftlog.repository.LolGroupRepository;
import com.behlul.liftlog.repository.LolMatchParticipantRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LolGroupServiceTest {

    private static final long OWNER = 7L;
    private static final long FRIEND = 8L;
    private static final long GROUP_ID = 3L;
    private static final String CODE = "K7QXM2PFD9";

    @Mock private LolGroupRepository groupRepository;
    @Mock private LolGroupMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private LolPlayerRepository playerRepository;
    @Mock private LolMatchParticipantRepository participantRepository;
    @Mock private LolService lolService;
    @Mock private CurrentUserProvider currentUser;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneOffset.UTC);
    private final LolGroup group = new LolGroup("Duo Queue", CODE);
    private LolGroupService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        service = new LolGroupService(groupRepository, memberRepository, userRepository, playerRepository,
                participantRepository, lolService, currentUser, clock);
    }

    private static LolGroupMember member(long userId, long groupId) {
        return new LolGroupMember(userId, groupId, LocalDateTime.of(2026, 10, 19, 12, 0).plusMinutes(userId));
    }

    /** The caller is a member of the group, and the group row can be locked. */
    private LolGroupMember signedInMember(long userId) {
        LolGroupMember me = member(userId, GROUP_ID);
        when(currentUser.currentUserId()).thenReturn(userId);
        when(memberRepository.findById(userId)).thenReturn(Optional.of(me));
        when(groupRepository.findLockedById(GROUP_ID)).thenReturn(Optional.of(group));
        return me;
    }

    private void ownerIs(long userId) {
        when(memberRepository.findFirstByGroupIdOrderByJoinedAtAscUserIdAsc(GROUP_ID))
                .thenReturn(Optional.of(member(userId, GROUP_ID)));
    }

    private static Rule ruleOf(Throwable e) {
        return ((LolGroupConflictException) e).rule();
    }

    @Test
    void theBoardRefreshesItsLinkedPlayers() {
        LolGroupMember me = member(OWNER, GROUP_ID);
        me.linkAccount("p1");
        LolPlayer linked = new LolPlayer("p1", "TR1", "Pesimist Fidan", "2003");
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(memberRepository.findById(OWNER)).thenReturn(Optional.of(me));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        // The friend has not linked an account yet, so only the owner's player is refreshed
        when(memberRepository.findByGroupIdOrderByJoinedAtAscUserIdAsc(GROUP_ID))
                .thenReturn(List.of(me, member(FRIEND, GROUP_ID)));
        when(playerRepository.findAllById(List.of("p1"))).thenReturn(List.of(linked));

        assertThat(service.myGroup()).isPresent();

        verify(lolService).refreshSomeStale(List.of(linked));
    }

    @Test
    void creatingAGroupNeverRefreshesPlayers() {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(groupRepository.save(any())).thenAnswer(call -> {
            LolGroup saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", GROUP_ID);
            return saved;
        });

        service.create(new GroupNameRequest("Duo Queue"));

        verify(lolService, never()).refreshSomeStale(any());
    }

    @Test
    void joiningNeverRefreshesPlayers() {
        when(currentUser.currentUserId()).thenReturn(FRIEND);
        when(groupRepository.findLockedByInviteCode(CODE)).thenReturn(Optional.of(group));
        when(memberRepository.findById(FRIEND)).thenReturn(Optional.empty());

        service.join(new JoinGroupRequest(CODE));

        verify(memberRepository).save(any());
        verify(lolService, never()).refreshSomeStale(any());
    }

    @Test
    void joinRejectsAFullGroup() {
        when(currentUser.currentUserId()).thenReturn(FRIEND);
        when(groupRepository.findLockedByInviteCode(CODE)).thenReturn(Optional.of(group));
        when(memberRepository.findById(FRIEND)).thenReturn(Optional.empty());
        when(memberRepository.countByGroupId(GROUP_ID)).thenReturn((long) LolGroupService.MAX_MEMBERS);

        assertThatThrownBy(() -> service.join(new JoinGroupRequest("k7qxm-2pfd9")))
                .isInstanceOfSatisfying(LolGroupConflictException.class, e -> assertThat(e.rule()).isEqualTo(Rule.GROUP_FULL));
        verify(memberRepository, never()).save(any());
    }

    @Test
    void joiningYourOwnGroupAgainReturnsIt() {
        when(currentUser.currentUserId()).thenReturn(FRIEND);
        when(groupRepository.findLockedByInviteCode(CODE)).thenReturn(Optional.of(group));
        when(memberRepository.findById(FRIEND)).thenReturn(Optional.of(member(FRIEND, GROUP_ID)));

        assertThat(service.join(new JoinGroupRequest(CODE)).name()).isEqualTo("Duo Queue");
        verify(memberRepository, never()).save(any());
    }

    @Test
    void joiningWhileInAnotherGroupConflicts() {
        when(currentUser.currentUserId()).thenReturn(FRIEND);
        when(groupRepository.findLockedByInviteCode(CODE)).thenReturn(Optional.of(group));
        when(memberRepository.findById(FRIEND)).thenReturn(Optional.of(member(FRIEND, 99L)));

        assertThatThrownBy(() -> service.join(new JoinGroupRequest(CODE)))
                .satisfies(e -> assertThat(ruleOf(e)).isEqualTo(Rule.ALREADY_IN_GROUP));
        verify(memberRepository, never()).save(any());
    }

    @Test
    void malformedCodesAreNotFoundWithoutALookup() {
        assertThatThrownBy(() -> service.join(new JoinGroupRequest("O0I1L-XXXXX")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(groupRepository, never()).findLockedByInviteCode(any());
    }

    @Test
    void lastMemberLeavingDeletesTheGroup() {
        signedInMember(OWNER);
        when(memberRepository.deleteFromGroup(OWNER, GROUP_ID)).thenReturn(1);
        when(memberRepository.countByGroupId(GROUP_ID)).thenReturn(0L);

        service.leave();

        verify(groupRepository).delete(group);
    }

    @Test
    void leavingWithOthersLeftKeepsTheGroup() {
        signedInMember(OWNER);
        when(memberRepository.deleteFromGroup(OWNER, GROUP_ID)).thenReturn(1);
        when(memberRepository.countByGroupId(GROUP_ID)).thenReturn(2L);

        service.leave();

        verify(groupRepository, never()).delete(any());
    }

    @Test
    void onlyTheEarliestMemberIsOwner() {
        signedInMember(FRIEND);
        ownerIs(OWNER);

        assertThatThrownBy(() -> service.rename(new GroupNameRequest("Ranked")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.rotateInviteCode()).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.removeMember(OWNER)).isInstanceOf(ForbiddenException.class);
        assertThat(group.getName()).isEqualTo("Duo Queue");
        assertThat(group.getInviteCode()).isEqualTo(CODE);
    }

    @Test
    void ownerRenamesWithTheNameTrimmed() {
        signedInMember(OWNER);
        ownerIs(OWNER);

        assertThat(service.rename(new GroupNameRequest("  Ranked Gang ")).name()).isEqualTo("Ranked Gang");
    }

    @Test
    void ownerCannotRemoveThemselves() {
        signedInMember(OWNER);
        ownerIs(OWNER);

        assertThatThrownBy(() -> service.removeMember(OWNER))
                .satisfies(e -> assertThat(ruleOf(e)).isEqualTo(Rule.CANNOT_REMOVE_SELF));
        verify(memberRepository, never()).deleteFromGroup(anyLong(), anyLong());
    }

    @Test
    void removingAMemberReplacesTheInviteCode() {
        signedInMember(OWNER);
        ownerIs(OWNER);
        when(memberRepository.deleteFromGroup(FRIEND, GROUP_ID)).thenReturn(1);

        service.removeMember(FRIEND);

        assertThat(group.getInviteCode()).isNotEqualTo(CODE).matches("^[A-HJKMNP-Z2-9]{10}$");
    }

    @Test
    void removingSomeoneOutsideTheGroupIsNotFound() {
        signedInMember(OWNER);
        ownerIs(OWNER);
        when(memberRepository.deleteFromGroup(42L, GROUP_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.removeMember(42L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(group.getInviteCode()).isEqualTo(CODE);
    }

    @Test
    void linkingForANonMemberNeverCallsRiot() {
        when(currentUser.currentUserId()).thenReturn(FRIEND);
        when(memberRepository.findById(FRIEND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkAccount(new TrackPlayerRequest("Pesimist Fidan#2003", "TR1")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(lolService, never()).findAndCache(any());
    }

    @Test
    void linkingAfterBeingRemovedNeverCallsRiot() {
        signedInMember(FRIEND);
        // Removed by the owner between reading the membership and taking the lock
        when(memberRepository.existsByUserIdAndGroupId(FRIEND, GROUP_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.linkAccount(new TrackPlayerRequest("Pesimist Fidan#2003", "TR1")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(lolService, never()).findAndCache(any());
    }

    @Test
    void linkingAnAccountTakenByAnotherMemberConflicts() {
        LolGroupMember me = signedInMember(FRIEND);
        TrackPlayerRequest request = new TrackPlayerRequest("Pesimist Fidan#2003", "TR1");
        when(memberRepository.existsByUserIdAndGroupId(FRIEND, GROUP_ID)).thenReturn(true);
        when(lolService.findAndCache(request)).thenReturn(new LolPlayer("p1", "TR1", "Pesimist Fidan", "2003"));
        when(memberRepository.existsByGroupIdAndPuuidAndUserIdNot(GROUP_ID, "p1", FRIEND)).thenReturn(true);

        assertThatThrownBy(() -> service.linkAccount(request))
                .satisfies(e -> assertThat(ruleOf(e)).isEqualTo(Rule.ACCOUNT_TAKEN));
        assertThat(me.getPuuid()).isNull();
    }

    @Test
    void linkingStoresTheAccountOnTheMembership() {
        LolGroupMember me = signedInMember(FRIEND);
        TrackPlayerRequest request = new TrackPlayerRequest("Pesimist Fidan#2003", "TR1");
        when(memberRepository.existsByUserIdAndGroupId(FRIEND, GROUP_ID)).thenReturn(true);
        when(lolService.findAndCache(request)).thenReturn(new LolPlayer("p1", "TR1", "Pesimist Fidan", "2003"));

        service.linkAccount(request);

        assertThat(me.getPuuid()).isEqualTo("p1");
    }
}
