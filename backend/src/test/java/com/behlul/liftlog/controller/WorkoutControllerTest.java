package com.behlul.liftlog.controller;

import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.service.RoutineService;
import com.behlul.liftlog.service.WorkoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks validation and the shared error format at the HTTP layer; the service is mocked. */
// Security filters are off here: these tests cover MVC mapping only; ApiIntegrationTest covers auth
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(WorkoutController.class)
class WorkoutControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WorkoutService workoutService;
    @MockitoBean private RoutineService routineService;

    @Test
    void addSetWithInvalidValuesReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/workouts/1/sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exerciseId": 1, "reps": 0, "weightKg": -5, "rpe": 11}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.reps").exists())
                .andExpect(jsonPath("$.fieldErrors.weightKg").exists())
                .andExpect(jsonPath("$.fieldErrors.rpe").exists());
        verifyNoInteractions(workoutService);
    }

    @Test
    void createWorkoutWithoutDateReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/workouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Leg day\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.workoutDate").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/workouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request"));
    }

    @Test
    void missingWorkoutReturns404InCommonFormat() throws Exception {
        when(workoutService.getWorkout(eq(99L))).thenThrow(new ResourceNotFoundException("Workout", 99L));

        mockMvc.perform(get("/api/v1/workouts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Workout 99 not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/workouts/99"));
    }

    @Test
    void addSetWithValidBodyReturns201() throws Exception {
        when(workoutService.addSet(eq(1L), any())).thenReturn(new com.behlul.liftlog.dto.SetResponse(
                318L, 1L, "Squat", 3, 5, new java.math.BigDecimal("140.00"), new java.math.BigDecimal("8.0"), false, false));

        mockMvc.perform(post("/api/v1/workouts/1/sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\": 1, \"reps\": 5, \"weightKg\": 140.0, \"rpe\": 8}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(318))
                .andExpect(jsonPath("$.exerciseName").value("Squat"))
                .andExpect(jsonPath("$.setOrder").value(3));
    }
}
