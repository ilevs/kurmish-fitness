package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Coach
import com.kurmish.fitness.core.DietPhase
import com.kurmish.fitness.core.ExerciseEntry
import com.kurmish.fitness.core.FlexibleDiet
import com.kurmish.fitness.core.PlanItem
import com.kurmish.fitness.core.PlanPart
import com.kurmish.fitness.core.Planner

/** Тренировка на сегодня чек-листом: разминка, упражнения с весом и подходами, заминка; ниже советы. */
@Composable
internal fun TodayScreen(
    data: AppData,
    today: Long,
    onSpeak: (String) -> Unit,
    dietActions: DietActions,
    modifier: Modifier = Modifier,
) {
    val advice = remember(data, today) { Coach.advise(data, today) }
    val diet = remember(data, today) { FlexibleDiet.day(data, today) }
    val plan = remember(data, today) { Planner.today(data, today) }
    val entries = remember(data, today) { data.sessions.filter { it.epochDay == today }.flatMap { it.entries } }
    var ticked by rememberSaveable(today) { mutableStateOf(emptyList<Int>()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Text(text = advice.headline, style = MaterialTheme.typography.titleLarge)
        Button(onClick = { onSpeak((listOf(advice.headline, plan.speech) + advice.points).joinToString(" ")) }) {
            Icon(painterResource(R.drawable.ic_volume), contentDescription = null)
            Spacer(Modifier.width(Gap))
            Text(stringResource(R.string.speak))
        }
        Text(text = plan.title, style = MaterialTheme.typography.titleMedium)
        plan.note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        plan.items.forEachIndexed { index, item ->
            PlanItemCard(
                item = item,
                recorded = item.recordedIn(entries),
                ticked = index in ticked,
                onTick = { ticked = if (index in ticked) ticked - index else ticked + index },
            )
        }
        if (data.profile.diet.phase != DietPhase.Off) {
            DietCard(
                phase = data.profile.diet.phase,
                day = diet,
                missing = FlexibleDiet.missing(data),
                actions = dietActions,
            )
        }
        if (advice.points.isNotEmpty()) {
            Text(text = stringResource(R.string.today_tips), style = MaterialTheme.typography.titleMedium)
            advice.points.forEach { point -> Text(text = "• $point", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun PlanItemCard(item: PlanItem, recorded: List<ExerciseEntry>, ticked: Boolean, onTick: () -> Unit) {
    val done = recorded.isNotEmpty() || ticked
    val colors = if (item.part == PlanPart.Work) {
        CardDefaults.cardColors()
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Row(modifier = Modifier.padding(end = ScreenPadding, top = Gap, bottom = Gap)) {
            Checkbox(checked = done, onCheckedChange = { onTick() }, enabled = recorded.isEmpty())
            Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                Text(text = item.title, style = MaterialTheme.typography.titleSmall)
                item.sets.forEach { Text(text = it.label, style = MaterialTheme.typography.bodyLarge) }
                item.notes.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                if (recorded.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.today_done, recorded.joinToString { it.label() }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
