package com.behlul.liftlog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.behlul.liftlog.R
import com.behlul.liftlog.data.repository.AppException
import com.behlul.liftlog.domain.model.AppError
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 140.0 -> "140", 142.5 -> "142.5", 12500.0 -> "12,500" (locale-aware grouping). */
fun formatKg(value: Double, locale: Locale = Locale.getDefault()): String =
    (DecimalFormat.getNumberInstance(locale) as DecimalFormat).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }.format(value)

fun LocalDate.formatLong(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

fun LocalDate.shortMonth(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("MMM", locale)).uppercase(locale).trimEnd('.')

fun LocalDate.shortWeekday(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.')

fun Throwable.toAppError(): AppError = (this as? AppException)?.error ?: AppError.Unknown

@Composable
fun AppError.message(): String = when (this) {
    AppError.Network -> stringResource(R.string.error_network)
    AppError.NotFound -> stringResource(R.string.error_not_found)
    AppError.Server -> stringResource(R.string.error_server)
    AppError.Unauthorized -> stringResource(R.string.error_unauthorized)
    AppError.Unknown -> stringResource(R.string.error_unknown)
    is AppError.Validation -> detail ?: stringResource(R.string.error_invalid)
    is AppError.Conflict -> detail ?: stringResource(R.string.error_conflict)
}
