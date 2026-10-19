package com.behlul.liftlog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The data export end to end: real security, schema and PostgreSQL (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ExportIntegrationTest {

    private static final String BOM = "﻿";
    private static final String TRICKY_NOTE = "Leg day; \"heavy\"\nfelt strong";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    @Test
    void exportsRequireAToken() throws Exception {
        for (String path : new String[]{"workouts.csv", "nutrition.csv", "all.zip"}) {
            mockMvc.perform(get("/api/v1/export/" + path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void workoutsCsvHasOneRowPerSetOfTheCallerOnly() throws Exception {
        String token = register("Behlül");
        String intruder = register("Someone else");
        seedWorkouts(token);
        long intruderWorkout = createWorkout(intruder, "2026-01-01", "intruder workout");
        addSet(intruder, intruderWorkout, exerciseId(intruder, "Squat"), 3, "999", null);

        MvcResult result = mockMvc.perform(get("/api/v1/export/workouts.csv").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"liftlog-antrenman-" + today() + ".csv\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).startsWith(0xEF, 0xBB, 0xBF);
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(BOM
                + "Tarih;Antrenman notu;Egzersiz;Set;Ağırlık (kg);Tekrar;RPE;Vücut ağırlığı;Tahmini 1RM (kg)\r\n"
                // ordered by date: the older workout first, then its sets in order
                + "2026-03-01;;Squat;1;140;3;;hayır;154\r\n"
                + "2026-03-05;\"Leg day; \"\"heavy\"\"\nfelt strong\";Squat;1;102,5;5;8,5;hayır;119,58\r\n"
                // pull-up: 10 kg added + 80 kg body weight = 90 kg load for the estimate
                + "2026-03-05;\"Leg day; \"\"heavy\"\"\nfelt strong\";Pull-up;2;10;6;;evet;108\r\n");
    }

    @Test
    void nutritionCsvHasTheCallersEntriesWithDecimalCommas() throws Exception {
        String token = register(null);
        String intruder = register(null);
        addFood(token, "2026-03-02", "Mercimek çorbası; büyük kase", 260, "13.5", "36.2", null, "312.5");
        addFood(token, "2026-03-02", "Yulaf (150 g)", 570, "19.5", "99", "10.5", "150");
        // Hand-typed: no portion, so the grams cell stays empty rather than 0
        addFood(token, "2026-03-01", "=1+1", 100, "0", null, "2.5");
        addFood(intruder, "2026-03-01", "Intruder food", 999, "99", null, null);

        MvcResult result = mockMvc.perform(get("/api/v1/export/nutrition.csv").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"liftlog-beslenme-" + today() + ".csv\""))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(BOM
                + "Tarih;Yemek;Miktar (g);Kalori (kcal);Protein (g);Karbonhidrat (g);Yağ (g)\r\n"
                + "2026-03-01;'=1+1;;100;0;;2,5\r\n"
                + "2026-03-02;\"Mercimek çorbası; büyük kase\";312,5;260;13,5;36,2;\r\n"
                + "2026-03-02;Yulaf (150 g);150;570;19,5;99;10,5\r\n");
    }

    @Test
    void zipHasBothCsvsAndAProfileWithoutSecrets() throws Exception {
        String email = randomEmail();
        String token = register(email, "Behlül");
        seedWorkouts(token);
        addFood(token, "2026-03-02", "Yulaf", 380, "13", null, null, "100");
        addFood(token, "2026-03-02", "Shake", 120, "24", null, null);
        authed(put("/api/v1/nutrition/goals"), token, "{\"calorieGoalKcal\":2500,\"proteinGoalG\":160}")
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/export/all.zip").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"liftlog-veriler-" + today() + ".zip\""))
                .andReturn();

        Map<String, String> entries = unzip(result.getResponse().getContentAsByteArray());
        assertThat(entries).containsOnlyKeys("antrenman.csv", "beslenme.csv", "profile.json");
        assertThat(entries.get("antrenman.csv")).startsWith(BOM + "Tarih;Antrenman notu;").contains("Pull-up;2;10;6");
        // Same writer as nutrition.csv: the grams column with a value, and empty when unknown
        assertThat(entries.get("beslenme.csv")).startsWith(BOM + "Tarih;Yemek;Miktar (g);")
                .contains("2026-03-02;Yulaf;100;380;13;;\r\n")
                .contains("2026-03-02;Shake;;120;24;;\r\n");

        JsonNode profile = jsonMapper.readTree(entries.get("profile.json"));
        assertThat(profile.get("displayName").asString()).isEqualTo("Behlül");
        assertThat(profile.get("heightCm").decimalValue()).isEqualByComparingTo("181.5");
        assertThat(profile.get("bodyWeightKg").decimalValue()).isEqualByComparingTo("80");
        assertThat(profile.get("calorieGoalKcal").asInt()).isEqualTo(2500);
        assertThat(profile.get("proteinGoalG").asInt()).isEqualTo(160);
        assertThat(profile.propertyNames()).containsExactlyInAnyOrder(
                "displayName", "heightCm", "bodyWeightKg", "calorieGoalKcal", "proteinGoalG");
        String raw = entries.get("profile.json").toLowerCase();
        assertThat(raw).doesNotContain(email.toLowerCase()).doesNotContain("password").doesNotContain("token");
    }

    @Test
    void anEmptyAccountGetsHeaderOnlyFiles() throws Exception {
        String token = register(null);

        MvcResult result = mockMvc.perform(get("/api/v1/export/all.zip").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn();

        Map<String, String> entries = unzip(result.getResponse().getContentAsByteArray());
        assertThat(entries.get("antrenman.csv")).hasLineCount(1);
        assertThat(entries.get("beslenme.csv")).hasLineCount(1);
        assertThat(jsonMapper.readTree(entries.get("profile.json")).get("displayName").isNull()).isTrue();
    }

    @Test
    void webAppCanReadTheChosenFileName() throws Exception {
        mockMvc.perform(get("/api/v1/export/workouts.csv")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.AUTHORIZATION, bearer(register(null))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition"));
        mockMvc.perform(options("/api/v1/export/workouts.csv")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk());
    }

    /** Profile 181.5 cm / 80 kg; a squat-only workout on Mar 1 and a squat + pull-up workout on Mar 5. */
    private void seedWorkouts(String token) throws Exception {
        authed(put("/api/v1/profile"), token,
                "{\"displayName\":\"Behlül\",\"heightCm\":181.5,\"bodyWeightKg\":80}").andExpect(status().isOk());
        long squat = exerciseId(token, "Squat");
        long pullUp = exerciseId(token, "Pull-up");
        long later = createWorkout(token, "2026-03-05", TRICKY_NOTE);
        long earlier = createWorkout(token, "2026-03-01", null);
        addSet(token, later, squat, 5, "102.5", "8.5");
        addSet(token, later, pullUp, 6, "10", null);
        addSet(token, earlier, squat, 3, "140", null);
    }

    private long createWorkout(String token, String date, String notes) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workoutDate", date);
        if (notes != null) {
            body.put("notes", notes);
        }
        MvcResult result = authed(post("/api/v1/workouts"), token, jsonMapper.writeValueAsString(body))
                .andExpect(status().isCreated()).andReturn();
        return read(result).get("id").asLong();
    }

    private void addSet(String token, long workoutId, long exerciseId, int reps, String weightKg, String rpe)
            throws Exception {
        String body = "{\"exerciseId\":%d,\"reps\":%d,\"weightKg\":%s%s}"
                .formatted(exerciseId, reps, weightKg, rpe == null ? "" : ",\"rpe\":" + rpe);
        authed(post("/api/v1/workouts/" + workoutId + "/sets"), token, body).andExpect(status().isCreated());
    }

    private void addFood(String token, String date, String name, int kcal, String protein, String carbs, String fat)
            throws Exception {
        addFood(token, date, name, kcal, protein, carbs, fat, null);
    }

    private void addFood(String token, String date, String name, int kcal, String protein, String carbs, String fat,
                         String grams) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (grams != null) {
            body.put("grams", grams);
        }
        body.put("entryDate", date);
        body.put("name", name);
        body.put("caloriesKcal", kcal);
        body.put("proteinG", protein);
        body.put("carbsG", carbs);
        body.put("fatG", fat);
        authed(post("/api/v1/nutrition/entries"), token, jsonMapper.writeValueAsString(body))
                .andExpect(status().isCreated());
    }

    private long exerciseId(String token, String name) throws Exception {
        JsonNode exercises = read(mockMvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn());
        for (JsonNode exercise : exercises) {
            if (exercise.get("name").asString().equals(name)) {
                return exercise.get("id").asLong();
            }
        }
        throw new AssertionError("No exercise " + name);
    }

    private String register(String displayName) throws Exception {
        return register(randomEmail(), displayName);
    }

    private String register(String email, String displayName) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", "s3cret-pass");
        if (displayName != null) {
            body.put("displayName", displayName);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        return read(result).get("token").asString();
    }

    private org.springframework.test.web.servlet.ResultActions authed(MockHttpServletRequestBuilder request,
                                                                       String token, String body) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String randomEmail() {
        return "export-" + UUID.randomUUID() + "@example.com";
    }
}
