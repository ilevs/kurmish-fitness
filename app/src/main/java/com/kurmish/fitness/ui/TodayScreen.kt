package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.Advice
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Coach
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.Strength
import com.kurmish.fitness.core.WarmUp
import com.kurmish.fitness.core.kg

private val Percents = listOf(60, 70, 80, 90)
private const val WarmUpPercent = 80

/** Совет на сегодня, тренировка программы и рабочие веса от ПМ. */
@Composable
internal fun TodayScreen(data: AppData, today: Long, onSpeak: (String) -> Unit, modifier: Modifier = Modifier) {
    val advice = remember(data, today) { Coach.advise(data, today) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Text(text = advice.headline, style = MaterialTheme.typography.titleLarge)
        Button(onClick = { onSpeak(advice.speech) }) {
            Icon(painterResource(R.drawable.ic_volume), contentDescription = null)
            Spacer(Modifier.width(Gap))
            Text(stringResource(R.string.speak))
        }
        if (advice.plan.isNotEmpty()) PlanCard(advice)
        advice.points.forEach { point -> Text(text = "• $point", style = MaterialTheme.typography.bodyLarge) }
        WeightsCard(data = data, today = today)
    }
}

@Composable
private fun PlanCard(advice: Advice) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = advice.plan.first(), style = MaterialTheme.typography.titleMedium)
            advice.plan.drop(1).forEachIndexed { index, line -> Text("${index + 1}. $line") }
            if (advice.warmUp.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.today_plan_warm_up, advice.warmUp.joinToString { it.label }),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun WeightsCard(data: AppData, today: Long) {
    val lifts = Exercise.entries
        .filter { it.isMainLift }
        .mapNotNull { lift -> data.currentMax(lift, today)?.let { lift to it } }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.today_weights), style = MaterialTheme.typography.titleMedium)
            if (lifts.isEmpty()) {
                Text(stringResource(R.string.today_no_maxes))
            }
            lifts.forEach { (lift, max) ->
                val warmUp = WarmUp.sets(lift, Strength.percentOf(max, WarmUpPercent))
                Text(
                    text = "${lift.title}, ПМ ${Strength.roundToPlates(max).kg()}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(Percents.joinToString(" · ") { "$it % — ${Strength.percentOf(max, it).kg()}" })
                Text(
                    text = stringResource(R.string.today_warm_up, warmUp.joinToString { it.label }),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
