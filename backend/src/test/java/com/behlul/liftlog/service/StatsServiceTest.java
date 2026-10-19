package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.StatsDtos.MuscleStatsResponse;
import com.behlul.liftlog.dto.StatsDtos.PersonalRecordResponse;
import com.behlul.liftlog.dto.StatsDtos.ProgressPointResponse;
import com.behlul.liftlog.dto.StatsDtos.WeeklyVolumeResponse;
import com.behlul.liftlog.entity.Exercise;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.ExerciseRepository;
import com.behlul.liftlog.repository.ExerciseSetCount;
import com.behlul.liftlog.repository.SetFact;
import com.behlul.liftlog.repository.StatsRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final long USER_ID = 7L;
    // Friday 2026-10-16, so the current week starts on Monday 2026-10-12
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    @Mock private StatsRepository statsRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private CurrentUserProvider currentUser;

    private StatsService statsService;

    @BeforeEach
    void setUp() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        statsService = new StatsService(statsRepository, exerciseRepository, currentUser, CLOCK);
    }

    @Test
    void personalRecordSeparatesHeaviestSetFromBestEstimatedMax() {
        when(statsRepository.findAllFacts(USER_ID)).thenReturn(List.of(
                fact(1, "Squat", "2026-10-01", 1, "150"),   // heaviest, e1RM 150
                fact(1, "Squat", "2026-10-08", 5, "140"),   // e1RM 163.33 -> best estimate
                fact(2, "Bench Press", "2026-10-02", 5, "100")));

        List<PersonalRecordResponse> prs = statsService.personalRecords();

        assertThat(prs).extracting(PersonalRecordResponse::exerciseName).containsExactly("Bench Press", "Squat");
        PersonalRecordResponse squat = prs.get(1);
        assertThat(squat.heaviestWeightKg()).isEqualByComparingTo("150");
        assertThat(squat.heaviestReps()).isEqualTo(1);
        assertThat(squat.bestEstimated1RmKg()).isEqualByComparingTo("163.33");
        assertThat(squat.bestEstimated1RmDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(squat.totalSets()).isEqualTo(2);
    }

    @Test
    void heaviestTieIsBrokenByRepsThenEarliestDate() {
        when(statsRepository.findAllFacts(USER_ID)).thenReturn(List.of(
                fact(1, "Squat", "2026-10-01", 3, "140"),
                fact(1, "Squat", "2026-10-05", 5, "140"),
                fact(1, "Squat", "2026-10-09", 5, "140")));

        PersonalRecordResponse squat = statsService.personalRecords().getFirst();

        assertThat(squat.heaviestReps()).isEqualTo(5);
        assertThat(squat.heaviestDate()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void progressHasOnePointPerWorkoutDay() {
        when(exerciseRepository.findVisibleById(1L, USER_ID)).thenReturn(Optional.of(new Exercise("Squat", "Legs", null)));
        when(statsRepository.findFactsForExercise(USER_ID, 1L)).thenReturn(List.of(
                fact(1, "Squat", "2026-10-01", 5, "100"),
                fact(1, "Squat", "2026-10-01", 3, "110"),
                fact(1, "Squat", "2026-10-08", 5, "105")));

        List<ProgressPointResponse> points = statsService.progress(1L);

        assertThat(points).hasSize(2);
        ProgressPointResponse first = points.getFirst();
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(first.topWeightKg()).isEqualByComparingTo("110");
        assertThat(first.estimated1RmKg()).isEqualByComparingTo("121.00");
        assertThat(first.volumeKg()).isEqualByComparingTo("830"); // 500 + 330
    }

    @Test
    void progressForInvisibleExerciseIsNotFound() {
        when(exerciseRepository.findVisibleById(9L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statsService.progress(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void weeklyVolumeFillsEmptyWeeksWithZero() {
        LocalDate firstMonday = LocalDate.of(2026, 9, 28);
        when(statsRepository.findWeeklyVolume(USER_ID, firstMonday)).thenReturn(List.of(
                row(LocalDate.of(2026, 10, 5), "2500", 6)));

        List<WeeklyVolumeResponse> weeks = statsService.weeklyVolume(3);

        assertThat(weeks).extracting(WeeklyVolumeResponse::weekStart).containsExactly(
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12));
        assertThat(weeks).extracting(w -> w.volumeKg().intValue()).containsExactly(0, 2500, 0);
    }

    @Test
    void muscleWindowEndsTodayAndIncludesIt() {
        // Server date 2026-10-16: seven days back to 2026-10-10, both ends included
        when(statsRepository.findExerciseSetCounts(USER_ID, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 16)))
                .thenReturn(List.of(new ExerciseSetCount(1L, "My Curl", "Arms", 2)));

        MuscleStatsResponse week = statsService.muscles(7, null);

        assertThat(week.days()).isEqualTo(7);
        assertThat(week.from()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(week.to()).isEqualTo(LocalDate.of(2026, 10, 16));
        // A custom exercise has no stored muscles: the Arms fallback gives biceps and triceps half a set each
        assertThat(week.muscles()).extracting(m -> m.muscle() + "=" + m.sets())
                .containsExactly("BICEPS=1.0", "TRICEPS=1.0");
        assertThat(week.muscles().getFirst().exercises().getFirst().name()).isEqualTo("My Curl");
    }

    @Test
    void muscleWindowFollowsThePhoneDate() {
        LocalDate phoneToday = LocalDate.of(2026, 10, 17);

        MuscleStatsResponse month = statsService.muscles(30, phoneToday);

        assertThat(month.from()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(month.to()).isEqualTo(phoneToday);
        assertThat(month.muscles()).isEmpty();
    }

    private static SetFact fact(long exerciseId, String name, String date, int reps, String weight) {
        return new SetFact(null, exerciseId, name, LocalDate.parse(date), reps, new BigDecimal(weight));
    }

    private static StatsRepository.WeeklyVolumeRow row(LocalDate week, String volume, long sets) {
        return new StatsRepository.WeeklyVolumeRow() {
            @Override public LocalDate getWeekStart() { return week; }
            @Override public BigDecimal getVolumeKg() { return new BigDecimal(volume); }
            @Override public long getSetCount() { return sets; }
        };
    }
}
