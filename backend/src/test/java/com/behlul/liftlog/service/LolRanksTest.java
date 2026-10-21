package com.behlul.liftlog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LolRanksTest {

    @Test
    void scoresPutEveryDivisionOnOneLadder() {
        assertThat(LolRanks.score("IRON", "IV", 0)).hasValue(0);
        assertThat(LolRanks.score("IRON", "III", 0)).hasValue(100);
        assertThat(LolRanks.score("GOLD", "II", 45)).hasValue(3 * 400 + 2 * 100 + 45);
        assertThat(LolRanks.score("DIAMOND", "I", 99)).hasValue(2799);
        // The three apex tiers share one ladder: their LP keeps counting from Master on
        assertThat(LolRanks.score("MASTER", "I", 0)).hasValue(2800);
        assertThat(LolRanks.score("GRANDMASTER", "I", 450)).hasValue(3250);
        assertThat(LolRanks.score("CHALLENGER", "I", 1200)).hasValue(4000);
        assertThat(LolRanks.score("UNKNOWN", "I", 10)).isEmpty();
        assertThat(LolRanks.score("GOLD", "V", 10)).isEmpty();
        assertThat(LolRanks.score(null, null, 0)).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3} {4} {5}, win={6}: {7}")
    @CsvSource({
            // Inside a division
            "GOLD, II, 45, GOLD, II, 66, true, 21",
            "GOLD, II, 45, GOLD, II, 27, false, -18",
            // Promotion: the LP over 100 carries into the next division
            "GOLD, IV, 90, GOLD, III, 15, true, 25",
            // Promotion into the next tier
            "SILVER, I, 88, GOLD, IV, 10, true, 22",
            // Demotion across a division and across a tier
            "GOLD, III, 5, GOLD, IV, 85, false, -20",
            "PLATINUM, IV, 10, GOLD, I, 85, false, -25",
            // Into Master and between the apex tiers, where LP keeps counting
            "DIAMOND, I, 80, MASTER, I, 0, true, 20",
            "MASTER, I, 480, GRANDMASTER, I, 498, true, 18",
            "GRANDMASTER, I, 300, MASTER, I, 281, false, -19",
    })
    void lpChangeAcrossDivisionsAndTiers(String tierBefore, String divisionBefore, int lpBefore, String tierAfter,
                                         String divisionAfter, int lpAfter, boolean win, int expected) {
        assertThat(LolRanks.lpChange(tierBefore, divisionBefore, lpBefore, tierAfter, divisionAfter, lpAfter, win))
                .isEqualTo(expected);
    }

    @Test
    void lpChangeIsUnknownWhenItCannotBeOneGame() {
        // No change (a remake)
        assertThat(LolRanks.lpChange("GOLD", "II", 45, "GOLD", "II", 45, true)).isNull();
        // The direction contradicts the result
        assertThat(LolRanks.lpChange("GOLD", "II", 45, "GOLD", "II", 30, true)).isNull();
        assertThat(LolRanks.lpChange("GOLD", "II", 45, "GOLD", "II", 60, false)).isNull();
        // A step no single game makes (decay, a whole tier at once)
        assertThat(LolRanks.lpChange("GOLD", "II", 45, "PLATINUM", "II", 45, true)).isNull();
        // A tier this code does not know
        assertThat(LolRanks.lpChange("GOLD", "II", 45, "MYTHIC", "I", 10, true)).isNull();
    }
}
