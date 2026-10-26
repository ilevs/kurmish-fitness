package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.LoadCalculator
import com.kurmish.fitness.core.LoadMetrics
import com.kurmish.fitness.core.WorkoutSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru-RU"))

/** Дневник тренировок, новые сверху, и синхронизация с Telegram над ними. */
@Composable
internal fun JournalScreen(
    data: AppData,
    today: Long,
    onDelete: (Long) -> Unit,
    telegram: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions = remember(data) {
        data.sessions.sortedWith(compareByDescending<WorkoutSession> { it.epochDay }.thenByDescending { it.id })
    }
    val loads = remember(data, today) {
        data.sessions.associate { session ->
            session.id to LoadCalculator.session(session) { data.currentMax(it, today) }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        item(key = "telegram") { telegram() }
        if (sessions.isEmpty()) {
            item(key = "empty") { Text(text = stringResource(R.string.journal_empty)) }
        }
        items(sessions, key = { it.id }) { session -> SessionCard(session, loads[session.id], onDelete) }
    }
}

@Composable
private fun SessionCard(session: WorkoutSession, load: LoadMetrics?, onDelete: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(ScreenPadding), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
                val date = LocalDate.ofEpochDay(session.epochDay).format(DateFormat)
                Text(text = date.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.titleMedium)
                session.entries.forEach { entry -> Text(entry.label()) }
                feelingLabel(session.feeling)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (load != null && load.lifts > 0) LoadLines(load)
            }
            IconButton(onClick = { onDelete(session.id) }) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.journal_delete)
                )
            }
        }
    }
}

@Composable
private fun LoadLines(load: LoadMetrics) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Text(
            text = stringResource(
                R.string.journal_load,
                load.tonnageKg.tonnage(),
                load.lifts,
                load.averageKg.roundedKg(),
                load.minutes,
            ),
            style = style,
            color = color,
        )
        load.relativeIntensity?.let { intensity ->
            Text(
                text = stringResource(
                    R.string.journal_intensity,
                    (intensity * PERCENT).roundToInt(),
                    load.weightedIntensity.tonnage(),
                ),
                style = style,
                color = color,
            )
        }
    }
}

private const val PERCENT = 100
