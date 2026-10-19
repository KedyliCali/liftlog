package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.WeightDtos.WeightEntryResponse;
import com.behlul.liftlog.dto.WeightDtos.WeightHistoryResponse;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.entity.WeightEntry;
import com.behlul.liftlog.exception.FieldValidationException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.repository.WeightEntryRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class WeightService {

    static final Set<Integer> HISTORY_DAYS = Set.of(30, 90, 365);

    /**
     * The log's upper limit (weight_entries CHECK). The profile still accepts up to 500 kg for older
     * clients; such a weight is stored on the profile but not logged.
     */
    static final BigDecimal MAX_LOGGED_KG = new BigDecimal("400");

    /** The 30-day change may compare with an entry up to 60 days before the latest one (see WeightTrend). */
    private static final int TREND_LOOKBACK_DAYS = 60;

    private final WeightEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public WeightService(WeightEntryRepository entryRepository, UserRepository userRepository,
                         CurrentUserProvider currentUser, Clock clock) {
        this.entryRepository = entryRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** @param today the phone's date (so the window ends on the user's day), the server's when null */
    @Transactional(readOnly = true)
    public WeightHistoryResponse history(int days, LocalDate today) {
        if (!HISTORY_DAYS.contains(days)) {
            throw new FieldValidationException("days", "must be 30, 90 or 365");
        }
        Long userId = currentUser.currentUserId();
        Optional<WeightEntry> newest = entryRepository.findFirstByUserIdOrderByEntryDateDesc(userId);
        if (newest.isEmpty()) {
            return WeightHistoryResponse.of(List.of(), WeightTrend.EMPTY);
        }
        LocalDate windowStart = (today != null ? today : LocalDate.now(clock)).minusDays(days - 1L);
        LocalDate trendStart = newest.get().getEntryDate().minusDays(TREND_LOOKBACK_DAYS);
        // One query covers both the chart window and the trend, whichever reaches further back
        List<WeightEntry> loaded = entryRepository.findByUserIdAndEntryDateGreaterThanEqualOrderByEntryDateAsc(
                userId, windowStart.isBefore(trendStart) ? windowStart : trendStart);

        List<WeightEntryResponse> inWindow = loaded.stream()
                .filter(e -> !e.getEntryDate().isBefore(windowStart))
                .map(WeightEntryResponse::from)
                .toList();
        WeightTrend trend = WeightTrend.compute(loaded.stream()
                .map(e -> new WeightTrend.Point(e.getEntryDate(), e.getWeightKg()))
                .toList());
        return WeightHistoryResponse.of(inWindow, trend);
    }

    /** One entry per day: logging again on the same date replaces the weight. */
    @Transactional
    public WeightEntryResponse save(LocalDate date, BigDecimal weightKg) {
        // One day of slack: a phone east of UTC is already on tomorrow while the server is not
        if (date.isAfter(LocalDate.now(clock).plusDays(1))) {
            throw new FieldValidationException("date", "must not be in the future");
        }
        Long userId = currentUser.currentUserId();
        WeightEntry entry = upsert(userId, date, weightKg);
        syncProfileWeight(userId);
        return WeightEntryResponse.from(entry);
    }

    @Transactional
    public void delete(LocalDate date) {
        Long userId = currentUser.currentUserId();
        WeightEntry entry = entryRepository.findByUserIdAndEntryDate(userId, date)
                .orElseThrow(() -> new ResourceNotFoundException("Weight entry", date.toString()));
        entryRepository.delete(entry);
        entryRepository.flush();
        syncProfileWeight(userId);
    }

    /**
     * Called when the profile's weight is edited, so the history gets today's value too. The profile has
     * already stored the weight itself; this only writes the log entry.
     */
    @Transactional
    public void recordFromProfile(Long userId, BigDecimal weightKg) {
        upsert(userId, LocalDate.now(clock), weightKg);
    }

    private WeightEntry upsert(Long userId, LocalDate date, BigDecimal weightKg) {
        // Two simultaneous saves for the same day can still race to insert; the UNIQUE constraint turns the
        // loser into a 409 instead of a duplicate row, which is acceptable for a once-a-day action
        WeightEntry entry = entryRepository.findByUserIdAndEntryDate(userId, date)
                .orElseGet(() -> new WeightEntry(userId, date, weightKg));
        entry.setWeightKg(weightKg);
        return entryRepository.save(entry);
    }

    /**
     * users.body_weight_kg is what bodyweight-exercise stats and BMI read, so it follows the newest entry.
     * Deleting the last entry leaves it as it was: clearing the log should not erase the profile.
     */
    private void syncProfileWeight(Long userId) {
        entryRepository.findFirstByUserIdOrderByEntryDateDesc(userId).ifPresent(newest -> {
            User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
            user.setBodyWeightKg(newest.getWeightKg());
        });
    }
}
