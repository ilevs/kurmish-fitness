package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.DietDay
import com.kurmish.fitness.core.DietPhase
import kotlin.math.roundToInt

private const val BreakDays = 14L

/**
 * Питание на сегодня: калории и БЖУ, рефид и поправка калорий. Вес вводится в профиле.
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
        }
    }
}

/**
 * Что можно сделать в карточке питания.
 *
 * @property onRefeedDone отметить рефид сегодня.
 * @property onApplyCorrection принять поправку калорий, в процентах.
 * @property onPhaseChange сменить фазу питания.
 */
internal class DietActions(
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
