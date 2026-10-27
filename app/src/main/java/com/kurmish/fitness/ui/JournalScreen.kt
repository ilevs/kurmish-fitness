package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.ExerciseNames
import com.kurmish.fitness.core.LoadCalculator
import com.kurmish.fitness.core.LoadMetrics
import com.kurmish.fitness.core.ParsedWorkout
import com.kurmish.fitness.core.SessionDraft
import com.kurmish.fitness.core.WorkoutSession
import com.kurmish.fitness.core.customExerciseNames
import com.kurmish.fitness.core.previousSets
import kotlin.math.roundToInt

/**
 * Дневник: сверху строка ввода тренировки текстом или голосом, под ней план на сегодня, ниже записи,
 * новые первыми.
 * Запись и план открываются в редакторе.
 */
@Composable
internal fun JournalScreen(
    data: AppData,
    today: Long,
    actions: JournalActions,
    modifier: Modifier = Modifier,
) {
    val sessions = remember(data) {
        data.sessions.sortedWith(compareByDescending<WorkoutSession> { it.epochDay }.thenByDescending { it.id })
    }
    var editing by remember { mutableStateOf<SessionDraft?>(null) }
    val customNames = remember(data) { data.customExerciseNames() }
    val loads = remember(data, today) {
        data.sessions.associate { session ->
            session.id to LoadCalculator.session(session, data.profile.bodyweightKg) { data.loadMax(it, today) }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        item(key = "record") { RecordForm(onSave = actions.onRecord) }
        item(key = "today") {
            TodayPlan(
                data = data,
                today = today,
                onSpeak = actions.onSpeak,
                onChoose = actions.onChoose,
                onRecordPlan = { plan ->
                    val existing = data.sessions.filter { it.epochDay == today }.maxByOrNull { it.id }
                    editing = SessionDraft.fromPlan(plan, today, existing)
                },
                dietActions = actions.diet,
            )
        }
        item(key = "add") { AddButton(empty = sessions.isEmpty(), onClick = { editing = SessionDraft.new(today) }) }
        items(sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                load = loads[session.id],
                onEdit = { editing = SessionDraft.of(session) },
                onDelete = { actions.onDelete(session.id) },
            )
        }
    }
    editing?.let { draft ->
        SessionEditor(
            initial = draft,
            suggest = { query -> ExerciseNames.suggest(query, customNames) },
            previous = { entry, epochDay -> data.previousSets(entry, epochDay, draft.id) },
            onSave = { session ->
                actions.onSave(session)
                editing = null
            },
            onDelete = {
                actions.onDelete(draft.id)
                editing = null
            }.takeIf { draft.id != 0L },
            onDismiss = { editing = null },
        )
    }
}

/**
 * Что Дневник делает с данными.
 *
 * @property onRecord сохранить тренировку из текста или голоса.
 * @property onSave сохранить запись из редактора.
 * @property onDelete удалить запись по номеру.
 * @property onSpeak озвучить текст.
 * @property onChoose заменить упражнение плана на цикл; null возвращает исходное.
 * @property diet действия карточки питания.
 */
internal class JournalActions(
    val onRecord: (String, ParsedWorkout) -> Unit,
    val onSave: (WorkoutSession) -> Unit,
    val onDelete: (Long) -> Unit,
    val onSpeak: (String) -> Unit,
    val onChoose: (slot: String, exercise: Exercise?) -> Unit,
    val diet: DietActions,
)

@Composable
private fun AddButton(empty: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        FilledTonalButton(onClick = onClick) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Spacer(Modifier.width(Gap))
            Text(stringResource(R.string.journal_add))
        }
        Text(
            text = stringResource(if (empty) R.string.journal_empty else R.string.journal_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionCard(session: WorkoutSession, load: LoadMetrics?, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(ScreenPadding), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
                Text(text = session.epochDay.dayTitle(), style = MaterialTheme.typography.titleMedium)
                session.entries.forEach { entry -> Text(entry.label()) }
                feelingLabel(session.feeling)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (load != null && load.lifts > 0) LoadLines(load)
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.journal_edit))
                }
                IconButton(onClick = { confirming = true }) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.journal_delete)
                    )
                }
            }
        }
    }
    if (confirming) ConfirmDelete(epochDay = session.epochDay, onConfirm = onDelete, onDismiss = { confirming = false })
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

/** Спрашивает, точно ли удалить запись за день [epochDay]. */
@Composable
internal fun ConfirmDelete(epochDay: Long, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journal_delete)) },
        text = { Text(stringResource(R.string.editor_delete_confirm, epochDay.dayTitle())) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(stringResource(R.string.editor_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

private const val PERCENT = 100
