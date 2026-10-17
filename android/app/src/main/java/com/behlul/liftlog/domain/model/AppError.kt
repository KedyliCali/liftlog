package com.behlul.liftlog.domain.model

/** Failure reasons the UI knows how to explain; mapped to string resources in ui/components. */
sealed interface AppError {
    data object Network : AppError
    data object NotFound : AppError
    data object Unauthorized : AppError
    data object Server : AppError
    data object Unknown : AppError
    data class Validation(val detail: String?) : AppError
    data class Conflict(val detail: String?) : AppError
}
