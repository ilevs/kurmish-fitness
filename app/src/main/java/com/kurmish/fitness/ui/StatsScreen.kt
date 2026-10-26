package com.kurmish.fitness.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.Movement
import com.kurmish.fitness.core.Stats
import kotlin.math.roundToInt

private val BalanceBarHeight = 12.dp
private val LabelWidth = 132.dp
private const val PushPullImbalance = 1.5
private const val Tenths = 10.0

/** Статистика Дневника: сводка, динамика ПМ, нагрузка по неделям и баланс движений. */
@Composable
internal fun StatsScreen(data: AppData, today: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        if (data.sessions.isEmpty()) {
            Text(stringResource(R.string.stats_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SummaryCard(data = data, today = today)
            MaxCard(data = data)
            WeeksCard(data = data, today = today)
            BalanceCard(data = data, today = today)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SummaryCard(data: AppData, today: Long) {
    val summary = remember(data, today) { Stats.summary(data, today) }
    Section(stringResource(R.string.stats_summary)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Tile(summary.sessions.toString(), stringResource(R.string.stats_sessions), Modifier.weight(1f))
            Tile(summary.perWeek.oneDecimal(), stringResource(R.string.stats_per_week), Modifier.weight(1f))
            Tile(summary.tonnageKg.tonnage(), stringResource(R.string.stats_tonnage), Modifier.weight(1f))
        }
        summary.daysSinceLast?.let { days ->
            Text(
                text = when (days) {
                    0L -> stringResource(R.string.stats_last_today)
                    else -> stringResource(R.string.stats_last_days, days)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MaxCard(data: AppData) {
    val exercises = remember(data) { Stats.exercisesWithMaxes(data) }
    var chosen by rememberSaveable { mutableStateOf<Exercise?>(null) }
    val exercise = chosen?.takeIf { it in exercises } ?: exercises.firstOrNull()
    Section(stringResource(R.string.stats_max_title)) {
        if (exercise == null) {
            Hint(stringResource(R.string.stats_max_empty))
            return@Section
        }
        Chips(options = exercises.map { it to it.title }, selected = exercise, onSelect = { chosen = it })
        val points = remember(data, exercise) { Stats.maxHistory(data, exercise) }
        MaxChart(points = points)
        Hint(stringResource(R.string.stats_max_hint))
    }
}

@Composable
private fun WeeksCard(data: AppData, today: Long) {
    val weeks = remember(data, today) { Stats.weeks(data, today) }
    var metric by rememberSaveable { mutableStateOf(WeekMetric.Tonnage) }
    Section(stringResource(R.string.stats_weeks_title)) {
        Chips(
            options = WeekMetric.entries.map { it to stringResource(it.title) },
            selected = metric,
            onSelect = { metric = it },
        )
        WeekBars(weeks = weeks, metric = metric)
        if (metric == WeekMetric.Intensity) Hint(stringResource(R.string.stats_intensity_hint))
    }
}

@Composable
private fun BalanceCard(data: AppData, today: Long) {
    val balance = remember(data, today) { Stats.balance(data, today) }
    val top = balance.values.maxOrNull() ?: 0
    Section(stringResource(R.string.stats_balance_title)) {
        if (top == 0) {
            Hint(stringResource(R.string.stats_balance_empty))
            return@Section
        }
        val bar = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.surfaceVariant
        balance.forEach { (movement, reps) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
                Text(movement.title, modifier = Modifier.width(LabelWidth), style = MaterialTheme.typography.bodySmall)
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(BalanceBarHeight),
                ) {
                    val radius = CornerRadius(BarCorner.toPx())
                    drawRoundRect(track, cornerRadius = radius)
                    if (reps > 0) {
                        drawRoundRect(bar, size = Size(size.width * reps / top, size.height), cornerRadius = radius)
                    }
                }
                Text(reps.toString(), style = MaterialTheme.typography.bodySmall)
            }
        }
        Hint(balanceAdvice(balance))
    }
}

@Composable
private fun balanceAdvice(balance: Map<Movement, Int>): String {
    val push = balance.filterKeys { it.isPush }.values.sum()
    val pull = balance.filterKeys { it.isPull }.values.sum()
    return when {
        push > pull * PushPullImbalance -> stringResource(R.string.stats_balance_more_push)
        pull > push * PushPullImbalance -> stringResource(R.string.stats_balance_more_pull)
        else -> stringResource(R.string.stats_balance_ok)
    }
}

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Gap)
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

private fun Double.oneDecimal(): String = "${(this * Tenths).roundToInt() / Tenths}".replace('.', ',')
