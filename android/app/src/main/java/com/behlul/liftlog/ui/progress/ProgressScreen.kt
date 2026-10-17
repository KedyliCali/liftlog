package com.behlul.liftlog.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behlul.liftlog.R
import com.behlul.liftlog.domain.model.PersonalRecord
import com.behlul.liftlog.domain.model.ProgressPoint
import com.behlul.liftlog.domain.model.WeeklyVolume
import com.behlul.liftlog.ui.components.ErrorView
import com.behlul.liftlog.ui.components.LoadingView
import com.behlul.liftlog.ui.components.MessageView
import com.behlul.liftlog.ui.components.UiState
import com.behlul.liftlog.ui.components.formatKg
import com.behlul.liftlog.ui.theme.NumberStyle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    ProgressContent(state, onRetry = viewModel::load, onSelectExercise = viewModel::selectExercise)
}

@Composable
fun ProgressContent(state: ProgressUiState, onRetry: () -> Unit, onSelectExercise: (Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.progress_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (val overview = state.overview) {
            UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(overview.error, onRetry)
            is UiState.Success -> if (overview.data.records.isEmpty()) {
                MessageView(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = stringResource(R.string.progress_empty_title),
                    body = stringResource(R.string.progress_empty_body),
                )
            } else {
                ProgressBody(state, overview.data, onSelectExercise)
            }
        }
    }
}

@Composable
private fun ProgressBody(state: ProgressUiState, overview: ProgressOverview, onSelectExercise: (Long) -> Unit) {
    // Vico picks its line/column colors from the Material theme
    ProvideVicoTheme(rememberM3VicoTheme()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().testTag("progress_list"),
        ) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(overview.records, key = { it.exerciseId }) { record ->
                        FilterChip(
                            selected = record.exerciseId == state.selectedExerciseId,
                            onClick = { onSelectExercise(record.exerciseId) },
                            label = { Text(record.exerciseName) },
                        )
                    }
                }
            }
            state.selectedRecord?.let { record -> item { RecordHighlight(record) } }
            item { OneRepMaxCard(state.progress) }
            item { VolumeCard(overview.weeklyVolume) }
            item {
                Text(
                    stringResource(R.string.pr_all_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            items(overview.records, key = { "pr_${it.exerciseId}" }) { record -> RecordRow(record) }
        }
    }
}

/** Two stat tiles; the trophy badge overlaps the corner of the first one. */
@Composable
private fun RecordHighlight(record: PersonalRecord) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
            StatTile(
                label = stringResource(R.string.pr_heaviest),
                value = stringResource(R.string.pr_reps, formatKg(record.heaviestWeightKg), record.heaviestReps),
                date = record.heaviestDate,
                emphasized = false,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.pr_e1rm),
                value = stringResource(R.string.weight_kg, formatKg(record.bestEstimated1RmKg)),
                date = record.bestEstimated1RmDate,
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            Modifier
                .size(34.dp)
                .offset(x = (-6).dp)
                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, date: LocalDate, emphasized: Boolean, modifier: Modifier = Modifier) {
    val container = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(shape = RoundedCornerShape(20.dp), color = container, modifier = modifier) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            Text(value, style = NumberStyle.merge(MaterialTheme.typography.titleLarge), color = content)
            Text(date.shortDate(), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun OneRepMaxCard(progress: UiState<List<ProgressPoint>>) {
    ChartCard(
        title = stringResource(R.string.progress_chart_title),
        caption = stringResource(R.string.progress_chart_caption),
    ) {
        when (progress) {
            UiState.Loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Text(stringResource(R.string.error_unknown), color = MaterialTheme.colorScheme.error)
            is UiState.Success -> if (progress.data.size < 2) {
                Text(
                    stringResource(R.string.progress_need_more),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                OneRepMaxChart(progress.data)
            }
        }
    }
}

@Composable
private fun OneRepMaxChart(points: List<ProgressPoint>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        // x is the session index, so uneven gaps between workout dates don't squash the line
        modelProducer.runTransaction { lineModel { series(points.map { it.estimated1RmKg }) } }
    }
    val dateLabels = remember(points) {
        CartesianValueFormatter { _, x, _ -> points.getOrNull(x.toInt())?.date?.shortDate().orEmpty() }
    }
    // Starting the y-axis at 0 would flatten a 80 -> 94 kg climb into a straight line
    val yRange = remember(points) {
        val values = points.map { it.estimated1RmKg }
        CartesianLayerRangeProvider.fixed(
            minY = floor((values.min() - 5) / 5) * 5,
            maxY = ceil((values.max() + 5) / 5) * 5,
        )
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(rangeProvider = yRange),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateLabels),
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = fitAllZoom(),
        modifier = Modifier.fillMaxWidth().height(220.dp).testTag("e1rm_chart"),
    )
}

@Composable
private fun VolumeCard(weeks: List<WeeklyVolume>) {
    ChartCard(
        title = stringResource(R.string.progress_volume_title),
        caption = stringResource(R.string.progress_volume_caption),
    ) {
        val modelProducer = remember { CartesianChartModelProducer() }
        LaunchedEffect(weeks) {
            modelProducer.runTransaction { columnModel { series(weeks.map { it.volumeKg }) } }
        }
        val weekLabels = remember(weeks) {
            CartesianValueFormatter { _, x, _ -> weeks.getOrNull(x.toInt())?.weekStart?.shortDate().orEmpty() }
        }
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = weekLabels),
            ),
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = fitAllZoom(),
            modifier = Modifier.fillMaxWidth().height(200.dp).testTag("volume_chart"),
        )
    }
}

/** Shows every point at once: the charts sit inside a scrolling list, so horizontal panning would fight it. */
@Composable
private fun fitAllZoom() = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content, minZoom = Zoom.Content)

/** Chart container with a short accent tab above the title. */
@Composable
private fun ChartCard(title: String, caption: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(Modifier.size(width = 28.dp, height = 6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RecordRow(record: PersonalRecord) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.width(4.dp).height(32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(record.exerciseName, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.pr_reps, formatKg(record.heaviestWeightKg), record.heaviestReps),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(R.string.weight_kg, formatKg(record.bestEstimated1RmKg)),
                style = NumberStyle.merge(MaterialTheme.typography.titleMedium),
                color = MaterialTheme.colorScheme.primary,
            )
            Text("e1RM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LocalDate.shortDate(): String = format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
