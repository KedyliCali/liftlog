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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the real security filter chain, Flyway schema and PostgreSQL (Testcontainers).
 * Each test registers its own users with random emails, so tests stay independent without cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JwtEncoder jwtEncoder;

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/workouts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/workouts").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsOnlyTheConfiguredWebOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/workouts")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mockMvc.perform(options("/api/v1/workouts")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthAndSwaggerArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void registerLoginAndMe() throws Exception {
        String email = randomEmail();
        register(email, "s3cret-pass", "Behlul");

        // Duplicate email, case-insensitively
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'%s','password':'another-pass'}", email.toUpperCase())))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'%s','password':'wrong-pass'}", email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'%s','password':'s3cret-pass'}", email)))
                .andExpect(status().isOk())
                .andReturn();
        String token = read(login).get("token").asString();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Behlul"));
    }

    @Test
    void registerRejectsShortPasswordAndBadEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'not-an-email','password':'short'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void fullWorkoutFlowWithStatsAndPersonalRecord() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        long workoutId = read(authed(post("/api/v1/workouts"), token,
                json("{'workoutDate':'%s','notes':'Leg day'}", today))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        // Exercise 1 is the seeded Squat
        authed(post("/api/v1/workouts/" + workoutId + "/sets"), token, "{\"exerciseId\":1,\"reps\":5,\"weightKg\":100}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setOrder").value(1))
                .andExpect(jsonPath("$.newPersonalRecord").value(false));
        authed(post("/api/v1/workouts/" + workoutId + "/sets"), token, "{\"exerciseId\":1,\"reps\":3,\"weightKg\":110,\"rpe\":9}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setOrder").value(2))
                .andExpect(jsonPath("$.newPersonalRecord").value(true));

        mockMvc.perform(get("/api/v1/workouts").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].setCount").value(2))
                .andExpect(jsonPath("$.content[0].totalVolumeKg").value(830.0));

        mockMvc.perform(get("/api/v1/stats/prs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exerciseName").value("Squat"))
                .andExpect(jsonPath("$[0].heaviestWeightKg").value(110.0))
                .andExpect(jsonPath("$[0].bestEstimated1RmKg").value(121.0));

        mockMvc.perform(get("/api/v1/stats/progress?exerciseId=1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].topWeightKg").value(110.0));

        // Native date_trunc query: the current week holds all the volume
        mockMvc.perform(get("/api/v1/stats/volume?weeks=4").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].volumeKg").value(830.0))
                .andExpect(jsonPath("$[3].setCount").value(2))
                .andExpect(jsonPath("$[0].volumeKg").value(0));
    }

    @Test
    void usersCannotSeeOrChangeEachOthersData() throws Exception {
        String owner = register(randomEmail(), "s3cret-pass", null);
        String intruder = register(randomEmail(), "s3cret-pass", null);

        long workoutId = read(authed(post("/api/v1/workouts"), owner, "{\"workoutDate\":\"2026-10-17\"}")
                .andReturn()).get("id").asLong();
        long setId = read(authed(post("/api/v1/workouts/" + workoutId + "/sets"), owner,
                "{\"exerciseId\":1,\"reps\":5,\"weightKg\":100}").andReturn()).get("id").asLong();

        mockMvc.perform(get("/api/v1/workouts/" + workoutId).header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder))
                .andExpect(status().isNotFound());
        authed(put("/api/v1/sets/" + setId), intruder, "{\"exerciseId\":1,\"reps\":1,\"weightKg\":1}")
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/workouts").header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void customExercisesArePrivateToTheirOwner() throws Exception {
        String owner = register(randomEmail(), "s3cret-pass", null);
        String other = register(randomEmail(), "s3cret-pass", null);
        String name = "Hip Thrust " + UUID.randomUUID().toString().substring(0, 6);

        authed(post("/api/v1/exercises"), owner, json("{'name':'%s','muscleGroup':'Legs'}", name))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.custom").value(true));

        MvcResult othersList = mockMvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isOk()).andReturn();
        assertThat(othersList.getResponse().getContentAsString()).doesNotContain(name);
    }

    @Test
    void profileCanBeEditedAndHasAPrivatePhoto() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        String other = register(randomEmail(), "s3cret-pass", null);

        authed(put("/api/v1/profile"), token, "{\"displayName\":\" Behlul \",\"heightCm\":181.5,\"bodyWeightKg\":82.4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Behlul"))
                .andExpect(jsonPath("$.heightCm").value(181.5))
                .andExpect(jsonPath("$.avatarVersion").doesNotExist());

        authed(put("/api/v1/profile"), token, "{\"heightCm\":20}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.heightCm").exists());

        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
        mockMvc.perform(put("/api/v1/profile/avatar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.IMAGE_JPEG).content(jpeg))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarVersion").isNumber());

        mockMvc.perform(get("/api/v1/profile/avatar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(jpeg));

        // Another user never sees it
        mockMvc.perform(get("/api/v1/profile/avatar").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());

        // Claims to be a PNG but is not an image at all
        mockMvc.perform(put("/api/v1/profile/avatar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.IMAGE_PNG).content("not an image".getBytes()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/profile/avatar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());

        mockMvc.perform(delete("/api/v1/profile/avatar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.avatarVersion").doesNotExist())
                .andExpect(jsonPath("$.bodyWeightKg").value(82.4));
    }

    @Test
    void bodyweightExercisesCountBodyWeightPlusAddedLoad() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        authed(put("/api/v1/profile"), token, "{\"bodyWeightKg\":80}").andExpect(status().isOk());

        JsonNode exercises = read(mockMvc.perform(get("/api/v1/exercises")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn());
        long pullUp = -1;
        for (JsonNode e : exercises) {
            if (e.get("name").asString().equals("Pull-up")) {
                assertThat(e.get("bodyweight").asBoolean()).isTrue();
                pullUp = e.get("id").asLong();
            }
        }
        assertThat(pullUp).isPositive();

        long workoutId = read(authed(post("/api/v1/workouts"), token, "{\"workoutDate\":\"2026-10-18\"}")
                .andReturn()).get("id").asLong();
        authed(post("/api/v1/workouts/" + workoutId + "/sets"), token,
                json("{'exerciseId':%d,'reps':8,'weightKg':0}", pullUp))
                .andExpect(jsonPath("$.bodyweight").value(true));
        // +10 kg for 5 reps: 90 kg x 5 beats 80 kg x 8 on estimated 1RM (105 vs 101.33)
        authed(post("/api/v1/workouts/" + workoutId + "/sets"), token,
                json("{'exerciseId':%d,'reps':5,'weightKg':10}", pullUp))
                .andExpect(jsonPath("$.newPersonalRecord").value(true))
                .andExpect(jsonPath("$.weightKg").value(10));

        mockMvc.perform(get("/api/v1/workouts/" + workoutId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.totalVolumeKg").value(1090.0)); // 8 x 80 + 5 x 90
        mockMvc.perform(get("/api/v1/stats/prs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$[0].exerciseName").value("Pull-up"))
                .andExpect(jsonPath("$[0].heaviestWeightKg").value(90.0))
                .andExpect(jsonPath("$[0].heaviestAddedKg").value(10.0))
                .andExpect(jsonPath("$[0].bodyweight").value(true))
                .andExpect(jsonPath("$[0].bestEstimated1RmKg").value(105.0));
        mockMvc.perform(get("/api/v1/workouts").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content[0].totalVolumeKg").value(1090.0));
    }

    @Test
    void nutritionDayTotalsGoalsAndFrequentFoods() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        String oats = json("{'entryDate':'%s','name':' Oats ','caloriesKcal':380,'proteinG':13.5,'carbsG':60.2,'fatG':6.5}", today);
        String shake = json("{'entryDate':'%s','name':'Protein shake','caloriesKcal':120,'proteinG':24}", today);

        authed(post("/api/v1/nutrition/entries"), token, oats).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Oats"));
        long shakeId = read(authed(post("/api/v1/nutrition/entries"), token, shake)
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        authed(post("/api/v1/nutrition/entries"), token, shake.replace(today, yesterday)).andExpect(status().isCreated());
        authed(post("/api/v1/nutrition/entries"), token,
                json("{'entryDate':'%s','name':'','caloriesKcal':-5,'proteinG':1}", today))
                .andExpect(status().isBadRequest());

        authed(put("/api/v1/nutrition/goals"), token, "{\"calorieGoalKcal\":2600,\"proteinGoalG\":160}")
                .andExpect(status().isOk());
        authed(put("/api/v1/nutrition/goals"), token, "{\"calorieGoalKcal\":100}").andExpect(status().isBadRequest());

        // Only today's entries count, in the order they were logged, and the goals come along
        mockMvc.perform(get("/api/v1/nutrition/days/" + today).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].name").value("Oats"))
                .andExpect(jsonPath("$.totalCaloriesKcal").value(500))
                .andExpect(jsonPath("$.totalProteinG").value(37.5))
                // The shake was logged without carbs or fat: it counts as zero, and stays null on the entry
                .andExpect(jsonPath("$.totalCarbsG").value(60.2))
                .andExpect(jsonPath("$.totalFatG").value(6.5))
                .andExpect(jsonPath("$.entries[1].carbsG").doesNotExist())
                .andExpect(jsonPath("$.goals.calorieGoalKcal").value(2600))
                .andExpect(jsonPath("$.goals.proteinGoalG").value(160));

        // The shake was eaten twice, so it leads the quick-add list
        mockMvc.perform(get("/api/v1/nutrition/frequent").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Protein shake"))
                .andExpect(jsonPath("$[0].timesEaten").value(2))
                .andExpect(jsonPath("$[1].name").value("Oats"));

        // Another user cannot see or delete these entries
        String stranger = register(randomEmail(), "s3cret-pass", null);
        mockMvc.perform(delete("/api/v1/nutrition/entries/" + shakeId).header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/nutrition/days/" + today).header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.totalCaloriesKcal").value(0));

        mockMvc.perform(delete("/api/v1/nutrition/entries/" + shakeId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/nutrition/days/" + today).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.totalCaloriesKcal").value(380));
    }

    @Test
    void foodCatalogListsSeededFoodsInBothLanguages() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        MvcResult result = mockMvc.perform(get("/api/v1/foods").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400, private"))
                .andExpect(jsonPath("$[0].nameTr").value("Mercimek çorbası"))
                .andExpect(jsonPath("$[0].nameEn").value("Red lentil soup"))
                .andExpect(jsonPath("$[0].servingG").value(250.0))
                .andReturn();
        JsonNode foods = read(result);
        assertThat(foods.size()).isGreaterThan(280);
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode food : foods) {
            String name = food.get("nameTr").asString();
            assertThat(names.add(name)).as("duplicate food %s", name).isTrue();
            assertThat(food.get("servingTr").asString()).isNotBlank();
            // Catches typos in the seed data: energy must roughly match 4 kcal/g protein and carbs, 9 kcal/g fat
            // (alcohol adds 7 kcal/g of its own, so those drinks are skipped)
            if (java.util.List.of("Bira", "Kırmızı şarap", "Rakı").contains(name)) continue;
            double kcal = food.get("kcalPer100g").asDouble();
            double fromMacros = 4 * food.get("proteinPer100g").asDouble() + 4 * food.get("carbsPer100g").asDouble()
                    + 9 * food.get("fatPer100g").asDouble();
            assertThat(Math.abs(kcal - fromMacros)).as("energy of %s: %s kcal vs %s from macros", name, kcal, fromMacros)
                    .isLessThanOrEqualTo(Math.max(25, kcal * 0.2));
        }
    }

    @Test
    void lolTrackerFollowsPlayersAndKeepsThemPrivate() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);

        authed(post("/api/v1/lol/players"), token, "{\"riotId\":\"bad-id\",\"platform\":\"TR1\"}")
                .andExpect(status().isBadRequest());
        authed(post("/api/v1/lol/players"), token, "{\"riotId\":\"Behlul#TR1\",\"platform\":\"XX9\"}")
                .andExpect(status().isBadRequest());
        authed(post("/api/v1/lol/players"), token, "{\"riotId\":\"notfound#TR1\",\"platform\":\"TR1\"}")
                .andExpect(status().isNotFound());

        // The fake Riot client answers every other Riot ID with the same generated data each time
        MvcResult tracked = authed(post("/api/v1/lol/players"), token, "{\"riotId\":\"Behlul K#TR1\",\"platform\":\"tr1\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameName").value("Behlul K"))
                .andExpect(jsonPath("$.platform").value("TR1"))
                .andExpect(jsonPath("$.refreshedAt").isNotEmpty())
                .andReturn();
        String puuid = read(tracked).get("puuid").asString();

        // Following the same player again is harmless
        authed(post("/api/v1/lol/players"), token, "{\"riotId\":\"Behlul K#TR1\",\"platform\":\"TR1\"}")
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/lol/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                // One refresh stores at most ten new matches
                .andExpect(jsonPath("$[0].recentGames").value(10));

        mockMvc.perform(get("/api/v1/lol/players/" + puuid).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.games").value(10))
                .andExpect(jsonPath("$.matches.length()").value(10))
                .andExpect(jsonPath("$.summary.form.length()").value(10));

        String stranger = register(randomEmail(), "s3cret-pass", null);
        mockMvc.perform(get("/api/v1/lol/players/" + puuid).header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/lol/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/v1/lol/players/" + puuid).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/lol/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void changingThePasswordSignsOutEveryOtherToken() throws Exception {
        String email = randomEmail();
        String firstDevice = register(email, "first-password-0", null);
        authed(post("/api/v1/workouts"), firstDevice, "{\"workoutDate\":\"2026-10-19\"}").andExpect(status().isCreated());
        authed(post("/api/v1/lol/players"), firstDevice, "{\"riotId\":\"Recover Me#TR1\",\"platform\":\"TR1\"}")
                .andExpect(status().isCreated());

        // A second device restoring with the code is just a login with the same credentials; both stay signed in
        String secondDevice = login(email, "first-password-0");
        me(firstDevice).andExpect(status().isOk());
        me(secondDevice).andExpect(status().isOk());

        MvcResult changed = authed(put("/api/v1/profile/password"), firstDevice,
                "{\"currentPassword\":\"first-password-0\",\"newPassword\":\"second-password-1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();
        String freshToken = read(changed).get("token").asString();

        me(firstDevice).andExpect(status().isUnauthorized());
        me(secondDevice).andExpect(status().isUnauthorized());
        me(freshToken).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'%s','password':'first-password-0'}", email)))
                .andExpect(status().isUnauthorized());

        // Same user id, so every per-user row comes back with the new code
        String restored = login(email, "second-password-1");
        mockMvc.perform(get("/api/v1/workouts").header(HttpHeaders.AUTHORIZATION, "Bearer " + restored))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/lol/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + restored))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void changePasswordNeedsTheCurrentPassword() throws Exception {
        String email = randomEmail();
        String token = register(email, "first-password-0", null);

        authed(put("/api/v1/profile/password"), token, "{\"currentPassword\":\"wrong-password\",\"newPassword\":\"second-password-1\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));

        // Nothing changed: the old token and the old password still work
        me(token).andExpect(status().isOk());
        login(email, "first-password-0");
    }

    @Test
    void changePasswordValidatesInputAndNeedsAToken() throws Exception {
        String token = register(randomEmail(), "first-password-0", null);

        mockMvc.perform(put("/api/v1/profile/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"first-password-0\",\"newPassword\":\"second-password-1\"}"))
                .andExpect(status().isUnauthorized());
        authed(put("/api/v1/profile/password"), token, "{\"currentPassword\":\"\",\"newPassword\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        authed(put("/api/v1/profile/password"), token,
                json("{'currentPassword':'first-password-0','newPassword':'%s'}", "x".repeat(73)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registeringAgainAfterAPasswordChangeConflicts() throws Exception {
        // The web app reads exactly this (login 401, then register 409) as "this device was signed out"
        String email = randomEmail();
        String token = register(email, "first-password-0", null);
        authed(put("/api/v1/profile/password"), token, "{\"currentPassword\":\"first-password-0\",\"newPassword\":\"second-password-1\"}")
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'%s','password':'first-password-0'}", email)))
                .andExpect(status().isConflict());
    }

    @Test
    void tokensAreCheckedAgainstTheTokenVersion() throws Exception {
        String token = register(randomEmail(), "first-password-0", null);
        String userId = read(me(token).andReturn()).get("id").asString();

        // Issued before versions existed: no "ver" claim, still accepted, so the deploy signed nobody out
        me(handMadeToken(userId, null)).andExpect(status().isOk());
        me(handMadeToken(userId, 0)).andExpect(status().isOk());
        me(handMadeToken(userId, 1)).andExpect(status().isUnauthorized());
        me(handMadeToken("999999999", 0)).andExpect(status().isUnauthorized());
    }

    private String handMadeToken(String subject, Integer version) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("liftlog").subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(3600));
        if (version != null) {
            claims.claim("ver", version);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'%s','password':'%s'}", email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("token").asString();
    }

    private org.springframework.test.web.servlet.ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String register(String email, String password, String displayName) throws Exception {
        String body = displayName == null
                ? json("{'email':'%s','password':'%s'}", email, password)
                : json("{'email':'%s','password':'%s','displayName':'%s'}", email, password, displayName);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return read(result).get("token").asString();
    }

    private org.springframework.test.web.servlet.ResultActions authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, String body)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Lets JSON bodies use single quotes for readability. */
    private static String json(String template, Object... args) {
        return String.format(template, args).replace('\'', '"');
    }

    private static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
