package com.behlul.liftlog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OneRepMaxTest {

    @ParameterizedTest(name = "{0} kg x {1} -> {2}")
    @CsvSource({
            "100, 1, 100.00",   // a single is already a max
            "100, 5, 116.67",   // 100 * (1 + 5/30)
            "140, 3, 154.00",
            "142.5, 5, 166.25",
            "60, 10, 80.00",
    })
    void estimatesWithEpley(String weight, int reps, String expected) {
        assertThat(OneRepMax.estimate(new BigDecimal(weight), reps)).isEqualByComparingTo(expected);
    }

    @Test
    void rejectsZeroReps() {
        assertThatThrownBy(() -> OneRepMax.estimate(BigDecimal.TEN, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
