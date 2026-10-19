package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolMatchDtos.LolTimeline;
import com.behlul.liftlog.dto.LolMatchDtos.TimelineMinute;
import com.behlul.liftlog.entity.LolMatch;
import com.behlul.liftlog.entity.LolMatchFrame;
import com.behlul.liftlog.exception.FieldValidationException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.LolMatchFrameRepository;
import com.behlul.liftlog.repository.LolMatchParticipantRepository;
import com.behlul.liftlog.repository.LolMatchRepository;
import com.behlul.liftlog.repository.LolMatchTeamRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.riot.FakeRiotClient;
import com.behlul.liftlog.riot.LolPlatform;
import com.behlul.liftlog.riot.RiotClient;
import com.behlul.liftlog.riot.RiotClient.Frame;
import com.behlul.liftlog.riot.RiotClient.ParticipantFrame;
import com.behlul.liftlog.riot.RiotUnavailableException;
import com.behlul.liftlog.riot.RiotUnavailableException.Reason;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The timeline's download-once rules and the stats filters' validation, with the repositories mocked. */
@ExtendWith(MockitoExtension.class)
class LolMatchServiceTest {

    private static final String MATCH_ID = "TR1_abc_480";

    @Mock private LolService lolService;
    @Mock private LolPlayerRepository playerRepository;
    @Mock private LolMatchRepository matchRepository;
    @Mock private LolMatchTeamRepository teamRepository;
    @Mock private LolMatchParticipantRepository participantRepository;
    @Mock private LolMatchFrameRepository frameRepository;
    @Mock private CurrentUserProvider currentUser;

    private final RiotClient riot = spy(new FakeRiotClient());
    private LolMatchService service;

    @BeforeEach
    void setUp() {
        service = new LolMatchService(lolService, playerRepository, matchRepository, teamRepository,
                participantRepository, frameRepository, riot, currentUser);
    }

    private void matchIsStored() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(
                new LolMatch(MATCH_ID, "TR1", 420, "CLASSIC", "14.20.1.2", LocalDateTime.of(2026, 10, 19, 12, 0), 1_800)));
    }

    @Test
    void theFirstViewerDownloadsAndStoresTheTimeline() {
        matchIsStored();
        when(matchRepository.claimTimeline(MATCH_ID)).thenReturn(1);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<LolMatchFrame>> saved = ArgumentCaptor.forClass((Class) List.class);
        when(frameRepository.saveAll(saved.capture())).thenAnswer(call -> call.getArgument(0));
        when(frameRepository.findByMatchIdOrderByMinuteAscParticipantIdAsc(MATCH_ID))
                .thenAnswer(call -> saved.getValue());

        Optional<LolTimeline> timeline = service.timeline(MATCH_ID);

        verify(riot).timeline(LolPlatform.TR1, MATCH_ID);
        assertThat(timeline).hasValueSatisfying(t -> {
            assertThat(t.participants()).hasSize(10);
            assertThat(t.minutes()).isNotEmpty();
            assertThat(t.participants().getFirst().at10()).isNotNull();
        });
    }

    @Test
    void laterViewersReadTheStoredFramesWithoutAskingRiot() {
        matchIsStored();
        when(matchRepository.claimTimeline(MATCH_ID)).thenReturn(0);
        when(frameRepository.findByMatchIdOrderByMinuteAscParticipantIdAsc(MATCH_ID))
                .thenReturn(List.of(new LolMatchFrame(MATCH_ID, 0, 1, 500, 0, 0, 1)));

        assertThat(service.timeline(MATCH_ID)).isPresent();
        verify(riot, never()).timeline(any(), anyString());
    }

    @Test
    void aTimelineRiotNoLongerHasIsEmptyAndStaysClaimed() {
        matchIsStored();
        doReturn(Optional.empty()).when(riot).timeline(LolPlatform.TR1, MATCH_ID);
        when(matchRepository.claimTimeline(MATCH_ID)).thenReturn(1).thenReturn(0);

        assertThat(service.timeline(MATCH_ID)).isEmpty();
        // The claim committed with no frames, so the next viewer gets the same answer without a Riot request
        assertThat(service.timeline(MATCH_ID)).isEmpty();
        verify(riot, times(1)).timeline(any(), anyString());
        verify(frameRepository, never()).saveAll(any());
    }

    @Test
    void riotBeingUnavailablePropagatesWithoutStoringAnything() {
        matchIsStored();
        when(matchRepository.claimTimeline(MATCH_ID)).thenReturn(1);
        doThrow(new RiotUnavailableException(Reason.RATE_LIMITED, "budget used up"))
                .when(riot).timeline(LolPlatform.TR1, MATCH_ID);

        // In the app the exception also rolls the claim back, so the next viewer tries again
        assertThatThrownBy(() -> service.timeline(MATCH_ID))
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.RATE_LIMITED));
        verify(frameRepository, never()).saveAll(any());
    }

    @Test
    void anUnknownMatchIsNotFoundBeforeAnyClaim() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.timeline(MATCH_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(matchRepository, never()).claimTimeline(anyString());
    }

    @Test
    void framesAreNumberedByTheirPlaceAndSummedPerTeam() {
        // Riot's last frame lands mid-minute (2:05); rounding timestamps would not keep minutes apart in general
        RiotClient.Timeline timeline = new RiotClient.Timeline(MATCH_ID, List.of(
                new Frame(0, Map.of("1", new ParticipantFrame(500, 0, 0, 0, 1), "6", new ParticipantFrame(500, 0, 0, 0, 1))),
                new Frame(60_021, Map.of("1", new ParticipantFrame(700, 200, 4, 2, 2), "6", new ParticipantFrame(650, 180, 5, null, 1))),
                new Frame(125_000, Map.of("1", new ParticipantFrame(1_100, 500, 9, 3, 3), "6", new ParticipantFrame(900, 400, 10, 0, 2)))));

        List<LolMatchFrame> frames = LolMatchService.framesOf(MATCH_ID, timeline);
        assertThat(frames).extracting(LolMatchFrame::getMinute).containsExactlyInAnyOrder(0, 0, 1, 1, 2, 2);
        assertThat(frames).filteredOn(f -> f.getMinute() == 1 && f.getParticipantId() == 1)
                .singleElement().satisfies(f -> assertThat(f.getCs()).isEqualTo(6));

        LolTimeline result = LolMatchService.toTimeline(MATCH_ID,
                frames.stream().sorted(java.util.Comparator.comparingInt(LolMatchFrame::getMinute)
                        .thenComparingInt(LolMatchFrame::getParticipantId)).toList(),
                Map.of(1, 100, 6, 200));
        assertThat(result.minutes()).extracting(TimelineMinute::minute, TimelineMinute::blueGold, TimelineMinute::redGold)
                .containsExactly(tuple(0, 500, 500), tuple(1, 700, 650), tuple(2, 1_100, 900));
        assertThat(result.participants()).hasSize(2);
        assertThat(result.participants().getFirst().gold()).containsExactly(500, 700, 1_100);
        // A two-minute game has no 10 or 15 minute mark
        assertThat(result.participants().getFirst().at10()).isNull();
        assertThat(result.participants().getFirst().at15()).isNull();
    }

    @Test
    void statsRejectWindowsAndOffsetsOutsideTheAllowedValues() {
        assertThatThrownBy(() -> service.deepStats("p", LolQueueFilter.ALL, 30, 0))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.fieldErrors()).containsKey("games"));
        assertThatThrownBy(() -> service.deepStats("p", LolQueueFilter.ALL, 20, 841))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.fieldErrors()).containsKey("tzOffsetMinutes"));
        assertThatThrownBy(() -> service.deepStats("p", LolQueueFilter.ALL, 20, -721))
                .isInstanceOf(FieldValidationException.class);
        verify(lolService, never()).loadVisible(anyString());
    }
}
