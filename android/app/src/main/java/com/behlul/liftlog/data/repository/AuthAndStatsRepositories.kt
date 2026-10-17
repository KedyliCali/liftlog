package com.behlul.liftlog.data.repository

import com.behlul.liftlog.data.local.SessionStore
import com.behlul.liftlog.data.remote.LiftLogApi
import com.behlul.liftlog.data.remote.dto.AuthResponseDto
import com.behlul.liftlog.data.remote.dto.LoginRequestDto
import com.behlul.liftlog.data.remote.dto.RegisterRequestDto
import com.behlul.liftlog.domain.model.PersonalRecord
import com.behlul.liftlog.domain.model.ProgressPoint
import com.behlul.liftlog.domain.model.Session
import com.behlul.liftlog.domain.model.SessionState
import com.behlul.liftlog.domain.model.WeeklyVolume
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    val session: StateFlow<SessionState>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(email: String, password: String, displayName: String?): Result<Unit>
    suspend fun logout()
}

interface StatsRepository {
    suspend fun getPersonalRecords(): Result<List<PersonalRecord>>
    suspend fun getProgress(exerciseId: Long): Result<List<ProgressPoint>>
    suspend fun getWeeklyVolume(weeks: Int): Result<List<WeeklyVolume>>
}

@Singleton
class RemoteAuthRepository @Inject constructor(
    private val api: LiftLogApi,
    private val json: Json,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override val session: StateFlow<SessionState> = sessionStore.state

    override suspend fun login(email: String, password: String) = apiCall(json) {
        store(api.login(LoginRequestDto(email.trim(), password)))
    }

    override suspend fun register(email: String, password: String, displayName: String?) = apiCall(json) {
        store(api.register(RegisterRequestDto(email.trim(), password, displayName?.trim()?.ifBlank { null })))
    }

    override suspend fun logout() = sessionStore.clear()

    private suspend fun store(response: AuthResponseDto) {
        sessionStore.save(Session(response.token, response.user.id, response.user.email, response.user.displayName))
    }
}

@Singleton
class RemoteStatsRepository @Inject constructor(
    private val api: LiftLogApi,
    private val json: Json,
) : StatsRepository {

    override suspend fun getPersonalRecords() = apiCall(json) {
        api.getPersonalRecords().map {
            PersonalRecord(
                exerciseId = it.exerciseId,
                exerciseName = it.exerciseName,
                heaviestWeightKg = it.heaviestWeightKg,
                heaviestReps = it.heaviestReps,
                heaviestDate = LocalDate.parse(it.heaviestDate),
                bestEstimated1RmKg = it.bestEstimated1RmKg,
                bestEstimated1RmDate = LocalDate.parse(it.bestEstimated1RmDate),
                totalSets = it.totalSets,
            )
        }
    }

    override suspend fun getProgress(exerciseId: Long) = apiCall(json) {
        api.getProgress(exerciseId).map {
            ProgressPoint(LocalDate.parse(it.date), it.topWeightKg, it.estimated1RmKg, it.volumeKg)
        }
    }

    override suspend fun getWeeklyVolume(weeks: Int) = apiCall(json) {
        api.getWeeklyVolume(weeks).map { WeeklyVolume(LocalDate.parse(it.weekStart), it.volumeKg, it.setCount) }
    }
}
