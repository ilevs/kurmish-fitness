package com.kurmish.fitness.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kurmish.fitness.R
import com.kurmish.fitness.core.EntryDraft
import com.kurmish.fitness.core.Feeling
import com.kurmish.fitness.core.SessionDraft
import com.kurmish.fitness.core.SetGroup
import com.kurmish.fitness.core.WorkoutParser
import com.kurmish.fitness.core.WorkoutSession

private const val MillisInDay = 86_400_000L
private val SmallGap = 8.dp

/**
 * Полноэкранный редактор записи Дневника. Как в современных трекерах: упражнение и его подходы — одна
 * карточка, каждый подход — строка «вес · повторы · ✓», из плана строки уже заполнены и ждут отметки.
 *
 * @param suggest подсказки названий упражнений к введённому тексту.
 * @param previous подходы упражнения в прошлый раз до дня записи, по одному.
 * @param onDelete удаление записи; null для новой записи.
 */
@Composable
internal fun SessionEditor(
    initial: SessionDraft,
    suggest: (String) -> List<String>,
    previous: (EntryDraft, Long) -> List<SetGroup>,
    onSave: (WorkoutSession) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Gap),
            ) {
                TopActions(draft = draft, onSave = onSave, onDismiss = onDismiss)
                DateRow(epochDay = draft.epochDay, onChange = { draft = draft.copy(epochDay = it) })
                FeelingRow(feeling = draft.feeling, onChange = { draft = draft.copy(feeling = it) })
                draft.entries.forEachIndexed { index, entry ->
                    val before = remember(entry.exercise, entry.customName, draft.epochDay) {
                        previous(entry, draft.epochDay)
                    }
                    EntryCard(
                        entry = entry,
                        previous = before,
                        suggest = suggest,
                        onChange = { changed ->
                            val entries = draft.entries.mapIndexed { i, e -> if (i == index) changed else e }
                            draft = draft.copy(entries = entries)
                        },
                        onRemove = { draft = draft.copy(entries = draft.entries.filterIndexed { i, _ -> i != index }) },
                    )
                }
                OutlinedButton(onClick = { draft = draft.copy(entries = draft.entries + EntryDraft.empty()) }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(Gap))
                    Text(stringResource(R.string.editor_add_exercise))
                }
                RawTextField(draft = draft, onChange = { draft = it })
                if (onDelete != null) DeleteButton(epochDay = draft.epochDay, onDelete = onDelete)
            }
        }
    }
}

/** Наверху: отмена, заголовок и «Сохранить», ниже — что мешает сохранить. */
@Composable
private fun TopActions(draft: SessionDraft, onSave: (WorkoutSession) -> Unit, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
            val title = if (draft.id == 0L) R.string.editor_title_new else R.string.journal_edit
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { draft.toSession()?.let(onSave) }, enabled = draft.valid) {
                Text(stringResource(R.string.log_save))
            }
        }
        val problem = when {
            draft.entries.any { it.broken } -> R.string.editor_invalid
            draft.entries.all { it.skipped } -> R.string.editor_nothing_done
            else -> null
        }
        problem?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (it == R.string.editor_invalid) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun DateRow(epochDay: Long, onChange: (Long) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(epochDay - 1) }) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.editor_prev_day))
        }
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_calendar), stringResource(R.string.editor_pick_date))
            Spacer(Modifier.width(Gap))
            Text(epochDay.dayTitle())
        }
        IconButton(onClick = { onChange(epochDay + 1) }) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.editor_next_day))
        }
    }
    if (picking) {
        DayPicker(epochDay = epochDay, onPick = onChange, onDismiss = { picking = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(epochDay: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = epochDay * MillisInDay)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(it / MillisInDay) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.editor_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    ) { DatePicker(state = state) }
}

@Composable
private fun FeelingRow(feeling: Feeling, onChange: (Feeling) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Feeling.entries.forEach { option ->
            val title = when (option) {
                Feeling.Easy -> R.string.editor_feeling_easy
                Feeling.Normal -> R.string.editor_feeling_normal
                Feeling.Hard -> R.string.editor_feeling_hard
            }
            FilterChip(
                selected = option == feeling,
                onClick = { onChange(option) },
                label = { Text(stringResource(title)) },
            )
        }
    }
}

/** Карточка упражнения: название, меню и подходы строками. */
@Composable
private fun EntryCard(
    entry: EntryDraft,
    previous: List<SetGroup>,
    suggest: (String) -> List<String>,
    onChange: (EntryDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(SmallGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseField(entry = entry, suggest = suggest, onChange = onChange, modifier = Modifier.weight(1f))
                EntryMenu(onRemove = onRemove)
            }
            SetHeader()
            entry.sets.forEachIndexed { index, set ->
                // Ключ меняется с числом подходов: после удаления свайпом строки не наследуют его состояние.
                key(entry.sets.size, index) {
                    SetRow(
                        number = index + 1,
                        set = set,
                        previous = previous.getOrNull(index),
                        onChange = { changed ->
                            val sets = entry.sets.mapIndexed { i, old -> if (i == index) changed else old }
                            onChange(entry.copy(sets = sets))
                        },
                        onRemove = { onChange(entry.copy(sets = entry.sets.filterIndexed { i, _ -> i != index })) },
                    )
                }
            }
            TextButton(onClick = { onChange(entry.withSet()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(SmallGap))
                Text(stringResource(R.string.editor_add_set))
            }
        }
    }
}

@Composable
private fun EntryMenu(onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.editor_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_remove_exercise)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseField(
    entry: EntryDraft,
    suggest: (String) -> List<String>,
    onChange: (EntryDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(entry.name, expanded) { if (expanded) suggest(entry.name) else emptyList() }
    val open = expanded && options.isNotEmpty()
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = entry.name,
            onValueChange = {
                onChange(entry.named(it))
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            label = { Text(stringResource(R.string.editor_exercise)) },
            supportingText = if (entry.exercise == null && entry.name.isNotBlank()) {
                { Text(stringResource(R.string.editor_custom_hint)) }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            isError = entry.nameMissing,
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChange(entry.named(option))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RawTextField(draft: SessionDraft, onChange: (SessionDraft) -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    val understood = remember(draft.rawText) { WorkoutParser.parse(draft.rawText).entries.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        TextButton(onClick = { shown = !shown }) {
            Text(stringResource(if (shown) R.string.editor_text_hide else R.string.editor_text_show))
        }
        if (shown) {
            OutlinedTextField(
                value = draft.rawText,
                onValueChange = { onChange(draft.copy(rawText = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_text)) },
                supportingText = { Text(stringResource(R.string.editor_text_hint)) },
            )
            TextButton(onClick = { onChange(draft.reparsed()) }, enabled = understood) {
                Text(stringResource(R.string.editor_reparse))
            }
        }
    }
}

@Composable
private fun DeleteButton(epochDay: Long, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    TextButton(onClick = { confirming = true }) {
        Text(stringResource(R.string.journal_delete), color = MaterialTheme.colorScheme.error)
    }
    if (confirming) {
        ConfirmDelete(epochDay = epochDay, onConfirm = onDelete, onDismiss = { confirming = false })
    }
}
