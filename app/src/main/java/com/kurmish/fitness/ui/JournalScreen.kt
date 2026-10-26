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
import com.kurmish.fitness.core.WorkoutSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru-RU"))

/** Журнал тренировок, новые сверху. */
@Composable
internal fun JournalScreen(data: AppData, onDelete: (Long) -> Unit, modifier: Modifier = Modifier) {
    val sessions = remember(
        data
    ) { data.sessions.sortedWith(compareByDescending<WorkoutSession> { it.epochDay }.thenByDescending { it.id }) }
    if (sessions.isEmpty()) {
        Text(text = stringResource(R.string.journal_empty), modifier = modifier.padding(ScreenPadding))
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            items(sessions, key = { it.id }) { session -> SessionCard(session, onDelete) }
        }
    }
}

@Composable
private fun SessionCard(session: WorkoutSession, onDelete: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(ScreenPadding), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
                val date = LocalDate.ofEpochDay(session.epochDay).format(DateFormat)
                Text(text = date.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.titleMedium)
                session.entries.forEach { entry -> Text(entry.label()) }
                feelingLabel(session.feeling)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
