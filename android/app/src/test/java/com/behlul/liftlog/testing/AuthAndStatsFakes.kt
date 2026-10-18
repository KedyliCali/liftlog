package com.behlul.liftlog.testing

import com.behlul.liftlog.data.repository.AppException
import com.behlul.liftlog.data.repository.AuthRepository
import com.behlul.liftlog.data.repository.StatsRepository
import com.behlul.liftlog.domain.model.AppError
import com.behlul.liftlog.domain.model.PersonalRecord
import com.behlul.liftlog.domain.model.ProgressPoint
import com.behlul.liftlog.domain.model.Session
import com.behlul.liftlog.domain.model.SessionState
import com.behlul.liftlog.domain.model.WeeklyVolume
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class FakeAuthRepository : AuthRepository {
    override val session = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    /** Results handed out by successive signInDevice() calls; when empty, sign-in succeeds. */
    val failures = ArrayDeque<AppError>()
    var signInCalls = 0

    override suspend fun signInDevice(): Result<Unit> {
        signInCalls++
        failures.removeFirstOrNull()?.let { return Result.failure(AppException(it)) }
        session.value = SessionState.LoggedIn(Session("token", 42, "device@liftlog.device", null))
        return Result.success(Unit)
    }
}

class FakeStatsRepository : StatsRepository {
    var records = listOf(
        record(exerciseId = 1, name = "Squat", totalSets = 12),
        record(exerciseId = 9, name = "Bench Press", totalSets = 20),
    )
    val progressRequests = mutableListOf<Long>()

    override suspend fun getPersonalRecords() = Result.success(records)

    override suspend fun getProgress(exerciseId: Long): Result<List<ProgressPoint>> {
        progressRequests += exerciseId
        return Result.success(listOf(ProgressPoint(LocalDate.of(2026, 10, 1), 100.0, 116.67, 1500.0)))
    }

    override suspend fun getWeeklyVolume(weeks: Int) =
        Result.success(List(weeks) { WeeklyVolume(LocalDate.of(2026, 8, 24).plusWeeks(it.toLong()), 1000.0 * it, it) })

    companion object {
        fun record(exerciseId: Long, name: String, totalSets: Int) = PersonalRecord(
            exerciseId, name, 140.0, 3, LocalDate.of(2026, 10, 1), 154.0, LocalDate.of(2026, 10, 1), totalSets,
        )
    }
}
