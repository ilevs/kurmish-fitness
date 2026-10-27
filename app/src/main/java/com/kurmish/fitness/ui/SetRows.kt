package com.kurmish.fitness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurmish.fitness.R
import com.kurmish.fitness.core.EntryDraft
import com.kurmish.fitness.core.SetDraft
import com.kurmish.fitness.core.SetGroup

private val NumberWidth = 28.dp
private val RowGap = 8.dp
private const val PreviousWeight = 1.3f

/**
 * Подходы упражнения [entry] строками: подписи столбцов, строки, «Добавить подход» и «Изменить». Удалять подходы
 * можно только в режиме изменения, а удалённый подход до выхода из режима можно вернуть.
 */
@Composable
internal fun SetTable(entry: EntryDraft, previous: List<SetGroup>, onChange: (EntryDraft) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var removed by remember { mutableStateOf<Pair<Int, SetDraft>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
        SetHeader(editing = editing)
        entry.sets.forEachIndexed { index, set ->
            // Ключ меняется с числом подходов: после удаления строки не наследуют состояние соседей.
            key(entry.sets.size, index) {
                SetRow(
                    number = index + 1,
                    set = set,
                    previous = previous.getOrNull(index),
                    onChange = { changed ->
                        val sets = entry.sets.mapIndexed { i, old -> if (i == index) changed else old }
                        onChange(entry.copy(sets = sets))
                    },
                    onRemove = {
                        removed = index to set
                        onChange(entry.copy(sets = entry.sets.filterIndexed { i, _ -> i != index }))
                    }.takeIf { editing },
                )
            }
        }
        removed?.takeIf { editing }?.let { (index, set) ->
            RemovedRow(number = index + 1) {
                val at = index.coerceAtMost(entry.sets.size)
                onChange(entry.copy(sets = entry.sets.take(at) + set + entry.sets.drop(at)))
                removed = null
            }
        }
        SetActions(
            editing = editing,
            onAdd = { onChange(entry.withSet()) },
            onEdit = {
                editing = !editing
                removed = null
            },
        )
    }
}

/** Под подходами: «Добавить подход» и переключатель режима изменения. */
@Composable
private fun SetActions(editing: Boolean, onAdd: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Spacer(Modifier.width(RowGap))
            Text(stringResource(R.string.editor_add_set))
        }
        TextButton(onClick = onEdit) {
            Text(stringResource(if (editing) R.string.editor_ok else R.string.editor_sets_edit))
        }
    }
}

/** Строка на месте удалённого подхода с кнопкой «Вернуть». */
@Composable
private fun RemovedRow(number: Int, onUndo: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.editor_set_removed, number),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUndo) { Text(stringResource(R.string.today_restore)) }
    }
}

/** Подписи столбцов под строками подходов: №, прошлый раз, кг, повторы, отметка (в режиме изменения пусто). */
@Composable
private fun SetHeader(editing: Boolean) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(RowGap), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.editor_set_number),
            modifier = Modifier.width(NumberWidth),
            color = color,
            textAlign = TextAlign.Center,
            style = style,
        )
        Text(
            text = stringResource(R.string.editor_previous),
            modifier = Modifier.weight(PreviousWeight),
            color = color,
            style = style,
        )
        Text(
            text = stringResource(R.string.design_kg),
            modifier = Modifier.weight(1f),
            color = color,
            textAlign = TextAlign.Center,
            style = style,
        )
        Text(
            text = stringResource(R.string.editor_reps),
            modifier = Modifier.weight(1f),
            color = color,
            textAlign = TextAlign.Center,
            style = style,
        )
        Text(
            text = if (editing) "" else "✓",
            modifier = Modifier.minimumInteractiveComponentSize(),
            color = color,
            textAlign = TextAlign.Center,
            style = style,
        )
    }
}

/**
 * Подход строкой: номер, прошлый раз (нажатие переносит его в поля), вес, повторы и отметка «сделан».
 * В режиме изменения ([onRemove] не null) вместо отметки кнопка удаления.
 */
@Composable
private fun SetRow(
    number: Int,
    set: SetDraft,
    previous: SetGroup?,
    onChange: (SetDraft) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val background = if (set.done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.small),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            modifier = Modifier.width(NumberWidth),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
        )
        PreviousCell(previous = previous, onUse = onChange, modifier = Modifier.weight(PreviousWeight))
        NumberField(
            value = set.weight,
            placeholder = stringResource(R.string.editor_weight_own),
            error = set.done && set.weightInvalid,
            decimal = true,
            onChange = { onChange(set.edited(weight = it)) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = set.reps,
            placeholder = previous?.reps?.toString().orEmpty(),
            error = set.broken && set.repsInvalid,
            onChange = { onChange(set.edited(reps = it)) },
            modifier = Modifier.weight(1f),
        )
        if (onRemove == null) {
            Checkbox(checked = set.done, onCheckedChange = { onChange(set.copy(done = it)) })
        } else {
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.editor_remove_set),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PreviousCell(previous: SetGroup?, onUse: (SetDraft) -> Unit, modifier: Modifier = Modifier) {
    val text = previous?.label() ?: "—"
    Text(
        text = text,
        modifier = modifier.clickable(enabled = previous != null) {
            previous?.let {
                val weight = it.weightKg?.let(SetDraft::formatWeight).orEmpty()
                onUse(SetDraft(weight = weight, reps = it.reps.toString(), done = true))
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun NumberField(
    value: String,
    error: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    decimal: Boolean = false,
) {
    val centered = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        textStyle = centered,
        placeholder = { Text(placeholder, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}
