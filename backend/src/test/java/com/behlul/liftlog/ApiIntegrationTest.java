package com.behlul.liftlog;

import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.entity.LolRankHistory;
import com.behlul.liftlog.repository.LolGroupRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.repository.LolRankHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
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
    @Autowired private LolPlayerRepository playerRepository;
    @Autowired private LolGroupRepository groupRepository;
    @Autowired private LolRankHistoryRepository rankHistoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

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
    void builtInExercisesIncludeMachinesAndCablesWithTheirEquipment() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        JsonNode exercises = read(mockMvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        java.util.Map<String, String> equipment = new java.util.HashMap<>();
        for (int i = 0; i < exercises.size(); i++) {
            JsonNode exercise = exercises.get(i);
            equipment.put(exercise.get("name").asString(),
                    exercise.get("equipment").isNull() ? null : exercise.get("equipment").asString());
        }
        assertThat(equipment).containsEntry("Seated Cable Row", "CABLE").containsEntry("Pec Deck", "MACHINE")
                .containsEntry("Leg Press", "MACHINE").containsEntry("Lat Pulldown", "CABLE")
                .containsEntry("Squat", "BARBELL").containsEntry("Pull-up", "BODYWEIGHT").containsEntry("Hammer Curl", "DUMBBELL");
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
    void routinesCanBeCreatedListedReplacedAndDeleted() throws Exception {
        String owner = register(randomEmail(), "s3cret-pass", null);
        String intruder = register(randomEmail(), "s3cret-pass", null);
        long squat = exerciseId(owner, "Squat");
        long bench = exerciseId(owner, "Bench Press");

        long routineId = read(authed(post("/api/v1/routines"), owner, json(
                "{'name':' Legs ','exercises':[{'exerciseId':%d,'sets':3,'targetReps':5},"
                        + "{'exerciseId':%d,'sets':2,'targetReps':8}]}", squat, bench))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Legs"))
                .andExpect(jsonPath("$.exercises.length()").value(2))
                .andExpect(jsonPath("$.exercises[0].exerciseId").value(squat))
                .andExpect(jsonPath("$.exercises[0].name").value("Squat"))
                .andExpect(jsonPath("$.exercises[0].bodyweight").value(false))
                .andExpect(jsonPath("$.exercises[0].sets").value(3))
                .andExpect(jsonPath("$.exercises[0].targetReps").value(5))
                .andReturn()).get("id").asLong();

        mockMvc.perform(get("/api/v1/routines").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(routineId));

        // PUT replaces the whole list, here in a new order
        authed(put("/api/v1/routines/" + routineId), owner, json(
                "{'name':'Legs B','exercises':[{'exerciseId':%d,'sets':4,'targetReps':6},"
                        + "{'exerciseId':%d,'sets':3,'targetReps':5}]}", bench, squat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Legs B"))
                .andExpect(jsonPath("$.exercises[0].name").value("Bench Press"))
                .andExpect(jsonPath("$.exercises[0].sets").value(4))
                .andExpect(jsonPath("$.exercises[1].name").value("Squat"));
        mockMvc.perform(get("/api/v1/routines").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$[0].exercises.length()").value(2))
                .andExpect(jsonPath("$[0].exercises[0].name").value("Bench Press"));

        // Another user's routine is a 404 everywhere
        mockMvc.perform(get("/api/v1/routines").header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder))
                .andExpect(jsonPath("$.length()").value(0));
        authed(put("/api/v1/routines/" + routineId), intruder, json(
                "{'name':'Mine','exercises':[{'exerciseId':%d,'sets':1,'targetReps':1}]}", squat))
                .andExpect(status().isNotFound());
        authed(post("/api/v1/routines/" + routineId + "/start"), intruder, "{}")
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/routines/" + routineId).header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/routines/" + routineId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/routines").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void routineExercisesMustBeVisibleAndWithinLimits() throws Exception {
        String owner = register(randomEmail(), "s3cret-pass", null);
        String other = register(randomEmail(), "s3cret-pass", null);
        long othersExercise = read(authed(post("/api/v1/exercises"), other,
                json("{'name':'Secret %s'}", UUID.randomUUID().toString().substring(0, 6)))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        authed(post("/api/v1/routines"), owner, json(
                "{'name':'Legs','exercises':[{'exerciseId':1,'sets':3,'targetReps':5},"
                        + "{'exerciseId':%d,'sets':3,'targetReps':5}]}", othersExercise))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['exercises[1].exerciseId']").value("exercise not found"));
        authed(post("/api/v1/routines"), owner,
                "{\"name\":\"Legs\",\"exercises\":[{\"exerciseId\":999999,\"sets\":3,\"targetReps\":5}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['exercises[0].exerciseId']").exists());
        authed(post("/api/v1/routines"), owner,
                "{\"name\":\" \",\"exercises\":[{\"exerciseId\":1,\"sets\":11,\"targetReps\":0}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors['exercises[0].sets']").exists())
                .andExpect(jsonPath("$.fieldErrors['exercises[0].targetReps']").exists());
        authed(post("/api/v1/routines"), owner, "{\"name\":\"Empty\",\"exercises\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.exercises").exists());
        mockMvc.perform(get("/api/v1/routines").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void workoutStartedFromARoutineHasAPlanWithProgression() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        String intruder = register(randomEmail(), "s3cret-pass", null);
        long squat = exerciseId(token, "Squat");
        long bench = exerciseId(token, "Bench Press");
        long pullUp = exerciseId(token, "Pull-up");

        // An older session (ignored: not the most recent) and a later one (ignored: after the new workout)
        long older = createWorkout(token, "2026-10-01", null);
        addSet(token, older, squat, 5, "90");
        long later = createWorkout(token, "2026-10-20", null);
        addSet(token, later, squat, 1, "200");

        // The session the routine is saved from: squat 3 x 5 done, bench 2 x 8 done
        long source = createWorkout(token, "2026-10-10", "Push pull");
        addSet(token, source, squat, 5, "100");
        addSet(token, source, bench, 8, "60");
        addSet(token, source, squat, 5, "100");
        addSet(token, source, bench, 8, "60");
        addSet(token, source, squat, 5, "100");

        long routineId = read(authed(post("/api/v1/routines/from-workout/" + source), token, "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Push pull"))
                .andExpect(jsonPath("$.exercises.length()").value(2))
                .andExpect(jsonPath("$.exercises[0].name").value("Squat"))
                .andExpect(jsonPath("$.exercises[0].sets").value(3))
                .andExpect(jsonPath("$.exercises[0].targetReps").value(5))
                .andExpect(jsonPath("$.exercises[1].name").value("Bench Press"))
                .andExpect(jsonPath("$.exercises[1].sets").value(2))
                .andExpect(jsonPath("$.exercises[1].targetReps").value(8))
                .andReturn()).get("id").asLong();
        authed(post("/api/v1/routines/from-workout/" + source), intruder, "{}").andExpect(status().isNotFound());

        // Bench now planned for 3 sets, so the 2-set session falls short; pull-ups were never done
        authed(put("/api/v1/routines/" + routineId), token, json(
                "{'name':'Push pull','exercises':[{'exerciseId':%d,'sets':3,'targetReps':5},"
                        + "{'exerciseId':%d,'sets':3,'targetReps':8},{'exerciseId':%d,'sets':3,'targetReps':10}]}",
                squat, bench, pullUp))
                .andExpect(status().isOk());

        long workoutId = read(authed(post("/api/v1/routines/" + routineId + "/start"), token,
                "{\"workoutDate\":\"2026-10-12\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routineId").value(routineId))
                .andExpect(jsonPath("$.notes").value("Push pull"))
                .andExpect(jsonPath("$.workoutDate").value("2026-10-12"))
                .andExpect(jsonPath("$.sets", empty()))
                .andReturn()).get("id").asLong();
        mockMvc.perform(get("/api/v1/workouts/" + workoutId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.routineId").value(routineId));
        mockMvc.perform(get("/api/v1/workouts/" + source).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.routineId").value(nullValue()));

        plan(token, workoutId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].exerciseId").value(squat))
                .andExpect(jsonPath("$[0].name").value("Squat"))
                .andExpect(jsonPath("$[0].bodyweight").value(false))
                .andExpect(jsonPath("$[0].plannedSets").value(3))
                .andExpect(jsonPath("$[0].loggedSets").value(0))
                .andExpect(jsonPath("$[0].targetReps").value(5))
                .andExpect(jsonPath("$[0].suggestion").value("INCREASE"))
                .andExpect(jsonPath("$[0].suggestedWeightKg").value(102.5))
                .andExpect(jsonPath("$[0].lastWeightKg").value(100.0))
                .andExpect(jsonPath("$[0].lastReps", contains(5, 5, 5)))
                .andExpect(jsonPath("$[0].increaseKg").value(2.5))
                .andExpect(jsonPath("$[0].suggestedReps").value(5))
                .andExpect(jsonPath("$[1].suggestion").value("SAME"))
                .andExpect(jsonPath("$[1].suggestedWeightKg").value(60.0))
                .andExpect(jsonPath("$[1].lastReps", contains(8, 8)))
                .andExpect(jsonPath("$[1].increaseKg").value(nullValue()))
                .andExpect(jsonPath("$[2].name").value("Pull-up"))
                .andExpect(jsonPath("$[2].bodyweight").value(true))
                .andExpect(jsonPath("$[2].suggestion").value("FIRST"))
                .andExpect(jsonPath("$[2].suggestedWeightKg").value(nullValue()))
                .andExpect(jsonPath("$[2].lastWeightKg").value(nullValue()))
                .andExpect(jsonPath("$[2].lastReps", empty()))
                .andExpect(jsonPath("$[2].suggestedReps").value(10));

        // A workout created later on the same day is not "previous": strictly earlier workouts only
        long sameDayLater = createWorkout(token, "2026-10-12", null);
        addSet(token, sameDayLater, squat, 1, "150");
        addSet(token, sameDayLater, pullUp, 5, "0");

        // Ticking a planned set logs it; the suggestion still compares against the earlier session
        addSet(token, workoutId, squat, 5, "102.5");
        plan(token, workoutId)
                .andExpect(jsonPath("$[0].loggedSets").value(1))
                .andExpect(jsonPath("$[0].suggestion").value("INCREASE"))
                .andExpect(jsonPath("$[0].suggestedWeightKg").value(102.5))
                .andExpect(jsonPath("$[1].loggedSets").value(0))
                .andExpect(jsonPath("$[2].suggestion").value("FIRST"));

        // ...while one created earlier on the same day is
        long sameDayEarlier = createWorkout(token, "2026-10-12", null);
        long viewedLater = read(authed(post("/api/v1/routines/" + routineId + "/start"), token,
                "{\"workoutDate\":\"2026-10-12\"}").andExpect(status().isCreated()).andReturn()).get("id").asLong();
        addSet(token, sameDayEarlier, squat, 5, "50");
        plan(token, viewedLater)
                .andExpect(jsonPath("$[0].lastWeightKg").value(50.0))
                .andExpect(jsonPath("$[0].lastReps", contains(5)))
                .andExpect(jsonPath("$[0].suggestion").value("SAME")); // 1 set of the 3 planned

        // Someone else's plan, and a workout without a routine, are 404
        plan(intruder, workoutId).andExpect(status().isNotFound());
        plan(token, source).andExpect(status().isNotFound());

        // Deleting the routine keeps the workout and its sets, without a plan
        mockMvc.perform(delete("/api/v1/routines/" + routineId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/workouts/" + workoutId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routineId").value(nullValue()))
                .andExpect(jsonPath("$.sets.length()").value(1));
        plan(token, workoutId).andExpect(status().isNotFound());
    }

    @Test
    void bodyweightOnlyExerciseProgressesByOneRep() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        long pushUp = exerciseId(token, "Push-up");
        long earlier = createWorkout(token, "2026-10-01", "Push-ups");
        addSet(token, earlier, pushUp, 15, "0");
        addSet(token, earlier, pushUp, 15, "0");

        long routineId = read(authed(post("/api/v1/routines/from-workout/" + earlier), token, "")
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long workoutId = read(authed(post("/api/v1/routines/" + routineId + "/start"), token,
                "{\"workoutDate\":\"2026-10-03\"}").andExpect(status().isCreated()).andReturn()).get("id").asLong();

        plan(token, workoutId)
                .andExpect(jsonPath("$[0].suggestion").value("INCREASE"))
                .andExpect(jsonPath("$[0].suggestedWeightKg").value(0.0))
                .andExpect(jsonPath("$[0].increaseKg").value(nullValue()))
                .andExpect(jsonPath("$[0].targetReps").value(15))
                .andExpect(jsonPath("$[0].suggestedReps").value(16));

        // Doing 16, 16 against the target of 15 moves the next suggestion to 17
        addSet(token, workoutId, pushUp, 16, "0");
        addSet(token, workoutId, pushUp, 16, "0");
        long next = read(authed(post("/api/v1/routines/" + routineId + "/start"), token,
                "{\"workoutDate\":\"2026-10-05\"}").andExpect(status().isCreated()).andReturn()).get("id").asLong();
        plan(token, next)
                .andExpect(jsonPath("$[0].suggestion").value("INCREASE"))
                .andExpect(jsonPath("$[0].lastReps", contains(16, 16)))
                .andExpect(jsonPath("$[0].suggestedReps").value(17));
    }

    private org.springframework.test.web.servlet.ResultActions plan(String token, long workoutId) throws Exception {
        return mockMvc.perform(get("/api/v1/workouts/" + workoutId + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private long createWorkout(String token, String date, String notes) throws Exception {
        String body = notes == null
                ? json("{'workoutDate':'%s'}", date)
                : json("{'workoutDate':'%s','notes':'%s'}", date, notes);
        return read(authed(post("/api/v1/workouts"), token, body)
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private void addSet(String token, long workoutId, long exerciseId, int reps, String weightKg) throws Exception {
        authed(post("/api/v1/workouts/" + workoutId + "/sets"), token,
                json("{'exerciseId':%d,'reps':%d,'weightKg':%s}", exerciseId, reps, weightKg))
                .andExpect(status().isCreated());
    }

    private long exerciseId(String token, String name) throws Exception {
        JsonNode exercises = read(mockMvc.perform(get("/api/v1/exercises")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn());
        for (JsonNode e : exercises) {
            if (e.get("name").asString().equals(name)) {
                return e.get("id").asLong();
            }
        }
        throw new AssertionError("No exercise " + name);
    }

    @Test
    void workoutSummaryHasRecordsComparisonAndStreak() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        String intruder = register(randomEmail(), "s3cret-pass", null);
        long squat = exerciseId(token, "Squat");
        long bench = exerciseId(token, "Bench Press");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Same exercises two weeks ago (the comparison), squat only last week (different exercises: ignored)
        long sameExercises = createWorkout(token, today.minusWeeks(2).toString(), null);
        addSet(token, sameExercises, squat, 5, "100");
        addSet(token, sameExercises, bench, 5, "80");
        long squatOnly = createWorkout(token, today.minusWeeks(1).toString(), null);
        addSet(token, squatOnly, squat, 5, "100");

        long workoutId = createWorkout(token, today.toString(), null);
        addSet(token, workoutId, squat, 3, "110");   // heavier than ever: weight record
        addSet(token, workoutId, bench, 8, "80");    // same weight, more reps: estimated 1RM record
        addSet(token, workoutId, squat, 5, "90");    // nothing beaten

        mockMvc.perform(get("/api/v1/workouts/" + workoutId + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVolumeKg").value(1420.0))
                .andExpect(jsonPath("$.sets").value(3))
                .andExpect(jsonPath("$.exercises").value(2))
                .andExpect(jsonPath("$.personalRecords.length()").value(2))
                .andExpect(jsonPath("$.personalRecords[0].exerciseName").value("Squat"))
                .andExpect(jsonPath("$.personalRecords[0].weightKg").value(110.0))
                .andExpect(jsonPath("$.personalRecords[0].reps").value(3))
                .andExpect(jsonPath("$.personalRecords[0].kind").value("WEIGHT"))
                .andExpect(jsonPath("$.personalRecords[1].exerciseName").value("Bench Press"))
                .andExpect(jsonPath("$.personalRecords[1].kind").value("ESTIMATED_1RM"))
                .andExpect(jsonPath("$.previousWorkoutId").value(sameExercises))
                .andExpect(jsonPath("$.previousVolumeKg").value(900.0))
                .andExpect(jsonPath("$.weekStreak").value(3));

        // The first workout has no history: no records and nothing to compare with
        mockMvc.perform(get("/api/v1/workouts/" + sameExercises + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalRecords", empty()))
                .andExpect(jsonPath("$.previousWorkoutId", nullValue()))
                .andExpect(jsonPath("$.previousVolumeKg", nullValue()));

        // An empty workout still has a summary
        long empty = createWorkout(token, today.toString(), null);
        mockMvc.perform(get("/api/v1/workouts/" + empty + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVolumeKg").value(0))
                .andExpect(jsonPath("$.sets").value(0))
                .andExpect(jsonPath("$.muscles", empty()));

        mockMvc.perform(get("/api/v1/workouts/" + workoutId + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/workouts/999999999/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void everyBuiltInExerciseHasAPrimaryMuscle() {
        // Queried in SQL because the API would hide a missing seed row behind the muscle-group fallback
        List<String> withoutPrimary = jdbcTemplate.queryForList("""
                select e.name from exercises e
                where e.user_id is null and not exists (
                    select 1 from exercise_muscles m where m.exercise_id = e.id and m.role = 'PRIMARY')
                order by e.name
                """, String.class);
        assertThat(withoutPrimary).isEmpty();

        Integer builtIns = jdbcTemplate.queryForObject("select count(*) from exercises where user_id is null", Integer.class);
        assertThat(builtIns).isEqualTo(76);
    }

    @Test
    void exercisesListTheirMusclesBuiltInAndCustom() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        String backName = "Rack Pull " + UUID.randomUUID().toString().substring(0, 6);
        authed(post("/api/v1/exercises"), token, json("{'name':'%s','muscleGroup':'Back'}", backName))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.muscles.length()").value(2))
                .andExpect(jsonPath("$.muscles[0].muscle").value("LATS"))
                .andExpect(jsonPath("$.muscles[0].role").value("PRIMARY"));
        String noGroupName = "Odd Thing " + UUID.randomUUID().toString().substring(0, 6);
        authed(post("/api/v1/exercises"), token, json("{'name':'%s'}", noGroupName))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.muscles", empty()));

        JsonNode exercises = read(mockMvc.perform(get("/api/v1/exercises").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        java.util.Map<String, List<String>> muscles = new java.util.HashMap<>();
        for (JsonNode exercise : exercises) {
            List<String> entries = new ArrayList<>();
            exercise.get("muscles").forEach(m -> entries.add(m.get("muscle").asString() + ":" + m.get("role").asString()));
            muscles.put(exercise.get("name").asString(), entries);
        }
        assertThat(muscles.get("Bench Press")).containsExactly("CHEST:PRIMARY", "FRONT_DELTS:SECONDARY", "TRICEPS:SECONDARY");
        assertThat(muscles.get("Face Pull")).containsExactly("REAR_DELTS:PRIMARY", "TRAPS:SECONDARY");
        // Adductors are drawn inside the quads region, so the API only speaks in map regions
        assertThat(muscles.get("Hip Adduction Machine")).containsExactly("QUADS:PRIMARY");
        assertThat(muscles.get("Squat")).containsExactly("GLUTES:PRIMARY", "QUADS:PRIMARY", "LOWER_BACK:SECONDARY");
        assertThat(muscles.get(backName)).containsExactly("LATS:PRIMARY", "TRAPS:SECONDARY");
        assertThat(muscles.get(noGroupName)).isEmpty();
        assertThat(muscles.values()).allSatisfy(list -> assertThat(list).noneMatch(
                entry -> entry.startsWith("ADDUCTORS") || entry.startsWith("ABDUCTORS")));
    }

    @Test
    void muscleMapCountsTheCallersSetsInTheWindowAndTheSummary() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        String other = register(randomEmail(), "s3cret-pass", null);
        LocalDate today = LocalDate.of(2026, 10, 19);
        long bench = exerciseId(token, "Bench Press");
        long pullUp = exerciseId(token, "Pull-up");
        long squat = exerciseId(token, "Squat");
        long deadlift = exerciseId(token, "Deadlift");
        long lateralRaise = exerciseId(token, "Lateral Raise");
        long legExtension = exerciseId(token, "Leg Extension");
        long customArms = read(authed(post("/api/v1/exercises"), token,
                json("{'name':'Arm Combo %s','muscleGroup':'Arms'}", UUID.randomUUID().toString().substring(0, 6)))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        // Today: bench 3, pull-up 2 (bodyweight, counts like any set), custom arms 2
        long todayWorkout = createWorkout(token, today.toString(), null);
        for (int i = 0; i < 3; i++) {
            addSet(token, todayWorkout, bench, 5, "80");
        }
        addSet(token, todayWorkout, pullUp, 8, "0");
        addSet(token, todayWorkout, pullUp, 6, "0");
        addSet(token, todayWorkout, customArms, 10, "20");
        addSet(token, todayWorkout, customArms, 10, "20");
        // First day of the 7-day window, then just outside it (still inside 30 days)
        long weekEdge = createWorkout(token, today.minusDays(6).toString(), null);
        addSet(token, weekEdge, squat, 5, "100");
        addSet(token, weekEdge, squat, 5, "100");
        long lastWeek = createWorkout(token, today.minusDays(7).toString(), null);
        addSet(token, lastWeek, deadlift, 3, "140");
        // First day of the 30-day window, then outside it on both sides
        long monthEdge = createWorkout(token, today.minusDays(29).toString(), null);
        addSet(token, monthEdge, lateralRaise, 12, "10");
        long tooOld = createWorkout(token, today.minusDays(30).toString(), null);
        long tomorrow = createWorkout(token, today.plusDays(1).toString(), null);
        for (int i = 0; i < 5; i++) {
            addSet(token, tooOld, legExtension, 10, "50");
            addSet(token, tomorrow, legExtension, 10, "50");
        }
        // Someone else's bench sets on the same day are not ours
        long othersWorkout = createWorkout(other, today.toString(), null);
        for (int i = 0; i < 10; i++) {
            addSet(other, othersWorkout, bench, 5, "100");
        }

        JsonNode week = read(mockMvc.perform(get("/api/v1/stats/muscles?days=7&today=" + today)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.from").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andReturn());
        assertThat(muscleSets(week.get("muscles"))).containsExactly(
                "CHEST=3.0", "TRICEPS=2.5", "BICEPS=2.0", "LATS=2.0", "GLUTES=2.0", "QUADS=2.0",
                "FRONT_DELTS=1.5", "REAR_DELTS=1.0", "FOREARMS=1.0", "LOWER_BACK=1.0");
        JsonNode triceps = week.get("muscles").get(1);
        assertThat(triceps.get("exercises").size()).isEqualTo(2);
        assertThat(triceps.get("exercises").get(0).get("name").asString()).isEqualTo("Bench Press");
        assertThat(triceps.get("exercises").get(0).get("sets").asDouble()).isEqualTo(1.5);
        assertThat(triceps.get("exercises").get(1).get("name").asString()).startsWith("Arm Combo");
        assertThat(triceps.get("exercises").get(1).get("sets").asDouble()).isEqualTo(1.0);

        JsonNode month = read(mockMvc.perform(get("/api/v1/stats/muscles?days=30&today=" + today)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(today.minusDays(29).toString()))
                .andReturn());
        // + deadlift 1 (hamstrings, glutes, lower back 1; traps, forearms, lats 0.5) + lateral raise 1
        assertThat(muscleSets(month.get("muscles"))).containsExactly(
                "CHEST=3.0", "GLUTES=3.0", "TRICEPS=2.5", "LATS=2.5", "BICEPS=2.0", "LOWER_BACK=2.0", "QUADS=2.0",
                "FRONT_DELTS=1.5", "FOREARMS=1.5", "SIDE_DELTS=1.0", "REAR_DELTS=1.0", "TRAPS=1.0", "HAMSTRINGS=1.0");

        mockMvc.perform(get("/api/v1/stats/muscles?days=14").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.days").exists());
        mockMvc.perform(get("/api/v1/stats/muscles?days=7&today=yesterday").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/stats/muscles?days=7"))
                .andExpect(status().isUnauthorized());

        JsonNode summary = read(mockMvc.perform(get("/api/v1/workouts/" + todayWorkout + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        assertThat(muscleSets(summary.get("muscles"))).containsExactly(
                "CHEST=3.0", "TRICEPS=2.5", "BICEPS=2.0", "LATS=2.0", "FRONT_DELTS=1.5", "REAR_DELTS=1.0",
                "FOREARMS=1.0");
    }

    private static List<String> muscleSets(JsonNode muscles) {
        List<String> result = new ArrayList<>();
        muscles.forEach(m -> result.add(m.get("muscle").asString() + "=" + m.get("sets").asString()));
        return result;
    }

    @Test
    void workoutSummaryComparesWithTheSameRoutineFirst() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        long squat = exerciseId(token, "Squat");
        long bench = exerciseId(token, "Bench Press");

        long source = createWorkout(token, "2026-09-01", "Legs");
        addSet(token, source, squat, 5, "100");
        long routineId = read(authed(post("/api/v1/routines/from-workout/" + source), token, "{}")
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        long first = startRoutine(token, routineId, "2026-09-08");
        addSet(token, first, squat, 5, "100");
        addSet(token, first, bench, 5, "60");
        long second = startRoutine(token, routineId, "2026-09-15");

        // Not the source workout with the same exercises: the earlier session of the same routine wins
        addSet(token, second, squat, 5, "100");
        mockMvc.perform(get("/api/v1/workouts/" + second + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousWorkoutId").value(first))
                .andExpect(jsonPath("$.previousVolumeKg").value(800.0));
    }

    private long startRoutine(String token, long routineId, String date) throws Exception {
        return read(authed(post("/api/v1/routines/" + routineId + "/start"), token,
                json("{'workoutDate':'%s'}", date))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    @Test
    void weeklyStreakCountsConsecutiveWeeksWithAWorkout() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        mockMvc.perform(get("/api/v1/stats/streak").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks").value(0))
                .andExpect(jsonPath("$.thisWeekWorkouts").value(0))
                .andExpect(jsonPath("$.longestWeeks").value(0));

        // This week twice, last week once, then a gap two weeks ago that breaks the run
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        createWorkout(token, today.toString(), null);
        createWorkout(token, today.toString(), null);
        createWorkout(token, today.minusWeeks(1).toString(), null);
        createWorkout(token, today.minusWeeks(3).toString(), null);

        mockMvc.perform(get("/api/v1/stats/streak").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks").value(2))
                .andExpect(jsonPath("$.thisWeekWorkouts").value(2))
                .andExpect(jsonPath("$.longestWeeks").value(2));
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
    void copyingADayDuplicatesItsEntriesForTheCallerOnly() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        String copyUrl = "/api/v1/nutrition/days/" + today + "/copy-from/" + yesterday;
        authed(post("/api/v1/nutrition/entries"), token,
                json("{'entryDate':'%s','name':'Oats','caloriesKcal':380,'proteinG':13.5,'carbsG':60.2,'fatG':6.5}", yesterday))
                .andExpect(status().isCreated());
        authed(post("/api/v1/nutrition/entries"), token,
                json("{'entryDate':'%s','name':'Protein shake','caloriesKcal':120,'proteinG':24}", yesterday))
                .andExpect(status().isCreated());

        // A stranger copying "their" yesterday gets nothing: the source is read per user
        String stranger = register(randomEmail(), "s3cret-pass", null);
        authed(post(copyUrl), stranger, "").andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(0));

        JsonNode copies = read(authed(post(copyUrl), token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Oats"))
                .andExpect(jsonPath("$[0].entryDate").value(today))
                .andExpect(jsonPath("$[0].carbsG").value(60.2))
                .andExpect(jsonPath("$[1].carbsG").doesNotExist())
                .andReturn());
        mockMvc.perform(get("/api/v1/nutrition/days/" + today).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.totalCaloriesKcal").value(500));

        // Undo on the client deletes the returned ids: they are new rows, so the source day is untouched
        for (JsonNode copy : copies) {
            mockMvc.perform(delete("/api/v1/nutrition/entries/" + copy.get("id").asLong())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/v1/nutrition/days/" + yesterday).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.entries.length()").value(2));
        mockMvc.perform(get("/api/v1/nutrition/days/" + today).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.entries.length()").value(0));

        authed(post("/api/v1/nutrition/days/" + today + "/copy-from/" + today), token, "").andExpect(status().isBadRequest());
        authed(post("/api/v1/nutrition/days/" + today + "/copy-from/not-a-date"), token, "").andExpect(status().isBadRequest());
        mockMvc.perform(post(copyUrl)).andExpect(status().isUnauthorized());
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
                .andExpect(jsonPath("$[0].recentGames").value(10))
                .andExpect(jsonPath("$[0].summary.kda").isNumber())
                .andExpect(jsonPath("$[0].summary.killParticipation").isNumber())
                .andExpect(jsonPath("$[0].summary.visionPerMinute").isNumber());

        MvcResult detail = mockMvc.perform(get("/api/v1/lol/players/" + puuid).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.games").value(10))
                .andExpect(jsonPath("$.matches.length()").value(10))
                .andExpect(jsonPath("$.summary.form.length()").value(10))
                // The fake client fills every detail, so the new numbers are all there
                .andExpect(jsonPath("$.matches[0].items.length()").value(7))
                .andExpect(jsonPath("$.matches[0].summonerSpells.length()").value(2))
                .andExpect(jsonPath("$.matches[0].gold").isNumber())
                .andExpect(jsonPath("$.matches[0].vision").isNumber())
                .andExpect(jsonPath("$.matches[0].killParticipation").isNumber())
                .andExpect(jsonPath("$.matches[0].damageShare").isNumber())
                .andExpect(jsonPath("$.matches[0].largestMultiKill").isNumber())
                .andExpect(jsonPath("$.matches[0].gameMode").value("CLASSIC"))
                .andExpect(jsonPath("$.summary.killParticipation").isNumber())
                .andExpect(jsonPath("$.summary.goldPerMinute").isNumber())
                .andExpect(jsonPath("$.summary.streak.count").isNumber())
                .andExpect(jsonPath("$.summary.roles").isArray())
                .andExpect(jsonPath("$.summary.queues").isArray())
                .andExpect(jsonPath("$.summary.champions[0].damagePerMinute").isNumber())
                .andExpect(jsonPath("$.summary.records.mostKills.matchId").isNotEmpty())
                .andReturn();

        // One point after the first refresh when ranked; the fake leaves some players unranked, who record nothing
        int expectedPoints = read(detail).get("player").get("rank").isNull() ? 0 : 1;
        mockMvc.perform(get("/api/v1/lol/players/" + puuid + "/rank-history").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(expectedPoints));

        String stranger = register(randomEmail(), "s3cret-pass", null);
        mockMvc.perform(get("/api/v1/lol/players/" + puuid).header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/lol/players/" + puuid + "/rank-history").header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/lol/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/v1/lol/players/" + puuid).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/lol/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rankHistoryKeepsOnlyTheNewestRows() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String puuid = "history-" + UUID.randomUUID();
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 10, 1, 12, 0);
        tx.executeWithoutResult(status -> {
            playerRepository.save(new LolPlayer(puuid, "TR1", "History", "TR1"));
            for (int i = 0; i < 4; i++) {
                rankHistoryRepository.save(new LolRankHistory(puuid, start.plusHours(i), "GOLD", "II", 10 * i, i, 0));
            }
        });

        // Fewer rows than kept: nothing goes
        tx.executeWithoutResult(status -> assertThat(rankHistoryRepository.deleteAllButNewest(puuid, 10)).isZero());
        tx.executeWithoutResult(status -> assertThat(rankHistoryRepository.deleteAllButNewest(puuid, 2)).isEqualTo(2));

        assertThat(rankHistoryRepository.findByPuuidOrderByRecordedAtAsc(puuid))
                .extracting(LolRankHistory::getLp).containsExactly(20, 30);
        assertThat(rankHistoryRepository.findFirstByPuuidOrderByRecordedAtDesc(puuid))
                .hasValueSatisfying(h -> assertThat(h.getLp()).isEqualTo(30));
    }

    @Test
    void followingACachedPlayerOnAnotherServerMovesThem() throws Exception {
        String token = register(randomEmail(), "s3cret-pass", null);
        String riotId = uniqueRiotId("Move");
        authed(post("/api/v1/lol/players"), token, "{\"riotId\":\"" + riotId + "\",\"platform\":\"TR1\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.platform").value("TR1"));

        // Same account (the fake keeps the PUUID, as Riot does across a transfer), now asked for on EUW1
        authed(post("/api/v1/lol/players"), token, "{\"riotId\":\"" + riotId + "\",\"platform\":\"EUW1\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.platform").value("EUW1"))
                .andExpect(jsonPath("$.refreshedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/lol/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].platform").value("EUW1"));
    }

    private static final String GROUP = "/api/v1/lol/group";

    @Test
    void lolGroupSharesOneBoardThroughAnInviteCode() throws Exception {
        Account owner = newAccount("Behlul");
        Account friend = newAccount(null);
        Account stranger = newAccount(null);

        asUser(get(GROUP), owner).andExpect(status().isNoContent());

        MvcResult created = authed(post(GROUP), owner.token(), "{\"name\":\" Duo Queue \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Duo Queue"))
                .andExpect(jsonPath("$.inviteCode").value(matchesPattern("^[A-HJKMNP-Z2-9]{10}$")))
                .andExpect(jsonPath("$.maxMembers").value(20))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].you").value(true))
                .andExpect(jsonPath("$.members[0].owner").value(true))
                .andExpect(jsonPath("$.members[0].player").value(nullValue()))
                .andReturn();
        String code = read(created).get("inviteCode").asString();

        String ownerRiotId = uniqueRiotId("Own");
        authed(put(GROUP + "/me/account"), owner.token(), riotIdBody(ownerRiotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].player.gameName").value(ownerRiotId.substring(0, ownerRiotId.indexOf('#'))))
                .andExpect(jsonPath("$.members[0].player.recentGames").value(10));

        // Typed the way people do: lowercase, with the dash the app shows
        String typed = (code.substring(0, 5) + "-" + code.substring(5)).toLowerCase();
        for (int attempt = 0; attempt < 2; attempt++) {
            // Joining again (a double tap) is harmless
            joinGroup(friend, typed)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.members.length()").value(2))
                    .andExpect(jsonPath("$.members[0].displayName").value("Behlul"))
                    .andExpect(jsonPath("$.members[1].you").value(true))
                    .andExpect(jsonPath("$.members[1].owner").value(false));
        }
        authed(put(GROUP + "/me/account"), friend.token(), riotIdBody(uniqueRiotId("Fri"))).andExpect(status().isOk());

        JsonNode ownerView = read(asUser(get(GROUP), owner).andExpect(status().isOk()).andReturn());
        JsonNode friendView = read(asUser(get(GROUP), friend).andExpect(status().isOk()).andReturn());
        assertThat(linkedPuuids(ownerView)).hasSize(2).isEqualTo(linkedPuuids(friendView));
        String ownerPuuid = linkedPuuids(ownerView).getFirst();

        // The friend never followed the owner's account, but the group makes its page visible
        asUser(get("/api/v1/lol/players/" + ownerPuuid), friend).andExpect(status().isOk());
        asUser(get("/api/v1/lol/players/" + ownerPuuid + "/rank-history"), friend).andExpect(status().isOk());
        asUser(get("/api/v1/lol/players"), friend).andExpect(jsonPath("$.length()").value(0));

        asUser(get("/api/v1/lol/players/" + ownerPuuid), stranger).andExpect(status().isNotFound());
        asUser(get("/api/v1/lol/players/" + ownerPuuid + "/rank-history"), stranger).andExpect(status().isNotFound());
        asUser(get(GROUP), stranger).andExpect(status().isNoContent());
    }

    @Test
    void lolGroupOwnerRulesRotationAndLeaving() throws Exception {
        Account owner = newAccount("Owner");
        Account member = newAccount("Member");
        Account stranger = newAccount(null);

        authed(post(GROUP), owner.token(), "{\"name\":\"   \"}").andExpect(status().isBadRequest());
        authed(post(GROUP), owner.token(), "{\"name\":\"" + "x".repeat(33) + "\"}").andExpect(status().isBadRequest());
        String code = read(authed(post(GROUP), owner.token(), "{\"name\":\"Duo\"}")
                .andExpect(status().isCreated()).andReturn()).get("inviteCode").asString();
        String ownerRiotId = uniqueRiotId("Own");
        String ownerPuuid = read(authed(put(GROUP + "/me/account"), owner.token(), riotIdBody(ownerRiotId))
                .andExpect(status().isOk()).andReturn()).get("members").get(0).get("player").get("puuid").asString();
        joinGroup(member, code).andExpect(status().isOk());

        authed(post(GROUP), member.token(), "{\"name\":\"Mine\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.group").value("ALREADY_IN_GROUP"));
        joinGroup(stranger, "AAAAA-AAAAA").andExpect(status().isNotFound());
        joinGroup(stranger, "O0I1L-XXXXX").andExpect(status().isNotFound());
        authed(put(GROUP + "/me/account"), member.token(), riotIdBody(ownerRiotId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.group").value("ACCOUNT_TAKEN"));

        // Owner-only actions
        authed(put(GROUP), member.token(), "{\"name\":\"Mine\"}").andExpect(status().isForbidden());
        asUser(post(GROUP + "/invite-code"), member).andExpect(status().isForbidden());
        asUser(delete(GROUP + "/members/" + owner.id()), member).andExpect(status().isForbidden());
        authed(put(GROUP), owner.token(), "{\"name\":\"Ranked\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ranked"));

        String rotated = read(asUser(post(GROUP + "/invite-code"), owner).andExpect(status().isOk()).andReturn())
                .get("inviteCode").asString();
        assertThat(rotated).isNotEqualTo(code);
        joinGroup(stranger, code).andExpect(status().isNotFound());

        asUser(delete(GROUP + "/members/" + owner.id()), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.group").value("CANNOT_REMOVE_SELF"));
        // Someone outside the group looks like a user that does not exist
        asUser(delete(GROUP + "/members/" + stranger.id()), owner).andExpect(status().isNotFound());

        asUser(delete(GROUP + "/members/" + member.id()), owner).andExpect(status().isNoContent());
        asUser(get(GROUP), member).andExpect(status().isNoContent());
        asUser(get("/api/v1/lol/players/" + ownerPuuid), member).andExpect(status().isNotFound());
        String afterRemoval = read(asUser(get(GROUP), owner).andReturn()).get("inviteCode").asString();
        assertThat(afterRemoval).isNotEqualTo(rotated);
        joinGroup(member, rotated).andExpect(status().isNotFound());

        // The member rejoins; when the owner leaves, ownership passes to them
        joinGroup(member, afterRemoval).andExpect(status().isOk());
        asUser(delete(GROUP + "/me"), owner).andExpect(status().isNoContent());
        asUser(get(GROUP), owner).andExpect(status().isNoContent());
        asUser(get(GROUP), member)
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].owner").value(true));

        // The last member out deletes the group, and its code with it
        asUser(delete(GROUP + "/me"), member).andExpect(status().isNoContent());
        asUser(delete(GROUP + "/me"), member).andExpect(status().isNotFound());
        joinGroup(stranger, afterRemoval).andExpect(status().isNotFound());
    }

    @Test
    void lolGroupsStayApart() throws Exception {
        Account ownerOne = newAccount("One");
        Account memberOne = newAccount(null);
        Account ownerTwo = newAccount("Two");
        Account memberTwo = newAccount(null);
        joinGroup(memberOne, createGroup(ownerOne, "First")).andExpect(status().isOk());
        joinGroup(memberTwo, createGroup(ownerTwo, "Second")).andExpect(status().isOk());

        // An account is only taken within a group, so the same one can be linked in both
        String shared = uniqueRiotId("Both");
        JsonNode boardOne = read(authed(put(GROUP + "/me/account"), memberOne.token(), riotIdBody(shared))
                .andExpect(status().isOk()).andReturn());
        JsonNode boardTwo = read(authed(put(GROUP + "/me/account"), memberTwo.token(), riotIdBody(shared))
                .andExpect(status().isOk()).andReturn());
        assertThat(linkedPuuids(boardOne)).hasSize(1).isEqualTo(linkedPuuids(boardTwo));
        String onlyInTwo = read(authed(put(GROUP + "/me/account"), ownerTwo.token(), riotIdBody(uniqueRiotId("Two")))
                .andExpect(status().isOk()).andReturn()).get("members").get(0).get("player").get("puuid").asString();

        // The other group's members look like users that do not exist, and stay where they are
        asUser(delete(GROUP + "/members/" + memberTwo.id()), ownerOne).andExpect(status().isNotFound());
        asUser(get(GROUP), memberTwo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Second"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[1].you").value(true));

        // Detail pages follow the group: an account linked only in the other group stays hidden
        asUser(get("/api/v1/lol/players/" + onlyInTwo), memberOne).andExpect(status().isNotFound());
        asUser(get("/api/v1/lol/players/" + onlyInTwo), memberTwo).andExpect(status().isOk());
    }

    @Test
    void groupRowLockMakesJoinsWaitTheirTurn() throws Exception {
        String code = createGroup(newAccount("Locker"), "Locked");
        Account joiner = newAccount(null);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            // Another change to this group holds the group row lock and keeps its transaction open
            Future<?> holder = threads.submit(() -> tx.executeWithoutResult(status -> {
                groupRepository.findLockedByInviteCode(code).orElseThrow();
                locked.countDown();
                awaitQuietly(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // The join waits for that lock before it counts the members, so it cannot take a seat in between
            Future<MvcResult> join = threads.submit(() -> joinGroup(joiner, code).andReturn());
            assertThatThrownBy(() -> join.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            MvcResult joined = join.get(10, TimeUnit.SECONDS);
            assertThat(joined.getResponse().getStatus()).isEqualTo(200);
            assertThat(read(joined).get("members").size()).isEqualTo(2);
        } finally {
            release.countDown();
            threads.shutdownNow();
        }
    }

    private String createGroup(Account owner, String name) throws Exception {
        return read(authed(post(GROUP), owner.token(), "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated()).andReturn()).get("inviteCode").asString();
    }

    private record Account(String token, long id) {
    }

    private Account newAccount(String displayName) throws Exception {
        String body = displayName == null
                ? json("{'email':'%s','password':'s3cret-pass'}", randomEmail())
                : json("{'email':'%s','password':'s3cret-pass','displayName':'%s'}", randomEmail(), displayName);
        JsonNode auth = read(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn());
        return new Account(auth.get("token").asString(), auth.get("user").get("id").asLong());
    }

    private org.springframework.test.web.servlet.ResultActions asUser(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Account account) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token()));
    }

    private org.springframework.test.web.servlet.ResultActions joinGroup(Account account, String code) throws Exception {
        return authed(post(GROUP + "/join"), account.token(), "{\"code\":\"" + code + "\"}");
    }

    /**
     * The fake Riot client derives the PUUID from the name, and players are shared by every test using this
     * database, so each test links its own made-up account.
     */
    private static String uniqueRiotId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6) + "#TR1";
    }

    private static String riotIdBody(String riotId) {
        return "{\"riotId\":\"" + riotId + "\",\"platform\":\"TR1\"}";
    }

    private static List<String> linkedPuuids(JsonNode group) {
        List<String> puuids = new ArrayList<>();
        JsonNode members = group.get("members");
        for (int i = 0; i < members.size(); i++) {
            JsonNode player = members.get(i).get("player");
            if (player != null && !player.isNull()) {
                puuids.add(player.get("puuid").asString());
            }
        }
        return puuids;
    }

    @Test
    void refreshClaimLetsOneRequestRefreshAPlayerAtATime() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String puuid = "claim-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        tx.executeWithoutResult(status -> playerRepository.save(new LolPlayer(puuid, "TR1", "Claim", "TR1")));

        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            // The first request claims the never-refreshed player and keeps its transaction open
            Future<Optional<String>> first = threads.submit(() -> tx.execute(status -> {
                Optional<String> claim = playerRepository.claimForRefresh(puuid, now);
                claimed.countDown();
                awaitQuietly(release);
                return claim;
            }));
            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();

            // A second request skips the row at once instead of waiting for the first one to commit
            Future<Optional<String>> second = threads.submit(() -> tx.execute(status ->
                    playerRepository.claimForRefresh(puuid, now)));
            assertThat(second.get(5, TimeUnit.SECONDS)).isEmpty();

            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).contains(puuid);
        } finally {
            release.countDown();
            threads.shutdownNow();
        }

        tx.executeWithoutResult(status -> playerRepository.findById(puuid).orElseThrow().markRefreshed(now));
        // Fresh data is not claimed; once it is older than the cut-off, it is again
        Optional<String> whileFresh = tx.execute(status -> playerRepository.claimForRefresh(puuid, now.minusMinutes(10)));
        Optional<String> onceStale = tx.execute(status -> playerRepository.claimForRefresh(puuid, now.plusMinutes(1)));
        assertThat(whileFresh).isEmpty();
        assertThat(onceStale).contains(puuid);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
