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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/workouts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/workouts").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
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
