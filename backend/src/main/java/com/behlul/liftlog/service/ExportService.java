package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.ExportDtos.ExportedProfile;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.ExportRepository;
import com.behlul.liftlog.repository.ExportedFoodEntry;
import com.behlul.liftlog.repository.ExportedSet;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Lets users take their own data with them. Every method writes straight to the given stream (the HTTP
 * response) while rows arrive from the database, so a long history never sits in memory as one string.
 * Only the caller's rows are read: the user id comes from the token, never from the request.
 */
@Service
public class ExportService {

    public static final String WORKOUTS_ENTRY = "antrenman.csv";
    public static final String NUTRITION_ENTRY = "beslenme.csv";
    public static final String PROFILE_ENTRY = "profile.json";

    private final ExportRepository exportRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;
    private final JsonMapper jsonMapper;

    public ExportService(ExportRepository exportRepository, UserRepository userRepository,
                         CurrentUserProvider currentUser, JsonMapper jsonMapper) {
        this.exportRepository = exportRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.jsonMapper = jsonMapper;
    }

    /** The transaction keeps the database cursor open while the rows stream out. */
    @Transactional(readOnly = true)
    public void writeWorkoutsCsv(OutputStream out) {
        writeWorkouts(loadUser(), out);
    }

    @Transactional(readOnly = true)
    public void writeNutritionCsv(OutputStream out) {
        writeNutrition(loadUser().getId(), out);
    }

    /** Both CSVs and the profile in one ZIP, read in one transaction so they describe the same moment. */
    @Transactional(readOnly = true)
    public void writeAllZip(OutputStream out) throws IOException {
        User user = loadUser();
        ZipOutputStream zip = new ZipOutputStream(out);
        zip.putNextEntry(new ZipEntry(WORKOUTS_ENTRY));
        writeWorkouts(user, zip);
        zip.closeEntry();
        zip.putNextEntry(new ZipEntry(NUTRITION_ENTRY));
        writeNutrition(user.getId(), zip);
        zip.closeEntry();
        zip.putNextEntry(new ZipEntry(PROFILE_ENTRY));
        zip.write(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(ExportedProfile.from(user)));
        zip.closeEntry();
        // finish, not close: the servlet container owns the response stream
        zip.finish();
    }

    private void writeWorkouts(User user, OutputStream out) {
        CsvWriter csv = CsvWriter.start(out, "Tarih", "Antrenman notu", "Egzersiz", "Set", "Ağırlık (kg)",
                "Tekrar", "RPE", "Vücut ağırlığı", "Tahmini 1RM (kg)");
        try (Stream<ExportedSet> sets = exportRepository.streamSets(user.getId())) {
            sets.forEach(set -> csv.row(set.workoutDate(), set.workoutNotes(), set.exerciseName(), set.setOrder(),
                    set.weightKg(), set.reps(), set.rpe(), set.bodyweight() ? "evet" : "hayır",
                    estimatedOneRepMax(set, user.getBodyWeightKg())));
        }
        csv.flush();
    }

    private void writeNutrition(Long userId, OutputStream out) {
        // "Miktar (g)" sits next to the name it describes; empty when the portion is unknown
        CsvWriter csv = CsvWriter.start(out, "Tarih", "Yemek", "Miktar (g)", "Kalori (kcal)", "Protein (g)",
                "Karbonhidrat (g)", "Yağ (g)");
        try (Stream<ExportedFoodEntry> entries = exportRepository.streamFoodEntries(userId)) {
            entries.forEach(entry -> csv.row(entry.entryDate(), entry.name(), entry.grams(), entry.caloriesKcal(),
                    entry.proteinG(), entry.carbsG(), entry.fatG()));
        }
        csv.flush();
    }

    /**
     * Same rule as personal records in the app: for a bodyweight exercise the logged weight is the added
     * load, so the lifter's (current) body weight counts too when it is known.
     */
    static BigDecimal estimatedOneRepMax(ExportedSet set, BigDecimal bodyWeightKg) {
        if (set.reps() < 1) {
            return null;
        }
        BigDecimal load = set.bodyweight() && bodyWeightKg != null ? set.weightKg().add(bodyWeightKg) : set.weightKg();
        return OneRepMax.estimate(load, set.reps());
    }

    private User loadUser() {
        Long userId = currentUser.currentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
