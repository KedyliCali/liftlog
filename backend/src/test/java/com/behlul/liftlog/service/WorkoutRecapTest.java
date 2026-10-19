package com.behlul.liftlog.service;

import com.behlul.liftlog.repository.TimelineSet;
import com.behlul.liftlog.repository.WorkoutExerciseVolume;
import com.behlul.liftlog.service.WorkoutRecap.RecordHit;
import com.behlul.liftlog.service.WorkoutRecap.RecordKind;
import com.behlul.liftlog.service.WorkoutRecap.WorkoutShape;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutRecapTest {

    private static final long SQUAT = 1L;
    private static final long BENCH = 2L;
    private static final long ROW = 3L;

    @Test
    void heavierSetIsAWeightRecord() {
        List<RecordHit> hits = WorkoutRecap.personalRecords(List.of(
                set(10, "2026-10-01", 1, SQUAT, 5, "100"),
                set(20, "2026-10-08", 1, SQUAT, 3, "110")), 20L);

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.exerciseName()).isEqualTo("Squat");
            assertThat(hit.weightKg()).isEqualByComparingTo("110");
            assertThat(hit.reps()).isEqualTo(3);
            assertThat(hit.kind()).isEqualTo(RecordKind.WEIGHT);
        });
    }

    @Test
    void moreRepsAtTheSameWeightIsAnEstimatedMaxRecord() {
        List<RecordHit> hits = WorkoutRecap.personalRecords(List.of(
                set(10, "2026-10-01", 1, SQUAT, 5, "100"),
                set(20, "2026-10-08", 1, SQUAT, 8, "100")), 20L);

        assertThat(hits).extracting(RecordHit::kind).containsExactly(RecordKind.ESTIMATED_1RM);
    }

    @Test
    void firstTimeAnExerciseIsDoneIsNotARecord() {
        List<RecordHit> hits = WorkoutRecap.personalRecords(List.of(
                set(20, "2026-10-08", 1, SQUAT, 5, "100"),
                set(20, "2026-10-08", 2, SQUAT, 5, "105")), 20L);

        // The second set beats the first one of the same workout, which already counts as history
        assertThat(hits).extracting(RecordHit::weightKg).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("105"));
    }

    @Test
    void laterWorkoutsDoNotTakeARecordAway() {
        // Judged "at its time": the heavier set a week later does not matter for workout 20
        List<RecordHit> hits = WorkoutRecap.personalRecords(List.of(
                set(10, "2026-10-01", 1, SQUAT, 5, "100"),
                set(20, "2026-10-08", 1, SQUAT, 5, "110"),
                set(30, "2026-10-15", 1, SQUAT, 5, "120")), 20L);

        assertThat(hits).hasSize(1);
    }

    @Test
    void reportsTheStrongestRecordPerExerciseInWorkoutOrder() {
        List<RecordHit> hits = WorkoutRecap.personalRecords(List.of(
                set(10, "2026-10-01", 1, SQUAT, 5, "100"),
                set(10, "2026-10-01", 2, BENCH, 5, "80"),
                set(20, "2026-10-08", 1, BENCH, 5, "85"),
                set(20, "2026-10-08", 2, SQUAT, 5, "102.5"),
                set(20, "2026-10-08", 3, SQUAT, 5, "105"),
                set(20, "2026-10-08", 4, SQUAT, 5, "90")), 20L);

        assertThat(hits).extracting(RecordHit::exerciseName).containsExactly("Bench Press", "Squat");
        assertThat(hits.get(1).weightKg()).isEqualByComparingTo("105");
    }

    @Test
    void noRecordWhenNothingIsBeaten() {
        List<RecordHit> hits = WorkoutRecap.personalRecords(List.of(
                set(10, "2026-10-01", 1, SQUAT, 5, "100"),
                set(20, "2026-10-08", 1, SQUAT, 5, "100")), 20L);

        assertThat(hits).isEmpty();
    }

    @Test
    void comparesWithTheLatestEarlierWorkoutOfTheSameRoutine() {
        WorkoutShape current = shape(30, "2026-10-15", 7L, Set.of(SQUAT, BENCH), "5000");
        List<WorkoutShape> workouts = List.of(
                shape(10, "2026-10-01", 7L, Set.of(SQUAT), "3000"),
                shape(20, "2026-10-08", 7L, Set.of(SQUAT, ROW), "4000"),
                shape(25, "2026-10-10", null, Set.of(SQUAT, BENCH), "4500"),
                shape(40, "2026-10-20", 7L, Set.of(SQUAT, BENCH), "6000"),
                current);

        assertThat(WorkoutRecap.previousWorkout(current, workouts)).map(WorkoutShape::id).contains(20L);
    }

    @Test
    void withoutARoutineComparesWithTheSameExercises() {
        WorkoutShape current = shape(30, "2026-10-15", null, Set.of(SQUAT, BENCH), "5000");
        List<WorkoutShape> workouts = List.of(
                shape(10, "2026-10-01", null, Set.of(BENCH, SQUAT), "3000"),
                shape(20, "2026-10-08", null, Set.of(SQUAT), "4000"),
                current);

        assertThat(WorkoutRecap.previousWorkout(current, workouts)).map(WorkoutShape::id).contains(10L);
    }

    @Test
    void sameDayWorkoutsAreOrderedById() {
        WorkoutShape current = shape(30, "2026-10-15", null, Set.of(SQUAT), "5000");
        List<WorkoutShape> workouts = List.of(
                shape(29, "2026-10-15", null, Set.of(SQUAT), "1000"),
                shape(31, "2026-10-15", null, Set.of(SQUAT), "2000"),
                current);

        assertThat(WorkoutRecap.previousWorkout(current, workouts)).map(WorkoutShape::id).contains(29L);
    }

    @Test
    void noComparisonForAnEmptyOrUnmatchedWorkout() {
        WorkoutShape empty = shape(30, "2026-10-15", null, Set.of(), "0");
        WorkoutShape unmatched = shape(31, "2026-10-15", null, Set.of(ROW), "100");
        List<WorkoutShape> workouts = List.of(shape(10, "2026-10-01", null, Set.of(SQUAT), "3000"));

        assertThat(WorkoutRecap.previousWorkout(empty, workouts)).isEmpty();
        assertThat(WorkoutRecap.previousWorkout(unmatched, workouts)).isEmpty();
    }

    @Test
    void foldsExerciseRowsIntoWorkouts() {
        List<WorkoutShape> shapes = WorkoutRecap.shapes(List.of(
                new WorkoutExerciseVolume(10L, LocalDate.parse("2026-10-01"), 7L, SQUAT, 3, new BigDecimal("1500")),
                new WorkoutExerciseVolume(10L, LocalDate.parse("2026-10-01"), 7L, BENCH, 2, new BigDecimal("800")),
                new WorkoutExerciseVolume(20L, LocalDate.parse("2026-10-08"), null, ROW, 1, new BigDecimal("300"))));

        assertThat(shapes).hasSize(2);
        assertThat(shapes.getFirst().exerciseIds()).containsExactlyInAnyOrder(SQUAT, BENCH);
        assertThat(shapes.getFirst().setCount()).isEqualTo(5);
        assertThat(shapes.getFirst().volumeKg()).isEqualByComparingTo("2300");
        assertThat(shapes.get(1).routineId()).isNull();
    }

    private static TimelineSet set(long workoutId, String date, int order, long exerciseId, int reps, String kg) {
        String name = exerciseId == SQUAT ? "Squat" : exerciseId == BENCH ? "Bench Press" : "Row";
        return new TimelineSet(workoutId, LocalDate.parse(date), order, exerciseId, name, reps, new BigDecimal(kg));
    }

    private static WorkoutShape shape(long id, String date, Long routineId, Set<Long> exercises, String volume) {
        return new WorkoutShape(id, LocalDate.parse(date), routineId, exercises, exercises.size(), new BigDecimal(volume));
    }
}
