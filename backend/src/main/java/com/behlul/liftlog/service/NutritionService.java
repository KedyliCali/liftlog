package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.NutritionDtos.FoodEntryRequest;
import com.behlul.liftlog.dto.NutritionDtos.FoodEntryResponse;
import com.behlul.liftlog.dto.NutritionDtos.FoodResponse;
import com.behlul.liftlog.dto.NutritionDtos.FrequentFoodResponse;
import com.behlul.liftlog.dto.NutritionDtos.NutritionDayResponse;
import com.behlul.liftlog.dto.NutritionDtos.NutritionGoals;
import com.behlul.liftlog.entity.FoodEntry;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.exception.FieldValidationException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.FoodEntryRepository;
import com.behlul.liftlog.repository.FoodRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class NutritionService {

    /** Long enough to cover a regular rotation of meals, short enough that an old diet drops off. */
    static final int FREQUENT_WINDOW_DAYS = 60;
    static final int MAX_FREQUENT = 12;

    private final FoodEntryRepository entryRepository;
    private final FoodRepository foodRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public NutritionService(FoodEntryRepository entryRepository, FoodRepository foodRepository,
                            UserRepository userRepository, CurrentUserProvider currentUser, Clock clock) {
        this.entryRepository = entryRepository;
        this.foodRepository = foodRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NutritionDayResponse getDay(LocalDate date) {
        User user = loadUser();
        List<FoodEntryResponse> entries = entryRepository.findByUserIdAndEntryDateOrderByIdAsc(user.getId(), date)
                .stream().map(FoodEntryResponse::from).toList();
        int calories = entries.stream().mapToInt(FoodEntryResponse::caloriesKcal).sum();
        BigDecimal protein = sum(entries.stream().map(FoodEntryResponse::proteinG).toList());
        BigDecimal carbs = sum(entries.stream().map(FoodEntryResponse::carbsG).toList());
        BigDecimal fat = sum(entries.stream().map(FoodEntryResponse::fatG).toList());
        return new NutritionDayResponse(date, entries, calories, protein, carbs, fat, goalsOf(user));
    }

    @Transactional
    public FoodEntryResponse addEntry(FoodEntryRequest request) {
        FoodEntry entry = new FoodEntry(userRepository.getReferenceById(currentUser.currentUserId()),
                request.entryDate(), request.name().trim(), request.caloriesKcal(), request.proteinG(),
                request.carbsG(), request.fatG(), request.grams());
        return FoodEntryResponse.from(entryRepository.save(entry));
    }

    /**
     * Copies every entry of {@code sourceDate} onto {@code targetDate} in one transaction, so a failure never
     * leaves half a day copied. Entries already on the target day stay; the copies are returned so the
     * client can offer an undo that deletes exactly them.
     */
    @Transactional
    public List<FoodEntryResponse> copyDay(LocalDate targetDate, LocalDate sourceDate) {
        if (targetDate.equals(sourceDate)) {
            throw new FieldValidationException("sourceDate", "must be a different day");
        }
        Long userId = currentUser.currentUserId();
        User user = userRepository.getReferenceById(userId);
        List<FoodEntry> copies = entryRepository.findByUserIdAndEntryDateOrderByIdAsc(userId, sourceDate)
                .stream()
                .map(source -> new FoodEntry(user, targetDate, source.getName(), source.getCaloriesKcal(),
                        source.getProteinG(), source.getCarbsG(), source.getFatG(), source.getGrams()))
                .toList();
        return entryRepository.saveAll(copies).stream().map(FoodEntryResponse::from).toList();
    }

    @Transactional
    public void deleteEntry(Long id) {
        FoodEntry entry = entryRepository.findByIdAndUserId(id, currentUser.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Food entry", id));
        entryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<FrequentFoodResponse> frequentFoods() {
        LocalDate since = LocalDate.now(clock).minusDays(FREQUENT_WINDOW_DAYS);
        return entryRepository.findFrequent(currentUser.currentUserId(), since, PageRequest.of(0, MAX_FREQUENT))
                .stream()
                .map(f -> new FrequentFoodResponse(f.name(), f.caloriesKcal(), f.proteinG(), f.carbsG(), f.fatG(),
                        f.timesEaten()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> foodCatalog() {
        return foodRepository.findAllByOrderByIdAsc().stream().map(FoodResponse::from).toList();
    }

    @Transactional
    public NutritionGoals updateGoals(NutritionGoals goals) {
        User user = loadUser();
        user.setNutritionGoals(goals.calorieGoalKcal(), goals.proteinGoalG());
        return goalsOf(user);
    }

    private User loadUser() {
        Long id = currentUser.currentUserId();
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /** Entries logged without carbs or fat count as zero in the day total. */
    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static NutritionGoals goalsOf(User user) {
        return new NutritionGoals(user.getCalorieGoalKcal(), user.getProteinGoalG());
    }
}
