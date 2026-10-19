package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.RoutineDtos.Suggestion;
import com.behlul.liftlog.service.Progression.PreviousSet;
import com.behlul.liftlog.service.Progression.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressionTest {

    private static PreviousSet set(int reps, String weightKg) {
        return new PreviousSet(reps, new BigDecimal(weightKg));
    }

    @Test
    void neverDoneBeforeIsFirstWithoutAWeight() {
        Result result = Progression.suggest(List.of(), 8, 3, false);

        assertThat(result.suggestion()).isEqualTo(Suggestion.FIRST);
        assertThat(result.suggestedWeightKg()).isNull();
        assertThat(result.lastWeightKg()).isNull();
        assertThat(result.increaseKg()).isNull();
        assertThat(result.suggestedReps()).isEqualTo(8);
    }

    @Test
    void everyPlannedSetAtTargetAddsTwoAndAHalfKilos() {
        Result result = Progression.suggest(List.of(set(8, "100"), set(8, "100"), set(9, "100")), 8, 3, false);

        assertThat(result.suggestion()).isEqualTo(Suggestion.INCREASE);
        assertThat(result.suggestedWeightKg()).isEqualTo(new BigDecimal("102.50"));
        assertThat(result.lastWeightKg()).isEqualTo(new BigDecimal("100.00"));
        assertThat(result.increaseKg()).isEqualTo(new BigDecimal("2.50"));
        assertThat(result.suggestedReps()).isEqualTo(8);
    }

    @Test
    void oneSetShortKeepsTheWeight() {
        Result result = Progression.suggest(List.of(set(8, "100"), set(7, "100"), set(8, "100")), 8, 3, false);

        assertThat(result.suggestion()).isEqualTo(Suggestion.SAME);
        assertThat(result.suggestedWeightKg()).isEqualTo(new BigDecimal("100.00"));
        assertThat(result.increaseKg()).isNull();
    }

    @Test
    void fewerSetsThanPlannedCountsAsShort() {
        Result result = Progression.suggest(List.of(set(10, "60"), set(10, "60")), 8, 3, false);

        assertThat(result.suggestion()).isEqualTo(Suggestion.SAME);
        assertThat(result.suggestedWeightKg()).isEqualTo(new BigDecimal("60.00"));
    }

    @Test
    void onlyTheFirstPlannedSetsAreJudged() {
        // A fourth, failed extra set does not cancel the increase
        assertThat(Progression.reachedEveryPlannedSet(
                List.of(set(5, "80"), set(5, "80"), set(5, "80"), set(2, "80")), 5, 3)).isTrue();
        // ...but a short set among the planned ones does
        assertThat(Progression.reachedEveryPlannedSet(
                List.of(set(5, "80"), set(4, "80"), set(5, "80"), set(5, "80")), 5, 3)).isFalse();
    }

    @Test
    void workingWeightIsTheMostCommonWeight() {
        // A light warm-up and one heavy single do not move it
        assertThat(Progression.workingWeight(List.of(set(10, "40"), set(5, "100"), set(5, "100.00"), set(1, "120"))))
                .isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void workingWeightTieGoesToTheHeaviest() {
        assertThat(Progression.workingWeight(List.of(set(5, "90"), set(5, "95"), set(5, "95"), set(5, "90"))))
                .isEqualTo(new BigDecimal("95.00"));
    }

    @Test
    void bodyweightWithoutAddedLoadProgressesByOneRep() {
        Result result = Progression.suggest(List.of(set(10, "0"), set(10, "0"), set(10, "0")), 10, 3, true);

        assertThat(result.suggestion()).isEqualTo(Suggestion.INCREASE);
        assertThat(result.suggestedWeightKg()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.increaseKg()).isNull();
        assertThat(result.suggestedReps()).isEqualTo(11);
    }

    @Test
    void bodyweightRepsKeepClimbingFromWhatWasDone() {
        // Last time 12, 13, 12 against a target of 10 (a fourth extra set is not judged): next is 13
        Result result = Progression.suggest(
                List.of(set(12, "0"), set(13, "0"), set(12, "0"), set(20, "0")), 10, 3, true);

        assertThat(result.suggestion()).isEqualTo(Suggestion.INCREASE);
        assertThat(result.suggestedReps()).isEqualTo(13);
    }

    @Test
    void bodyweightWithAddedLoadProgressesByWeight() {
        Result result = Progression.suggest(List.of(set(6, "10"), set(6, "10")), 6, 2, true);

        assertThat(result.suggestion()).isEqualTo(Suggestion.INCREASE);
        assertThat(result.suggestedWeightKg()).isEqualTo(new BigDecimal("12.50"));
        assertThat(result.increaseKg()).isEqualTo(new BigDecimal("2.50"));
        assertThat(result.suggestedReps()).isEqualTo(6);
    }

    @Test
    void bodyweightShortKeepsTheTargetReps() {
        Result result = Progression.suggest(List.of(set(10, "0"), set(8, "0")), 10, 2, true);

        assertThat(result.suggestion()).isEqualTo(Suggestion.SAME);
        assertThat(result.suggestedWeightKg()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.suggestedReps()).isEqualTo(10);
    }
}
