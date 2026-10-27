package com.kurmish.fitness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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

/** Подходы упражнения [entry] строками: подписи столбцов, строки и «Добавить подход». */
@Composable
internal fun SetTable(entry: EntryDraft, previous: List<SetGroup>, onChange: (EntryDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
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
            Spacer(Modifier.width(RowGap))
            Text(stringResource(R.string.editor_add_set))
        }
    }
}

/** Подписи столбцов под строками подходов: №, прошлый раз, кг, повторы, отметка. */
@Composable
private fun SetHeader() {
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
            text = "✓",
            modifier = Modifier.minimumInteractiveComponentSize(),
            color = color,
            textAlign = TextAlign.Center,
            style = style,
        )
    }
}

/**
 * Подход строкой: номер, прошлый раз (нажатие переносит его в поля), вес, повторы и отметка «сделан».
 * Свайп в сторону удаляет подход.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetRow(
    number: Int,
    set: SetDraft,
    previous: SetGroup?,
    onChange: (SetDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onRemove()
            value != SwipeToDismissBoxValue.Settled
        },
    )
    SwipeToDismissBox(state = swipe, backgroundContent = { RemoveBackground() }) {
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
            Checkbox(checked = set.done, onCheckedChange = { onChange(set.copy(done = it)) })
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
private fun RemoveBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Gap),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.editor_remove_set),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
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
