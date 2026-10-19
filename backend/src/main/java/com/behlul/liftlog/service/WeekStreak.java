package com.behlul.liftlog.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Weekly training streak: how many ISO weeks (Monday start) in a row had at least one workout.
 *
 * @param weeks            the current streak; counts this week only once it has a workout, otherwise it
 *                         starts from last week, so a streak does not drop to 0 on Monday morning
 * @param thisWeekWorkouts workouts dated in the current week
 * @param longestWeeks     the longest run of consecutive weeks ever, never less than {@code weeks}
 */
public record WeekStreak(int weeks, int thisWeekWorkouts, int longestWeeks) {

    public static WeekStreak compute(Collection<LocalDate> workoutDates, LocalDate today) {
        LocalDate thisMonday = mondayOf(today);
        TreeSet<LocalDate> activeWeeks = new TreeSet<>();
        int thisWeekWorkouts = 0;
        for (LocalDate date : workoutDates) {
            LocalDate monday = mondayOf(date);
            activeWeeks.add(monday);
            if (monday.equals(thisMonday)) {
                thisWeekWorkouts++;
            }
        }

        LocalDate cursor = activeWeeks.contains(thisMonday) ? thisMonday : thisMonday.minusWeeks(1);
        int current = 0;
        while (activeWeeks.contains(cursor)) {
            current++;
            cursor = cursor.minusWeeks(1);
        }

        return new WeekStreak(current, thisWeekWorkouts, Math.max(current, longestRun(List.copyOf(activeWeeks))));
    }

    /** Longest run of consecutive Mondays in an ascending, duplicate-free list. */
    private static int longestRun(List<LocalDate> sortedMondays) {
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate monday : sortedMondays) {
            run = previous != null && previous.plusWeeks(1).equals(monday) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = monday;
        }
        return longest;
    }

    private static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
