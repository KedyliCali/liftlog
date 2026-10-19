package com.behlul.liftlog.service;

import com.behlul.liftlog.repository.ExerciseRepository.ExerciseMuscleRow;
import com.behlul.liftlog.repository.ExerciseSetCount;
import com.behlul.liftlog.service.MuscleMap.ExerciseShare;
import com.behlul.liftlog.service.MuscleMap.Muscle;
import com.behlul.liftlog.service.MuscleMap.MuscleRole;
import com.behlul.liftlog.service.MuscleMap.MuscleSets;
import com.behlul.liftlog.service.MuscleMap.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.behlul.liftlog.service.MuscleMap.Muscle.ABDUCTORS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.ABS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.ADDUCTORS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.BICEPS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.CHEST;
import static com.behlul.liftlog.service.MuscleMap.Muscle.FRONT_DELTS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.GLUTES;
import static com.behlul.liftlog.service.MuscleMap.Muscle.HAMSTRINGS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.LATS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.QUADS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.SIDE_DELTS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.TRAPS;
import static com.behlul.liftlog.service.MuscleMap.Muscle.TRICEPS;
import static com.behlul.liftlog.service.MuscleMap.Role.PRIMARY;
import static com.behlul.liftlog.service.MuscleMap.Role.SECONDARY;
import static org.assertj.core.api.Assertions.assertThat;

class MuscleMapTest {

    private static final List<MuscleRole> BENCH = List.of(
            role(CHEST, PRIMARY), role(FRONT_DELTS, SECONDARY), role(TRICEPS, SECONDARY));

    @Test
    void primaryMusclesCountOneSetAndSecondaryHalf() {
        List<MuscleSets> result = MuscleMap.setsPerMuscle(
                List.of(count(1, "Bench Press", 3), count(2, "Triceps Pushdown", 2)),
                Map.of(1L, BENCH, 2L, List.of(role(TRICEPS, PRIMARY))));

        // Most sets first: triceps 2 (pushdown) + 1.5 (bench), chest 3, front delts 1.5
        assertThat(result).extracting(MuscleSets::muscle).containsExactly(TRICEPS, CHEST, FRONT_DELTS);
        assertThat(result).extracting(m -> m.sets().toPlainString()).containsExactly("3.5", "3.0", "1.5");
        assertThat(result.getFirst().exercises()).containsExactly(
                new ExerciseShare("Triceps Pushdown", new BigDecimal("2.0")),
                new ExerciseShare("Bench Press", new BigDecimal("1.5")));
    }

    @Test
    void aSingleSecondarySetIsHalfASet() {
        List<MuscleSets> result = MuscleMap.setsPerMuscle(List.of(count(1, "Bench Press", 1)), Map.of(1L, BENCH));

        assertThat(result).extracting(m -> m.sets().toPlainString()).containsExactly("1.0", "0.5", "0.5");
    }

    @Test
    void tiesKeepMapOrderAndExerciseTiesSortByName() {
        List<MuscleSets> result = MuscleMap.setsPerMuscle(
                List.of(count(1, "Pec Deck", 2), count(2, "Cable Fly", 2), count(3, "Leg Extension", 4)),
                Map.of(1L, List.of(role(CHEST, PRIMARY)), 2L, List.of(role(CHEST, PRIMARY)),
                        3L, List.of(role(QUADS, PRIMARY))));

        assertThat(result).extracting(MuscleSets::muscle).containsExactly(CHEST, QUADS);
        assertThat(result.getFirst().exercises()).extracting(ExerciseShare::name)
                .containsExactly("Cable Fly", "Pec Deck");
    }

    @Test
    void exercisesWithoutMusclesAndEmptyInputAddNothing() {
        assertThat(MuscleMap.setsPerMuscle(List.of(), Map.of())).isEmpty();
        assertThat(MuscleMap.setsPerMuscle(List.of(count(9, "Mystery", 5)), Map.of())).isEmpty();
        assertThat(MuscleMap.setsPerMuscle(List.of(count(9, "Mystery", 5)), Map.of(9L, List.of()))).isEmpty();
    }

    @Test
    void sameExerciseCountedTwiceIsSummed() {
        // Two rows for one exercise (e.g. from two queries merged) still add up under one exercise share
        List<MuscleSets> result = MuscleMap.setsPerMuscle(
                List.of(count(1, "Bench Press", 2), count(1, "Bench Press", 1)), Map.of(1L, BENCH));

        assertThat(result.getFirst().sets()).isEqualByComparingTo("3");
        assertThat(result.getFirst().exercises()).hasSize(1);
    }

    @Test
    void adductorsAndAbductorsFoldIntoQuadsAndGlutesWithoutCountingTwice() {
        List<MuscleRole> squat = MuscleMap.resolve(
                List.of(role(QUADS, PRIMARY), role(GLUTES, PRIMARY), role(ADDUCTORS, SECONDARY)), "Legs");
        assertThat(squat).containsExactly(role(GLUTES, PRIMARY), role(QUADS, PRIMARY));

        assertThat(MuscleMap.resolve(List.of(role(ADDUCTORS, PRIMARY)), "Legs"))
                .containsExactly(role(QUADS, PRIMARY));
        assertThat(MuscleMap.resolve(List.of(role(ABDUCTORS, PRIMARY), role(GLUTES, SECONDARY)), "Legs"))
                .containsExactly(role(GLUTES, PRIMARY));

        List<MuscleSets> result = MuscleMap.setsPerMuscle(List.of(count(1, "Squat", 4)), Map.of(1L, squat));
        assertThat(result).extracting(MuscleSets::muscle).containsExactly(GLUTES, QUADS);
        assertThat(result).allSatisfy(m -> assertThat(m.sets()).isEqualByComparingTo("4"));
    }

    @Test
    void storedMusclesWinOverTheMuscleGroup() {
        assertThat(MuscleMap.resolve(List.of(role(BICEPS, PRIMARY)), "Chest")).containsExactly(role(BICEPS, PRIMARY));
    }

    @Test
    void customExercisesFallBackToTheirMuscleGroup() {
        assertThat(MuscleMap.resolve(List.of(), "Chest")).containsExactly(role(CHEST, PRIMARY));
        assertThat(MuscleMap.resolve(List.of(), "Back")).containsExactly(role(LATS, PRIMARY), role(TRAPS, SECONDARY));
        assertThat(MuscleMap.resolve(List.of(), "Legs"))
                .containsExactly(role(QUADS, PRIMARY), role(GLUTES, SECONDARY), role(HAMSTRINGS, SECONDARY));
        assertThat(MuscleMap.resolve(List.of(), "Shoulders"))
                .containsExactly(role(SIDE_DELTS, PRIMARY), role(FRONT_DELTS, SECONDARY));
        assertThat(MuscleMap.resolve(List.of(), "Arms"))
                .containsExactly(role(BICEPS, SECONDARY), role(TRICEPS, SECONDARY));
        assertThat(MuscleMap.resolve(List.of(), "Core")).containsExactly(role(ABS, PRIMARY));
        // Case and spaces do not matter; unknown or missing groups train nothing on the map
        assertThat(MuscleMap.resolve(List.of(), "  cHeSt ")).containsExactly(role(CHEST, PRIMARY));
        assertThat(MuscleMap.resolve(List.of(), "Grip")).isEmpty();
        assertThat(MuscleMap.resolve(List.of(), null)).isEmpty();
    }

    @Test
    void armsFallbackGivesHalfASetToBothBicepsAndTriceps() {
        List<MuscleSets> result = MuscleMap.setsPerMuscle(List.of(count(5, "My Arm Thing", 3)),
                Map.of(5L, MuscleMap.resolve(List.of(), "Arms")));

        assertThat(result).extracting(MuscleSets::muscle).containsExactly(BICEPS, TRICEPS);
        assertThat(result).extracting(m -> m.sets().toPlainString()).containsExactly("1.5", "1.5");
    }

    @Test
    void resolveAllUsesStoredRowsAndFallsBackPerExercise() {
        Map<Long, String> groups = new HashMap<>();
        groups.put(1L, "Chest");
        groups.put(2L, "Core");
        groups.put(3L, null);

        Map<Long, List<MuscleRole>> resolved = MuscleMap.resolveAll(groups,
                List.of(row(1, "CHEST", "PRIMARY"), row(1, "TRICEPS", "SECONDARY")));

        assertThat(resolved.get(1L)).containsExactly(role(CHEST, PRIMARY), role(TRICEPS, SECONDARY));
        assertThat(resolved.get(2L)).containsExactly(role(ABS, PRIMARY));
        assertThat(resolved.get(3L)).isEmpty();
    }

    private static MuscleRole role(Muscle muscle, Role role) {
        return new MuscleRole(muscle, role);
    }

    private static ExerciseSetCount count(long exerciseId, String name, long sets) {
        return new ExerciseSetCount(exerciseId, name, null, sets);
    }

    private static ExerciseMuscleRow row(long exerciseId, String muscle, String role) {
        return new ExerciseMuscleRow() {
            @Override
            public Long getExerciseId() {
                return exerciseId;
            }

            @Override
            public String getMuscle() {
                return muscle;
            }

            @Override
            public String getRole() {
                return role;
            }
        };
    }
}
