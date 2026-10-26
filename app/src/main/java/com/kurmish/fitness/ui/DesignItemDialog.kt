package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardType.Companion.Decimal
import com.kurmish.fitness.R
import com.kurmish.fitness.core.DesignItem
import com.kurmish.fitness.core.DesignWeek
import com.kurmish.fitness.core.Planner

/**
 * Упражнение своего цикла: название и нагрузка на микроцикл [week] — схема и вес процентами от ПМ
 * или в килограммах. Галочка «во всех микроциклах» переносит ту же нагрузку на весь цикл.
 */
@Composable
internal fun DesignItemDialog(
    item: DesignItem,
    week: Int,
    onSave: (DesignItem) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = item.weeks.getOrNull(week)
    var name by remember(item) { mutableStateOf(item.name) }
    var present by remember(item) { mutableStateOf(current != null) }
    var scheme by remember(item) { mutableStateOf(current?.scheme.orEmpty()) }
    var inPercents by remember(item) { mutableStateOf(current?.kg == null) }
    var load by remember(item) { mutableStateOf(current?.loadText().orEmpty()) }
    var everywhere by remember(item) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val value = DesignWeek.parse(scheme, load, inPercents).takeIf { present }
                    onSave(item.copy(name = name.trim()).withWeek(week, value, everywhere))
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.editor_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
        title = { Text(stringResource(R.string.design_week, week + 1)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Gap),
            ) {
                NameField(name = name, onChange = { name = it })
                CheckRow(checked = present, text = stringResource(R.string.design_present), onChange = { present = it })
                if (present) {
                    LoadFields(
                        scheme = scheme,
                        onScheme = { scheme = it },
                        inPercents = inPercents,
                        onPercents = { inPercents = it },
                        load = load,
                        onLoad = { load = it },
                    )
                }
                CheckRow(
                    checked = everywhere,
                    text = stringResource(R.string.design_everywhere),
                    onChange = { everywhere = it },
                )
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.editor_remove_exercise), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

private fun DesignWeek.loadText(): String = kg?.let { "$it".removeSuffix(".0").replace('.', ',') }
    ?: percents.joinToString(", ")

@Composable
private fun NameField(name: String, onChange: (String) -> Unit) {
    val exercise = remember(name) { Planner.exerciseIn(name) }
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.editor_exercise)) },
        supportingText = {
            Text(
                if (exercise == null) {
                    stringResource(R.string.design_unknown)
                } else {
                    stringResource(R.string.design_known, exercise.title)
                },
            )
        },
    )
}

@Composable
private fun LoadFields(
    scheme: String,
    onScheme: (String) -> Unit,
    inPercents: Boolean,
    onPercents: (Boolean) -> Unit,
    load: String,
    onLoad: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        OutlinedTextField(
            value = scheme,
            onValueChange = onScheme,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.design_scheme)) },
            placeholder = { Text("4×6") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            FilterChip(
                selected = inPercents,
                onClick = { onPercents(true) },
                label = { Text(stringResource(R.string.design_percents)) },
            )
            FilterChip(
                selected = !inPercents,
                onClick = { onPercents(false) },
                label = { Text(stringResource(R.string.design_kg)) },
            )
        }
        OutlinedTextField(
            value = load,
            onValueChange = onLoad,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(if (inPercents) R.string.design_percents else R.string.design_kg)) },
            supportingText = {
                Text(stringResource(if (inPercents) R.string.design_percents_hint else R.string.design_kg_hint))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (inPercents) KeyboardType.Text else Decimal),
        )
    }
}

@Composable
private fun CheckRow(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Подтверждение удаления своего цикла [title]. */
@Composable
internal fun ConfirmDesignDelete(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
        text = { Text(stringResource(R.string.design_delete_confirm, title)) },
    )
}
