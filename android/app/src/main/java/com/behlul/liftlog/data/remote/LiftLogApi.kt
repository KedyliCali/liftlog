package com.behlul.liftlog.data.remote

import com.behlul.liftlog.data.remote.dto.CreateExerciseRequestDto
import com.behlul.liftlog.data.remote.dto.ExerciseDto
import com.behlul.liftlog.data.remote.dto.PageDto
import com.behlul.liftlog.data.remote.dto.SetDto
import com.behlul.liftlog.data.remote.dto.SetRequestDto
import com.behlul.liftlog.data.remote.dto.WorkoutDetailDto
import com.behlul.liftlog.data.remote.dto.WorkoutRequestDto
import com.behlul.liftlog.data.remote.dto.WorkoutSummaryDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface LiftLogApi {

    @GET("api/v1/exercises")
    suspend fun getExercises(): List<ExerciseDto>

    @POST("api/v1/exercises")
    suspend fun createExercise(@Body request: CreateExerciseRequestDto): ExerciseDto

    @GET("api/v1/workouts")
    suspend fun getWorkouts(@Query("page") page: Int, @Query("size") size: Int): PageDto<WorkoutSummaryDto>

    @GET("api/v1/workouts/{id}")
    suspend fun getWorkout(@Path("id") id: Long): WorkoutDetailDto

    @POST("api/v1/workouts")
    suspend fun createWorkout(@Body request: WorkoutRequestDto): WorkoutDetailDto

    @PUT("api/v1/workouts/{id}")
    suspend fun updateWorkout(@Path("id") id: Long, @Body request: WorkoutRequestDto): WorkoutDetailDto

    @DELETE("api/v1/workouts/{id}")
    suspend fun deleteWorkout(@Path("id") id: Long)

    @POST("api/v1/workouts/{id}/sets")
    suspend fun addSet(@Path("id") workoutId: Long, @Body request: SetRequestDto): SetDto

    @PUT("api/v1/sets/{id}")
    suspend fun updateSet(@Path("id") id: Long, @Body request: SetRequestDto): SetDto

    @DELETE("api/v1/sets/{id}")
    suspend fun deleteSet(@Path("id") id: Long)
}
