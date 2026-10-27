package com.kurmish.fitness.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurmish.fitness.R
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.Movement
import com.kurmish.fitness.core.PlanItem
import com.kurmish.fitness.core.Planes

private val RowPadding = 8.dp

/**
 * Выбор упражнения на замену: сначала той же плоскости, по ступеням от простых к сложным, ниже остальные
 * по плоскостям. Если тип исходного упражнения неизвестен, показываются все.
 */
@Composable
internal fun ReplacementPicker(item: PlanItem, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    val current = item.exercise
    val groups = Movement.entries
        .sortedBy { if (it == current?.movement) 0 else 1 }
        .map { movement ->
            movement to Exercise.entries.filter { it.movement == movement && it != current }.sortedBy { it.level }
        }
        .filter { (_, exercises) -> exercises.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
        title = { Text(stringResource(R.string.picker_title, item.title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = stringResource(R.string.picker_hint), style = MaterialTheme.typography.bodySmall)
                groups.forEach { (movement, exercises) ->
                    Text(
                        text = movement.plane,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = Gap),
                    )
                    exercises.forEach { exercise ->
                        val label = if (exercise in Planes.discouraged) {
                            R.string.picker_step_discouraged
                        } else {
                            R.string.picker_step
                        }
                        Text(
                            text = stringResource(label, exercise.title, exercise.level),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(exercise) }
                                .padding(vertical = RowPadding),
                        )
                    }
                }
            }
        },
    )
}
