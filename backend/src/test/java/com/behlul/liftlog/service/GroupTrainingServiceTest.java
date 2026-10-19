package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.GroupTrainingDtos.LiftBest;
import com.behlul.liftlog.exception.FieldValidationException;
import com.behlul.liftlog.repository.UserLiftSet;
import com.behlul.liftlog.repository.UserSetTotals;
import com.behlul.liftlog.service.GroupTrainingService.TrainingNumbers;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupTrainingServiceTest {

    // Friday; the 7-day window is Saturday 10-10 .. Friday 10-16
    private static final LocalDate TO = LocalDate.parse("2026-10-16");
    private static final LocalDate FROM = TO.minusDays(6);

    @Test
    void countsOnlyWorkoutsInsideTheWindowButStreaksOverAllOfThem() {
        List<LocalDate> dates = List.of(
                LocalDate.parse("2026-10-16"), LocalDate.parse("2026-10-10"),   // inside, both ends
                LocalDate.parse("2026-10-09"),                                  // a day before the window
                LocalDate.parse("2026-09-28"));                                 // three weeks back: streak of 3

        TrainingNumbers numbers = GroupTrainingService.summarize(dates, null, List.of(), FROM, TO);

        assertThat(numbers.workouts()).isEqualTo(2);
        assertThat(numbers.weekStreak()).isEqualTo(3);
        assertThat(numbers.sets()).isZero();
        assertThat(numbers.totalVolumeKg()).isEqualByComparingTo("0");
    }

    @Test
    void bestIsTheHighestEstimatePerLiftInAFixedOrderWithNullsForMissingLifts() {
        List<UserLiftSet> lifts = List.of(
                new UserLiftSet(1L, "Squat", 5, new BigDecimal("100")),   // 116.67
                new UserLiftSet(1L, "Squat", 1, new BigDecimal("120")),   // 120 wins
                new UserLiftSet(1L, "Bench Press", 8, new BigDecimal("80")));   // 101.33

        TrainingNumbers numbers = GroupTrainingService.summarize(List.of(TO),
                new UserSetTotals(1L, 3, new BigDecimal("1260")), lifts, FROM, TO);

        assertThat(numbers.best()).extracting(LiftBest::exercise)
                .containsExactly("Bench Press", "Squat", "Deadlift", "Overhead Press");
        assertThat(numbers.best().get(0).estimated1rmKg()).isEqualByComparingTo("101.33");
        assertThat(numbers.best().get(1).estimated1rmKg()).isEqualByComparingTo("120");
        assertThat(numbers.best().get(2).estimated1rmKg()).isNull();
        assertThat(numbers.best().get(3).estimated1rmKg()).isNull();
        assertThat(numbers.sets()).isEqualTo(3);
        assertThat(numbers.totalVolumeKg()).isEqualByComparingTo("1260");
    }

    @Test
    void onlySevenOrThirtyDaysAreAccepted() {
        GroupTrainingService service = new GroupTrainingService(null, null, null, null, Clock.systemUTC());
        assertThatThrownBy(() -> service.training(14, TO)).isInstanceOf(FieldValidationException.class);
    }
}
