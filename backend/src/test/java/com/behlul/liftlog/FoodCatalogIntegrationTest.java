package com.behlul.liftlog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The built-in food list after V23: its size, the new categories, data sanity and how it searches. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FoodCatalogIntegrationTest {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    /** Alcohol brings 7 kcal/g of its own; the two others are fibre-rich rows from V7 counted the US way. */
    private static final Set<String> ENERGY_EXCEPTIONS = Set.of("Bira", "Kırmızı şarap", "Rakı", "Haşlanmış mısır", "Kuru kayısı");

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void v23AddsHundredsOfFoodsInTheNewCategories() throws Exception {
        long lastBeforeV23 = lastIdBeforeV23();
        Integer added = jdbcTemplate.queryForObject("SELECT count(*) FROM foods WHERE id > ?", Integer.class, lastBeforeV23);
        assertThat(lastBeforeV23).isEqualTo(315);
        assertThat(added).isGreaterThanOrEqualTo(700);

        Map<String, Object> perCategory = jdbcTemplate.queryForMap("""
                SELECT count(*) FILTER (WHERE category = 'fastfood')   AS fastfood,
                       count(*) FILTER (WHERE category = 'supplement') AS supplement,
                       count(*) FILTER (WHERE category = 'packaged')   AS packaged
                FROM foods""");
        assertThat(((Number) perCategory.get("fastfood")).intValue()).isGreaterThanOrEqualTo(100);
        assertThat(((Number) perCategory.get("supplement")).intValue()).isGreaterThanOrEqualTo(70);
        assertThat(((Number) perCategory.get("packaged")).intValue()).isGreaterThanOrEqualTo(150);

        // The endpoint sends every row, in seed order, so the picker shows each category together
        JsonNode foods = catalog();
        Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM foods", Integer.class);
        assertThat(foods.size()).isEqualTo(total);
        assertThat(foods.get(315).get("nameTr").asString()).isEqualTo("Big Mac (McDonald's)");
        assertThat(foods.get(315).get("category").asString()).isEqualTo("fastfood");
    }

    @Test
    void v23SkipsNamesThatAreAlreadyInTheCatalog() throws Exception {
        String migration = new ClassPathResource("db/migration/V23__more_foods.sql").getContentAsString(StandardCharsets.UTF_8);
        // Running it again finds every name in place and inserts nothing
        assertThat(jdbcTemplate.update(migration)).isZero();
    }

    @Test
    void everyFoodHasASensibleServingAndEnergyThatMatchesItsMacros() {
        long lastBeforeV23 = lastIdBeforeV23();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM foods");
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("name_tr");
            assertThat(((Number) row.get("serving_g")).doubleValue()).as("serving of %s", name).isPositive();
            assertThat((String) row.get("serving_tr")).as("serving label of %s", name).isNotBlank();
            assertThat((String) row.get("serving_en")).as("English serving label of %s", name).isNotBlank();
            assertThat((String) row.get("name_en")).as("English name of %s", name).isNotBlank();
            if (ENERGY_EXCEPTIONS.contains(name)) continue;

            double kcal = number(row, "kcal_per_100g");
            double fromMacros = 4 * number(row, "protein_per_100g") + 4 * number(row, "carbs_per_100g") + 9 * number(row, "fat_per_100g");
            // V23 was checked tighter while it was written; the older rows keep a little more room
            boolean fromV23 = ((Number) row.get("id")).longValue() > lastBeforeV23;
            double tolerance = fromV23 ? Math.max(8, kcal * 0.10) : Math.max(15, kcal * 0.12);
            assertThat(Math.abs(kcal - fromMacros)).as("energy of %s: %s kcal vs %s from macros", name, kcal, fromMacros)
                    .isLessThanOrEqualTo(tolerance);
        }
    }

    @Test
    void brandsAndTurkishNamesAreFoundTypedPlainly() throws Exception {
        JsonNode foods = catalog();
        // Typed the way people type on a phone: no Turkish letters, no apostrophes, lower case
        assertThat(search(foods, "big mac")).contains("Big Mac (McDonald's)", "Double Big Mac (McDonald's)");
        assertThat(search(foods, "mcdonalds")).contains("Big Mac (McDonald's)", "McChicken (McDonald's)").hasSizeGreaterThan(15);
        assertThat(search(foods, "whey")).contains("Whey protein (çikolatalı)", "Whey izolat", "Gold Standard Whey (Optimum Nutrition)");
        assertThat(search(foods, "sutlac")).contains("Sütlaç", "Fırın sütlaç");
        assertThat(search(foods, "cig kofte durum")).contains("Çiğ köfte dürüm", "Çiğ köfte marul dürüm");
        assertThat(search(foods, "IZGARA KOFTE")).contains("Izgara köfte");
        assertThat(search(foods, "kitkat")).contains("KitKat (Nestlé)");
        assertThat(search(foods, "coca cola zero")).containsExactly("Coca-Cola Zero");
        assertThat(search(foods, "simit sarayi")).contains("Simit (Simit Sarayı)");
    }

    /**
     * Same matching as the web app's searchFoods (web/src/lib/foodCatalog.ts): every typed word must be in
     * the Turkish or English name after folding, or in the name with spaces and punctuation squeezed out.
     */
    private static List<String> search(JsonNode foods, String query) {
        List<String> words = Arrays.stream(fold(query).split("\\s+")).filter(w -> !w.isEmpty()).toList();
        List<String> matches = new ArrayList<>();
        for (JsonNode food : foods) {
            List<String> names = List.of(fold(food.get("nameTr").asString()), fold(food.get("nameEn").asString()));
            boolean all = words.stream().allMatch(word -> names.stream()
                    .anyMatch(name -> name.contains(word) || name.replaceAll("[^a-z0-9]", "").contains(word)));
            if (all) matches.add(food.get("nameTr").asString());
        }
        return matches;
    }

    private static String fold(String text) {
        String lower = text.toLowerCase(TURKISH);
        String stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('ı', 'i');
        return stripped.replaceAll("['’`]", "").replaceAll("[-/&.,()+]", " ");
    }

    private long lastIdBeforeV23() {
        // Rakı is the last row V8 added; nothing between V8 and V23 touched the catalog
        return jdbcTemplate.queryForObject("SELECT id FROM foods WHERE name_tr = 'Rakı'", Long.class);
    }

    private static double number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).doubleValue();
    }

    private JsonNode catalog() throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of("email", "foods-" + UUID.randomUUID() + "@example.com", "password", "s3cret-pass"));
        String token = jsonMapper.readTree(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asString();
        return jsonMapper.readTree(mockMvc.perform(get("/api/v1/foods").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
