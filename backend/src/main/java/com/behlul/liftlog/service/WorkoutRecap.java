package com.behlul.liftlog.service;

import com.behlul.liftlog.repository.TimelineSet;
import com.behlul.liftlog.repository.WorkoutExerciseVolume;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pure logic behind the end-of-workout summary: records hit in a workout and the workout to compare with. */
public final class WorkoutRecap {

    public enum RecordKind {
        /** Heavier than any earlier set of the exercise. */
        WEIGHT,
        /** Not heavier, but a better estimated one-rep max (e.g. more reps at a weight). */
        ESTIMATED_1RM
    }

    public record RecordHit(Long exerciseId, String exerciseName, BigDecimal weightKg, int reps, RecordKind kind,
                            BigDecimal estimated1RmKg) {
    }

    /** A workout reduced to what the comparison needs. */
    public record WorkoutShape(Long id, LocalDate date, Long routineId, Set<Long> exerciseIds, long setCount,
                               BigDecimal volumeKg) {

        /** Earlier on the timeline: an earlier date, or the same date and created first (smaller id). */
        boolean isBefore(WorkoutShape other) {
            int byDate = date.compareTo(other.date);
            return byDate < 0 || (byDate == 0 && id < other.id);
        }
    }

    private static final Comparator<WorkoutShape> TIMELINE =
            Comparator.comparing(WorkoutShape::date).thenComparing(WorkoutShape::id);

    private WorkoutRecap() {
    }

    /**
     * Sets of {@code workoutId} that beat every earlier set of their exercise (earlier workouts and earlier sets
     * of the same workout), on weight or on estimated 1RM. Like the live PR toast, the first time an exercise is
     * ever done is not a record. At most one hit per exercise, the strongest, in the order the exercises were
     * first done in the workout.
     *
     * @param timeline sets of the user's exercises in timeline order (date, workout id, set order)
     */
    public static List<RecordHit> personalRecords(List<TimelineSet> timeline, Long workoutId) {
        Map<Long, BigDecimal> bestWeight = new HashMap<>();
        Map<Long, BigDecimal> bestEstimate = new HashMap<>();
        Map<Long, RecordHit> strongestHit = new LinkedHashMap<>();

        for (TimelineSet set : timeline) {
            Long exerciseId = set.exerciseId();
            BigDecimal estimate = OneRepMax.estimate(set.loadKg(), set.reps());
            BigDecimal previousWeight = bestWeight.get(exerciseId);
            BigDecimal previousEstimate = bestEstimate.get(exerciseId);

            if (set.workoutId().equals(workoutId) && previousWeight != null) {
                boolean heavier = set.loadKg().compareTo(previousWeight) > 0;
                boolean strongerEstimate = estimate.compareTo(previousEstimate) > 0;
                if (heavier || strongerEstimate) {
                    RecordHit hit = new RecordHit(exerciseId, set.exerciseName(), set.loadKg(), set.reps(),
                            heavier ? RecordKind.WEIGHT : RecordKind.ESTIMATED_1RM, estimate);
                    strongestHit.merge(exerciseId, hit, WorkoutRecap::stronger);
                } else {
                    // Keep the exercise's slot so the output follows the workout's exercise order
                    strongestHit.putIfAbsent(exerciseId, null);
                }
            }

            bestWeight.merge(exerciseId, set.loadKg(), BigDecimal::max);
            bestEstimate.merge(exerciseId, estimate, BigDecimal::max);
        }
        return strongestHit.values().stream().filter(hit -> hit != null).toList();
    }

    /**
     * The workout to compare {@code current} with: the latest earlier workout of the same routine, or else the
     * latest earlier workout with exactly the same exercises. Empty when there is none, so the summary does not
     * compare unrelated sessions.
     */
    public static Optional<WorkoutShape> previousWorkout(WorkoutShape current, List<WorkoutShape> workouts) {
        List<WorkoutShape> earlier = workouts.stream().filter(w -> w.isBefore(current)).toList();
        if (current.routineId() != null) {
            Optional<WorkoutShape> sameRoutine = earlier.stream()
                    .filter(w -> current.routineId().equals(w.routineId()))
                    .max(TIMELINE);
            if (sameRoutine.isPresent()) {
                return sameRoutine;
            }
        }
        if (current.exerciseIds().isEmpty()) {
            return Optional.empty();
        }
        return earlier.stream().filter(w -> w.exerciseIds().equals(current.exerciseIds())).max(TIMELINE);
    }

    /** Folds per-exercise rows into one shape per workout. */
    public static List<WorkoutShape> shapes(List<WorkoutExerciseVolume> rows) {
        Map<Long, List<WorkoutExerciseVolume>> byWorkout = new LinkedHashMap<>();
        for (WorkoutExerciseVolume row : rows) {
            byWorkout.computeIfAbsent(row.workoutId(), id -> new ArrayList<>()).add(row);
        }
        List<WorkoutShape> shapes = new ArrayList<>(byWorkout.size());
        byWorkout.forEach((id, workoutRows) -> {
            WorkoutExerciseVolume first = workoutRows.getFirst();
            Set<Long> exerciseIds = new HashSet<>();
            long setCount = 0;
            BigDecimal volume = BigDecimal.ZERO;
            for (WorkoutExerciseVolume row : workoutRows) {
                exerciseIds.add(row.exerciseId());
                setCount += row.setCount();
                volume = volume.add(row.volumeKg());
            }
            shapes.add(new WorkoutShape(id, first.workoutDate(), first.routineId(), Set.copyOf(exerciseIds),
                    setCount, volume));
        });
        return shapes;
    }

    private static RecordHit stronger(RecordHit a, RecordHit b) {
        if (a == null) {
            return b;
        }
        int byEstimate = b.estimated1RmKg().compareTo(a.estimated1RmKg());
        if (byEstimate != 0) {
            return byEstimate > 0 ? b : a;
        }
        return b.weightKg().compareTo(a.weightKg()) > 0 ? b : a;
    }
}
