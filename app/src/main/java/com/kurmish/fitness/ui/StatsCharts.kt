package com.kurmish.fitness.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurmish.fitness.R
import com.kurmish.fitness.core.MaxPoint
import com.kurmish.fitness.core.WeekStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val ChartHeight = 160.dp
private val LineWidth = 2.dp
private val DotRadius = 4.dp
private val BarGap = 2.dp
internal val BarCorner = 4.dp
private const val PERCENT = 100
private const val WeekLabelEvery = 3
private const val FaintAlpha = 0.45f
private const val GridAlpha = 0.3f
private const val Tenths = 10.0
private val ShortDate = DateTimeFormatter.ofPattern("d.MM")

/** Что показывает график нагрузки по неделям. */
internal enum class WeekMetric(@StringRes val title: Int) {
    Tonnage(R.string.stats_metric_tonnage),
    Lifts(R.string.stats_metric_lifts),
    Intensity(R.string.stats_metric_intensity),
    Sessions(R.string.stats_metric_sessions),
}

@Composable
internal fun MaxChart(points: List<MaxPoint>) {
    var picked by remember(points) { mutableIntStateOf(points.lastIndex) }
    val point = points[picked.coerceIn(points.indices)]
    val change = points.last().kg - points.first().kg
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(stringResource(R.string.stats_max_point, point.kg.roundedKg(), point.epochDay.dayTitle()))
        if (points.size > 1) {
            Hint(stringResource(R.string.stats_max_change, change.signedKg(), points.first().epochDay.shortDate()))
        }
        val low = points.minOf { it.kg }
        val high = points.maxOf { it.kg }
        val first = points.first().epochDay
        val span = (points.last().epochDay - first).coerceAtLeast(1)
        val line = MaterialTheme.colorScheme.primary
        val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = GridAlpha)
        val surface = MaterialTheme.colorScheme.surfaceVariant
        val description = stringResource(R.string.stats_max_chart, low.roundedKg(), high.roundedKg())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.height(ChartHeight), verticalArrangement = Arrangement.SpaceBetween) {
                Hint(high.roundedKg())
                Hint(low.roundedKg())
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(ChartHeight)
                    .padding(start = Gap)
                    .semantics { contentDescription = description }
                    .pointerInput(points) {
                        detectTapGestures { tap ->
                            val day = first + (tap.x / size.width * span).roundToInt()
                            picked = points.indices.minBy { abs(points[it].epochDay - day) }
                        }
                    },
            ) {
                val dot = DotRadius.toPx()
                val range = (high - low).takeIf { it > 0 } ?: 1.0
                fun at(p: MaxPoint) = Offset(
                    x = if (points.size == 1) size.width / 2 else (p.epochDay - first).toFloat() / span * size.width,
                    y = dot + ((high - p.kg) / range).toFloat() * (size.height - 2 * dot),
                )
                listOf(dot, size.height - dot).forEach { y ->
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val path = Path()
                points.forEachIndexed { index, p ->
                    val o = at(p)
                    if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, line, style = Stroke(width = LineWidth.toPx(), cap = StrokeCap.Round))
                val selected = at(point)
                drawCircle(surface, radius = dot + LineWidth.toPx(), center = selected)
                drawCircle(line, radius = dot, center = selected)
            }
        }
    }
}

@Composable
internal fun WeekBars(weeks: List<WeekStats>, metric: WeekMetric) {
    var picked by remember(weeks) { mutableIntStateOf(weeks.lastIndex) }
    val values = weeks.map { it.value(metric) ?: 0.0 }
    val week = weeks[picked.coerceIn(weeks.indices)]
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(
            stringResource(
                R.string.stats_week_value,
                week.weekStart.shortDate(),
                week.label(metric) ?: stringResource(R.string.stats_no_value),
            ),
        )
        val top = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val bar = MaterialTheme.colorScheme.primary
        val baseline = MaterialTheme.colorScheme.outlineVariant
        val description = stringResource(R.string.stats_weeks_chart, stringResource(metric.title))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics { contentDescription = description }
                .pointerInput(weeks) {
                    detectTapGestures { tap ->
                        picked = (tap.x / size.width * weeks.size).toInt().coerceIn(weeks.indices)
                    }
                },
        ) {
            val slot = size.width / values.size
            val gap = BarGap.toPx()
            values.forEachIndexed { index, value ->
                val height = (value / top).toFloat() * size.height
                if (height > 0f) {
                    drawRoundRect(
                        color = if (index == picked) bar else bar.copy(alpha = FaintAlpha),
                        topLeft = Offset(index * slot + gap, size.height - height),
                        size = Size(slot - 2 * gap, height),
                        cornerRadius = CornerRadius(BarCorner.toPx()),
                    )
                }
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weeks.forEachIndexed { index, item ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if ((weeks.lastIndex - index) % WeekLabelEvery == 0) Hint(item.weekStart.shortDate())
                }
            }
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun WeekStats.value(metric: WeekMetric): Double? = when (metric) {
    WeekMetric.Tonnage -> tonnageKg
    WeekMetric.Lifts -> lifts.toDouble()
    WeekMetric.Intensity -> relativeIntensity?.let { it * PERCENT }
    WeekMetric.Sessions -> sessions.toDouble()
}

private fun WeekStats.label(metric: WeekMetric): String? = when (metric) {
    WeekMetric.Tonnage -> tonnageKg.tonnage()
    WeekMetric.Lifts -> "$lifts"
    WeekMetric.Intensity -> relativeIntensity?.let { "${(it * PERCENT).roundToInt()} %" }
    WeekMetric.Sessions -> "$sessions"
}

private fun Long.shortDate(): String = LocalDate.ofEpochDay(this).format(ShortDate)

private fun Double.signedKg(): String {
    val rounded = (this * Tenths).roundToInt() / Tenths
    val number = "${abs(rounded)}".removeSuffix(".0").replace('.', ',')
    return when {
        rounded > 0 -> "+$number кг"
        rounded < 0 -> "−$number кг"
        else -> "0 кг"
    }
}
