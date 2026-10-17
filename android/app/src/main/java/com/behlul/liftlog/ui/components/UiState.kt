package com.behlul.liftlog.ui.components

import com.behlul.liftlog.domain.model.AppError

/** The three states every data-driven screen can be in. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val error: AppError) : UiState<Nothing>
}
