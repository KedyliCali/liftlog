package com.behlul.liftlog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GroupTrainingDtos {

    private GroupTrainingDtos() {
    }

    /**
     * The caller's group compared on training, over workouts dated {@code from}..{@code to} (both inclusive).
     *
     * @param members join order, like the League board
     */
    public record GroupTrainingResponse(int days, LocalDate from, LocalDate to, List<GroupTrainingMember> members) {
    }

    /**
     * One member. A member who did not opt in is only {displayName, shares: false}: null fields are left out of
     * the JSON, so not even a zero leaks. The caller always gets their own numbers, with {@code shares} saying
     * whether the others can see them.
     *
     * @param workouts      workouts in the window, empty ones included (the same rule as the streak)
     * @param totalVolumeKg reps × effective weight over the window's sets
     * @param weekStreak    consecutive ISO weeks with a workout, up to the window's last day
     * @param best          all-time best estimated 1RM of the four big lifts, always in the same order
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupTrainingMember(Long userId, String displayName, Boolean you, boolean shares,
                                      Integer workouts, BigDecimal totalVolumeKg, Long sets, Integer weekStreak,
                                      List<LiftBest> best) {

        public static GroupTrainingMember hidden(String displayName) {
            return new GroupTrainingMember(null, displayName, null, false, null, null, null, null, null);
        }
    }

    /** {@code estimated1rmKg} is null when the member never logged the lift; it stays in the JSON as null. */
    public record LiftBest(String exercise, BigDecimal estimated1rmKg) {
    }
}
