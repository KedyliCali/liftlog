package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.StatsDtos.PersonalRecordResponse;
import com.behlul.liftlog.dto.StatsDtos.ProgressPointResponse;
import com.behlul.liftlog.dto.StatsDtos.WeeklyVolumeResponse;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.ExerciseRepository;
import com.behlul.liftlog.repository.SetFact;
import com.behlul.liftlog.repository.StatsRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PRs are derived from sets on every request instead of being stored, so editing or deleting a
 * set can never leave a stale record behind.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    static final int MAX_WEEKS = 52;

    /** Heavier wins; at equal weight more reps wins; at a full tie the earlier set keeps the record. */
    static final Comparator<SetFact> HEAVIEST_FIRST = Comparator.comparing(SetFact::weightKg)
            .thenComparingInt(SetFact::reps)
            .reversed()
            .thenComparing(SetFact::workoutDate);

    private final StatsRepository statsRepository;
    private final ExerciseRepository exerciseRepository;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public StatsService(StatsRepository statsRepository, ExerciseRepository exerciseRepository,
                        CurrentUserProvider currentUser, Clock clock) {
        this.statsRepository = statsRepository;
        this.exerciseRepository = exerciseRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public List<PersonalRecordResponse> personalRecords() {
        Map<Long, List<SetFact>> byExercise = statsRepository.findAllFacts(currentUser.currentUserId()).stream()
                .collect(Collectors.groupingBy(SetFact::exerciseId, LinkedHashMap::new, Collectors.toList()));

        return byExercise.values().stream()
                .map(StatsService::toPersonalRecord)
                .sorted(Comparator.comparing(PersonalRecordResponse::exerciseName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<ProgressPointResponse> progress(Long exerciseId) {
        Long userId = currentUser.currentUserId();
        exerciseRepository.findVisibleById(exerciseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", exerciseId));

        Map<LocalDate, List<SetFact>> byDate = statsRepository.findFactsForExercise(userId, exerciseId).stream()
                .collect(Collectors.groupingBy(SetFact::workoutDate, TreeMap::new, Collectors.toList()));

        List<ProgressPointResponse> points = new ArrayList<>();
        byDate.forEach((date, sets) -> points.add(new ProgressPointResponse(
                date,
                sets.stream().map(SetFact::weightKg).max(Comparator.naturalOrder()).orElseThrow(),
                sets.stream().map(StatsService::estimate).max(Comparator.naturalOrder()).orElseThrow(),
                sets.stream().map(StatsService::volume).reduce(BigDecimal.ZERO, BigDecimal::add))));
        return points;
    }

    public List<WeeklyVolumeResponse> weeklyVolume(int weeks) {
        int safeWeeks = Math.clamp(weeks, 1, MAX_WEEKS);
        LocalDate thisMonday = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstMonday = thisMonday.minusWeeks(safeWeeks - 1L);

        Map<LocalDate, StatsRepository.WeeklyVolumeRow> rows = statsRepository
                .findWeeklyVolume(currentUser.currentUserId(), firstMonday).stream()
                .collect(Collectors.toMap(StatsRepository.WeeklyVolumeRow::getWeekStart, Function.identity()));

        // Emit every week in the window so the chart's x-axis has no gaps
        List<WeeklyVolumeResponse> result = new ArrayList<>(safeWeeks);
        for (LocalDate week = firstMonday; !week.isAfter(thisMonday); week = week.plusWeeks(1)) {
            StatsRepository.WeeklyVolumeRow row = rows.get(week);
            result.add(row == null
                    ? new WeeklyVolumeResponse(week, BigDecimal.ZERO, 0)
                    : new WeeklyVolumeResponse(week, row.getVolumeKg(), row.getSetCount()));
        }
        return result;
    }

    /** Best estimated 1RM among {@code sets}, or {@code null} when there are none. */
    static BigDecimal bestEstimate(List<SetFact> sets) {
        return sets.stream().map(StatsService::estimate).max(Comparator.naturalOrder()).orElse(null);
    }

    private static PersonalRecordResponse toPersonalRecord(List<SetFact> sets) {
        SetFact heaviest = sets.stream().sorted(HEAVIEST_FIRST).findFirst().orElseThrow();
        SetFact best1Rm = sets.stream()
                .max(Comparator.comparing(StatsService::estimate)
                        .thenComparing(SetFact::workoutDate, Comparator.reverseOrder()))
                .orElseThrow();
        return new PersonalRecordResponse(
                heaviest.exerciseId(),
                heaviest.exerciseName(),
                heaviest.weightKg(),
                heaviest.reps(),
                heaviest.workoutDate(),
                estimate(best1Rm),
                best1Rm.workoutDate(),
                sets.size(),
                heaviest.addedWeightKg(),
                heaviest.bodyweight());
    }

    private static BigDecimal estimate(SetFact set) {
        return OneRepMax.estimate(set.weightKg(), set.reps());
    }

    private static BigDecimal volume(SetFact set) {
        return set.weightKg().multiply(BigDecimal.valueOf(set.reps()));
    }
}
