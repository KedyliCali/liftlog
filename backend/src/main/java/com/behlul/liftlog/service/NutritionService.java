package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.NutritionDtos.FoodEntryRequest;
import com.behlul.liftlog.dto.NutritionDtos.FoodEntryResponse;
import com.behlul.liftlog.dto.NutritionDtos.FoodResponse;
import com.behlul.liftlog.dto.NutritionDtos.FrequentFoodResponse;
import com.behlul.liftlog.dto.NutritionDtos.NutritionDayResponse;
import com.behlul.liftlog.dto.NutritionDtos.NutritionGoals;
import com.behlul.liftlog.entity.FoodEntry;
import com.behlul.liftlog.entity.User;
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
        BigDecimal protein = entries.stream().map(FoodEntryResponse::proteinG).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new NutritionDayResponse(date, entries, calories, protein, goalsOf(user));
    }

    @Transactional
    public FoodEntryResponse addEntry(FoodEntryRequest request) {
        FoodEntry entry = new FoodEntry(userRepository.getReferenceById(currentUser.currentUserId()),
                request.entryDate(), request.name().trim(), request.caloriesKcal(), request.proteinG());
        return FoodEntryResponse.from(entryRepository.save(entry));
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
                .map(f -> new FrequentFoodResponse(f.name(), f.caloriesKcal(), f.proteinG(), f.timesEaten()))
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

    private static NutritionGoals goalsOf(User user) {
        return new NutritionGoals(user.getCalorieGoalKcal(), user.getProteinGoalG());
    }
}
