package com.kurmish.fitness.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kurmish.fitness.R
import com.kurmish.fitness.core.CycleDesign
import com.kurmish.fitness.core.DesignDay
import com.kurmish.fitness.core.DesignItem

private val ItemPadding = 6.dp

/**
 * Полноэкранный редактор своего цикла: название, число микроциклов, дни и упражнения с нагрузкой
 * на выбранный микроцикл. Сохранённые правки сразу идут в план на сегодня и расписание.
 *
 * @param onDelete удаление цикла; null для ещё не сохранённого.
 */
@Composable
internal fun CycleEditor(
    initial: CycleDesign,
    onSave: (CycleDesign) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var design by remember(initial) { mutableStateOf(initial) }
    var week by remember(initial) { mutableIntStateOf(0) }
    var editing by remember(initial) { mutableStateOf<Pair<Int, Int>?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Gap),
            ) {
                OutlinedTextField(
                    value = design.title,
                    onValueChange = { design = design.copy(title = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.design_title)) },
                    singleLine = true,
                )
                WeekPicker(
                    count = design.microcycles,
                    week = week,
                    onWeek = { week = it },
                    onCount = { count ->
                        design = design.withMicrocycles(count)
                        week = week.coerceAtMost(design.microcycles - 1)
                    },
                )
                Text(text = stringResource(R.string.design_hint), style = MaterialTheme.typography.bodySmall)
                Days(design = design, week = week, onChange = { design = it }, onEdit = { editing = it })
                DesignActions(design = design, onSave = onSave, onDelete = onDelete, onDismiss = onDismiss)
            }
        }
    }
    editing?.let { (dayIndex, itemIndex) ->
        val day = design.days.getOrNull(dayIndex)
        val item = day?.items?.getOrNull(itemIndex)
        if (day != null && item != null) {
            DesignItemDialog(
                item = item,
                week = week,
                onSave = { changed ->
                    design = design.withDay(dayIndex, day.withItem(itemIndex, changed))
                    editing = null
                },
                onDelete = {
                    design = design.withDay(dayIndex, day.withItem(itemIndex, null))
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun Days(design: CycleDesign, week: Int, onChange: (CycleDesign) -> Unit, onEdit: (Pair<Int, Int>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        design.days.forEachIndexed { dayIndex, day ->
            DayCard(
                day = day,
                week = week,
                onChange = { onChange(design.withDay(dayIndex, it)) },
                onEditItem = { onEdit(dayIndex to it) },
                onAddItem = {
                    onChange(design.withDay(dayIndex, day.withItem(day.items.size, design.newItem(""))))
                    onEdit(dayIndex to day.items.size)
                },
            )
        }
        OutlinedButton(onClick = { onChange(design.withDay(design.days.size, nextDay(design))) }) {
            Text(stringResource(R.string.design_add_day))
        }
    }
}

private fun nextDay(design: CycleDesign): DesignDay = DesignDay("День ${design.days.size + 1}")

@Composable
private fun WeekPicker(count: Int, week: Int, onWeek: (Int) -> Unit, onCount: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.design_microcycles, count),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onCount(count - 1) }, enabled = count > 1) { Text("−") }
            TextButton(onClick = { onCount(count + 1) }, enabled = count < CycleDesign.MAX_MICROCYCLES) {
                Text("+")
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            repeat(count) { index ->
                FilterChip(
                    selected = index == week,
                    onClick = { onWeek(index) },
                    label = { Text(stringResource(R.string.design_week, index + 1)) },
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    day: DesignDay,
    week: Int,
    onChange: (DesignDay?) -> Unit,
    onEditItem: (Int) -> Unit,
    onAddItem: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            OutlinedTextField(
                value = day.title,
                onValueChange = { onChange(day.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.design_day)) },
                singleLine = true,
            )
            day.items.forEachIndexed { index, item ->
                ItemRow(
                    item = item,
                    week = week,
                    onClick = { onEditItem(index) },
                    onUp = { onChange(day.moved(index, -1)) }.takeIf { index > 0 },
                    onDown = { onChange(day.moved(index, 1)) }.takeIf { index < day.items.lastIndex },
                )
            }
            Row {
                TextButton(onClick = onAddItem) { Text(stringResource(R.string.editor_add_exercise)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onChange(null) }) {
                    Text(stringResource(R.string.design_remove_day), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: DesignItem, week: Int, onClick: () -> Unit, onUp: (() -> Unit)?, onDown: (() -> Unit)?) {
    val load = item.weeks.getOrNull(week)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = ItemPadding),
        ) {
            Text(text = item.name.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (load == null) {
                    stringResource(R.string.design_absent)
                } else {
                    listOf(load.scheme, load.load).filter { it.isNotBlank() }.joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = { onUp?.invoke() }, enabled = onUp != null) { Text("↑") }
        TextButton(onClick = { onDown?.invoke() }, enabled = onDown != null) { Text("↓") }
    }
}

@Composable
private fun DesignActions(
    design: CycleDesign,
    onSave: (CycleDesign) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) {
            TextButton(onClick = { confirming = true }) {
                Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        Button(onClick = { onSave(design) }, enabled = design.title.isNotBlank() && design.days.isNotEmpty()) {
            Text(stringResource(R.string.log_save))
        }
    }
    if (confirming && onDelete != null) {
        ConfirmDesignDelete(title = design.title, onConfirm = onDelete, onDismiss = { confirming = false })
    }
}
