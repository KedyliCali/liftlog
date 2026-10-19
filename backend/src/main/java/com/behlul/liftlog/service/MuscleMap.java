package com.behlul.liftlog.service;

import com.behlul.liftlog.repository.ExerciseRepository.ExerciseMuscleRow;
import com.behlul.liftlog.repository.ExerciseSetCount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure logic behind the muscle map: which map regions an exercise trains and how many sets each region got.
 * A set counts 1 for every primary muscle of its exercise and 0.5 for every secondary one.
 */
final class MuscleMap {

    /** The 16 regions drawn on the map, plus two muscles stored for accuracy and drawn inside another region. */
    enum Muscle {
        CHEST, FRONT_DELTS, SIDE_DELTS, REAR_DELTS, BICEPS, TRICEPS, FOREARMS, ABS, OBLIQUES, TRAPS, LATS,
        LOWER_BACK, GLUTES, QUADS, HAMSTRINGS, CALVES, ADDUCTORS, ABDUCTORS;

        /** The region that shows this muscle: the inner thigh sits with QUADS, the outer hip with GLUTES. */
        Muscle region() {
            return switch (this) {
                case ADDUCTORS -> QUADS;
                case ABDUCTORS -> GLUTES;
                default -> this;
            };
        }
    }

    enum Role {
        PRIMARY(BigDecimal.ONE), SECONDARY(new BigDecimal("0.5"));

        private final BigDecimal setWeight;

        Role(BigDecimal setWeight) {
            this.setWeight = setWeight;
        }
    }

    record MuscleRole(Muscle muscle, Role role) {
    }

    record ExerciseShare(String name, BigDecimal sets) {
    }

    record MuscleSets(Muscle muscle, BigDecimal sets, List<ExerciseShare> exercises) {
    }

    private MuscleMap() {
    }

    /**
     * Map regions an exercise trains: its stored rows, or (custom exercises have none) a guess from its muscle
     * group. Folded onto map regions, one entry per region; when two muscles land on one region the primary role
     * wins, so a set is never counted twice for the same region. Ordered by role, then region.
     */
    static List<MuscleRole> resolve(List<MuscleRole> stored, String muscleGroup) {
        List<MuscleRole> source = stored.isEmpty() ? fromMuscleGroup(muscleGroup) : stored;
        Map<Muscle, Role> byRegion = new EnumMap<>(Muscle.class);
        for (MuscleRole entry : source) {
            byRegion.merge(entry.muscle().region(), entry.role(), (a, b) -> a == Role.PRIMARY ? a : b);
        }
        return byRegion.entrySet().stream()
                .map(e -> new MuscleRole(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(MuscleRole::role).thenComparing(MuscleRole::muscle))
                .toList();
    }

    /**
     * {@link #resolve} for many exercises at once.
     *
     * @param muscleGroupById every exercise to resolve, with its muscle group (null allowed)
     * @param storedRows      the stored muscle rows of those exercises
     */
    static Map<Long, List<MuscleRole>> resolveAll(Map<Long, String> muscleGroupById, List<ExerciseMuscleRow> storedRows) {
        Map<Long, List<MuscleRole>> stored = new HashMap<>();
        for (ExerciseMuscleRow row : storedRows) {
            stored.computeIfAbsent(row.getExerciseId(), id -> new ArrayList<>())
                    .add(new MuscleRole(Muscle.valueOf(row.getMuscle()), Role.valueOf(row.getRole())));
        }
        Map<Long, List<MuscleRole>> resolved = new HashMap<>();
        muscleGroupById.forEach((id, group) -> resolved.put(id, resolve(stored.getOrDefault(id, List.of()), group)));
        return resolved;
    }

    /** Fallback for exercises without stored muscles; unknown or missing groups train nothing on the map. */
    static List<MuscleRole> fromMuscleGroup(String muscleGroup) {
        if (muscleGroup == null) {
            return List.of();
        }
        return switch (muscleGroup.trim().toLowerCase(Locale.ROOT)) {
            case "chest" -> List.of(primary(Muscle.CHEST));
            case "back" -> List.of(primary(Muscle.LATS), secondary(Muscle.TRAPS));
            case "legs" -> List.of(primary(Muscle.QUADS), secondary(Muscle.HAMSTRINGS), secondary(Muscle.GLUTES));
            case "shoulders" -> List.of(primary(Muscle.SIDE_DELTS), secondary(Muscle.FRONT_DELTS));
            // Unknown whether it was a curl or a pushdown, so each gets half
            case "arms" -> List.of(secondary(Muscle.BICEPS), secondary(Muscle.TRICEPS));
            case "core" -> List.of(primary(Muscle.ABS));
            default -> List.of();
        };
    }

    /**
     * Sets per map region with each exercise's share, for regions with sets above zero. Sums are exact (halves
     * only) and rounded to one decimal at the end. Regions with the most sets first, ties in map order; inside a
     * region, exercises with the most sets first, ties by name.
     *
     * @param counts            sets done per exercise
     * @param musclesByExercise resolved muscles per exercise id (see {@link #resolve}); missing ids train nothing
     */
    static List<MuscleSets> setsPerMuscle(List<ExerciseSetCount> counts,
                                          Map<Long, List<MuscleRole>> musclesByExercise) {
        Map<Muscle, Map<Long, BigDecimal>> setsByMuscleAndExercise = new EnumMap<>(Muscle.class);
        Map<Long, String> names = new LinkedHashMap<>();
        for (ExerciseSetCount count : counts) {
            names.put(count.exerciseId(), count.exerciseName());
            for (MuscleRole entry : musclesByExercise.getOrDefault(count.exerciseId(), List.of())) {
                BigDecimal sets = entry.role().setWeight.multiply(BigDecimal.valueOf(count.setCount()));
                setsByMuscleAndExercise.computeIfAbsent(entry.muscle().region(), m -> new LinkedHashMap<>())
                        .merge(count.exerciseId(), sets, BigDecimal::add);
            }
        }

        List<MuscleSets> result = new ArrayList<>();
        setsByMuscleAndExercise.forEach((muscle, byExercise) -> {
            BigDecimal total = byExercise.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() <= 0) {
                return;
            }
            List<ExerciseShare> shares = byExercise.entrySet().stream()
                    .map(e -> new ExerciseShare(names.get(e.getKey()), oneDecimal(e.getValue())))
                    .sorted(Comparator.comparing(ExerciseShare::sets).reversed()
                            .thenComparing(ExerciseShare::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            result.add(new MuscleSets(muscle, oneDecimal(total), shares));
        });
        // EnumMap iterates in map order, and the sort is stable, so ties keep that order
        result.sort(Comparator.comparing(MuscleSets::sets).reversed());
        return result;
    }

    private static BigDecimal oneDecimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private static MuscleRole primary(Muscle muscle) {
        return new MuscleRole(muscle, Role.PRIMARY);
    }

    private static MuscleRole secondary(Muscle muscle) {
        return new MuscleRole(muscle, Role.SECONDARY);
    }
}
