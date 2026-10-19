package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.GroupTrainingDtos.GroupTrainingMember;
import com.behlul.liftlog.dto.GroupTrainingDtos.GroupTrainingResponse;
import com.behlul.liftlog.dto.GroupTrainingDtos.LiftBest;
import com.behlul.liftlog.entity.LolGroupMember;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.exception.FieldValidationException;
import com.behlul.liftlog.repository.LolGroupMemberRepository;
import com.behlul.liftlog.repository.StatsRepository;
import com.behlul.liftlog.repository.UserLiftSet;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.repository.UserSetTotals;
import com.behlul.liftlog.repository.UserWorkoutDate;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Friends compare training inside the group they already share for League (one group per user). Only
 * members who opted in (users.share_training) are summarized; the others appear by name alone. The summary
 * is numbers only: counts, volume, streak and estimated 1RMs. Workouts, notes, nutrition and body data are
 * never read here, so they cannot leak.
 */
@Service
@Transactional(readOnly = true)
public class GroupTrainingService {

    static final Set<Integer> WINDOW_DAYS = Set.of(7, 30);

    /** The four big lifts, by the built-in exercise names, in the order the table shows them. */
    static final List<String> BIG_LIFTS = List.of("Bench Press", "Squat", "Deadlift", "Overhead Press");

    private final LolGroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StatsRepository statsRepository;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public GroupTrainingService(LolGroupMemberRepository memberRepository, UserRepository userRepository,
                                StatsRepository statsRepository, CurrentUserProvider currentUser, Clock clock) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.statsRepository = statsRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Empty when the caller is in no group.
     *
     * @param today the phone's date (its time zone decides what "today" is); null means the server's date
     */
    public Optional<GroupTrainingResponse> training(int days, LocalDate today) {
        if (!WINDOW_DAYS.contains(days)) {
            throw new FieldValidationException("days", "must be 7 or 30");
        }
        Long me = currentUser.currentUserId();
        Optional<LolGroupMember> membership = memberRepository.findById(me);
        if (membership.isEmpty()) {
            return Optional.empty();
        }
        LocalDate to = today != null ? today : LocalDate.now(clock);
        LocalDate from = to.minusDays(days - 1L);

        List<LolGroupMember> members = memberRepository.findByGroupIdOrderByJoinedAtAscUserIdAsc(
                membership.get().getGroupId());
        Map<Long, User> users = userRepository.findAllById(members.stream().map(LolGroupMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, user -> user));
        // The opt-in gate: only these users' sets are ever queried
        List<Long> visible = members.stream()
                .map(LolGroupMember::getUserId)
                .filter(id -> id.equals(me) || users.get(id).isShareTraining())
                .toList();

        Map<Long, List<LocalDate>> datesByUser = statsRepository.findWorkoutDatesOf(visible).stream()
                .collect(Collectors.groupingBy(UserWorkoutDate::userId,
                        Collectors.mapping(UserWorkoutDate::workoutDate, Collectors.toList())));
        Map<Long, UserSetTotals> totalsByUser = statsRepository.findSetTotalsOf(visible, from, to).stream()
                .collect(Collectors.toMap(UserSetTotals::userId, totals -> totals));
        Map<Long, List<UserLiftSet>> liftsByUser = statsRepository.findLiftSetsOf(visible, BIG_LIFTS).stream()
                .collect(Collectors.groupingBy(UserLiftSet::userId));

        List<GroupTrainingMember> result = new ArrayList<>(members.size());
        for (LolGroupMember member : members) {
            Long id = member.getUserId();
            User user = users.get(id);
            if (!visible.contains(id)) {
                result.add(GroupTrainingMember.hidden(user.getDisplayName()));
                continue;
            }
            TrainingNumbers numbers = summarize(datesByUser.getOrDefault(id, List.of()), totalsByUser.get(id),
                    liftsByUser.getOrDefault(id, List.of()), from, to);
            result.add(new GroupTrainingMember(id, user.getDisplayName(), id.equals(me), user.isShareTraining(),
                    numbers.workouts(), numbers.totalVolumeKg(), numbers.sets(), numbers.weekStreak(),
                    numbers.best()));
        }
        return Optional.of(new GroupTrainingResponse(days, from, to, result));
    }

    record TrainingNumbers(int workouts, BigDecimal totalVolumeKg, long sets, int weekStreak, List<LiftBest> best) {
    }

    /** One user's numbers from their rows; the streak and 1RM rules are the ones the Progress page uses. */
    static TrainingNumbers summarize(List<LocalDate> workoutDates, UserSetTotals totals, List<UserLiftSet> lifts,
                                     LocalDate from, LocalDate to) {
        int workouts = (int) workoutDates.stream().filter(date -> !date.isBefore(from) && !date.isAfter(to)).count();
        int streak = WeekStreak.compute(workoutDates, to).weeks();

        Map<String, BigDecimal> bestByLift = new HashMap<>();
        for (UserLiftSet set : lifts) {
            bestByLift.merge(set.exerciseName(), OneRepMax.estimate(set.weightKg(), set.reps()), BigDecimal::max);
        }
        List<LiftBest> best = BIG_LIFTS.stream().map(lift -> new LiftBest(lift, bestByLift.get(lift))).toList();

        return new TrainingNumbers(workouts,
                totals == null ? BigDecimal.ZERO : totals.volumeKg(),
                totals == null ? 0 : totals.sets(),
                streak, best);
    }
}
