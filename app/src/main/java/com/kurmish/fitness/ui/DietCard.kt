package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.kurmish.fitness.R
import com.kurmish.fitness.core.DietDay
import com.kurmish.fitness.core.DietPhase
import kotlin.math.roundToInt

private const val BreakDays = 14L

/**
 * Питание на сегодня: калории и БЖУ, рефид, поправка калорий и взвешивание.
 *
 * @param day расчёт на сегодня; null, если данных не хватает, тогда показывается [missing].
 */
@Composable
internal fun DietCard(
    phase: DietPhase,
    day: DietDay?,
    missing: String?,
    actions: DietActions,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(stringResource(R.string.diet_title, phase.title), style = MaterialTheme.typography.titleMedium)
            missing?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (day != null) DietNumbers(day = day, actions = actions)
            WeightRow(onSave = actions.onLogWeight)
        }
    }
}

/**
 * Что можно сделать в карточке питания.
 *
 * @property onLogWeight записать вес за сегодня.
 * @property onRefeedDone отметить рефид сегодня.
 * @property onApplyCorrection принять поправку калорий, в процентах.
 * @property onPhaseChange сменить фазу питания.
 */
internal class DietActions(
    val onLogWeight: (Double) -> Unit,
    val onRefeedDone: () -> Unit,
    val onApplyCorrection: (Int) -> Unit,
    val onPhaseChange: (DietPhase) -> Unit,
)

@Composable
private fun DietNumbers(day: DietDay, actions: DietActions) {
    val estimated = if (day.bodyFatEstimated) stringResource(R.string.diet_body_estimated) else ""
    val refeed = day.refeed?.takeIf { it.due && !it.doneToday }
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(
            text = stringResource(
                R.string.diet_body,
                day.bodyFatPercent.roundToInt().toString(),
                estimated,
                day.leanKg.roundedKg(),
                day.category,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(R.string.diet_target, day.target.label), style = MaterialTheme.typography.titleSmall)
        if (refeed != null) {
            day.refeedTarget?.let {
                Text(stringResource(R.string.diet_refeed_target, it.label), style = MaterialTheme.typography.titleSmall)
            }
        }
        day.points.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        day.suggestion?.let { suggestion ->
            Text(suggestion.reason, color = MaterialTheme.colorScheme.primary)
            Button(onClick = { actions.onApplyCorrection(suggestion.percent) }) {
                Text(stringResource(R.string.diet_apply))
            }
        }
        if (refeed != null) {
            OutlinedButton(onClick = actions.onRefeedDone) { Text(stringResource(R.string.diet_refeed_done)) }
        }
        if (day.phase == DietPhase.Break && day.daysInPhase >= BreakDays) {
            OutlinedButton(onClick = { actions.onPhaseChange(DietPhase.Deficit) }) {
                Text(stringResource(R.string.diet_back_to_deficit))
            }
        }
    }
}

@Composable
private fun WeightRow(onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val weight = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.diet_weight)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Button(
            onClick = {
                weight?.let(onSave)
                text = ""
            },
            enabled = weight != null,
        ) {
            Text(stringResource(R.string.diet_weight_save))
        }
    }
}
