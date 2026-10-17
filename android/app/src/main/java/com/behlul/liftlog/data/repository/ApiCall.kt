package com.behlul.liftlog.data.repository

import com.behlul.liftlog.data.remote.dto.ApiErrorDto
import com.behlul.liftlog.domain.model.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Exception carrying a typed [AppError] so ViewModels never have to inspect HTTP details. */
class AppException(val error: AppError) : Exception(error.toString())

/**
 * Runs a Retrofit call and translates transport/HTTP failures into [AppException].
 * Cancellation is rethrown untouched so leaving a screen still cancels its requests.
 */
internal suspend fun <T> apiCall(json: Json, block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    Result.failure(AppException(e.toAppError(json)))
} catch (e: IOException) {
    Result.failure(AppException(AppError.Network))
} catch (e: Exception) {
    Result.failure(AppException(AppError.Unknown))
}

private fun HttpException.toAppError(json: Json): AppError {
    val body = response()?.errorBody()?.string()
    val parsed = body?.let { runCatching { json.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
    return when (code()) {
        400 -> AppError.Validation(parsed?.fieldErrors?.values?.firstOrNull() ?: parsed?.message)
        401 -> AppError.Unauthorized
        404 -> AppError.NotFound
        409 -> AppError.Conflict(parsed?.message)
        in 500..599 -> AppError.Server
        else -> AppError.Unknown
    }
}
