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
    var failWith: AppError? = null
    val calls = mutableListOf<String>()

    override suspend fun login(email: String, password: String): Result<Unit> {
        calls += "login:$email"
        return complete(email, null)
    }

    override suspend fun register(email: String, password: String, displayName: String?): Result<Unit> {
        calls += "register:$email:$displayName"
        return complete(email, displayName)
    }

    override suspend fun logout() {
        session.value = SessionState.LoggedOut
    }

    private fun complete(email: String, displayName: String?): Result<Unit> {
        failWith?.let { return Result.failure(AppException(it)) }
        session.value = SessionState.LoggedIn(Session("token", 1, email, displayName))
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
