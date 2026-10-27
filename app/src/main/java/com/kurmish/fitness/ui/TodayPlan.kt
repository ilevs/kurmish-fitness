package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Coach
import com.kurmish.fitness.core.DayPlan
import com.kurmish.fitness.core.EntryDraft
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.FitnessTest
import com.kurmish.fitness.core.PlanItem
import com.kurmish.fitness.core.PlanPart
import com.kurmish.fitness.core.Planner
import com.kurmish.fitness.core.SessionDraft
import com.kurmish.fitness.core.SetGroup
import com.kurmish.fitness.core.WorkoutSession
import com.kurmish.fitness.core.matchPlan
import com.kurmish.fitness.core.previousSets

/**
 * Тренировка на сегодня прямо в Дневнике: разминка и заминка чек-листом, у каждого упражнения плана свои
 * подходы строками «вес · повторы · ✓». Отмеченные подходы сразу сохраняются в сегодняшнюю запись.
 *
 * @param suggest подсказки названий для упражнений сверх плана.
 * @param onSave сохранить сегодняшнюю запись.
 * @param onDelete удалить сегодняшнюю запись, когда в ней не осталось отмеченных подходов.
 * @param onTest назначить тестовую тренировку на сегодня или вернуть обычный план.
 */
@Composable
internal fun TodayPlan(
    data: AppData,
    today: Long,
    suggest: (String) -> List<String>,
    onSpeak: (String) -> Unit,
    onChoose: (slot: String, exercise: Exercise?) -> Unit,
    onSave: (WorkoutSession) -> Unit,
    onDelete: (Long) -> Unit,
    onTest: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val headline = remember(data, today) { Coach.advise(data, today).headline }
    val plan = remember(data, today) { Planner.today(data, today) }
    val workout = rememberTodayWorkout(data, today, plan, onSave, onDelete)
    var choosing by remember { mutableStateOf<PlanItem?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(text = headline, style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = plan.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onSpeak("$headline ${plan.speech}") }) {
                Icon(painterResource(R.drawable.ic_volume), contentDescription = stringResource(R.string.speak))
            }
        }
        plan.note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        TestSwitch(data = data, today = today, onTest = onTest)
        val matched = workout.draft.matchPlan(plan)
        plan.items.forEachIndexed { index, item ->
            if (item.part == PlanPart.Work) {
                val entryIndex = matched[index]
                WorkItemCard(
                    item = item,
                    entry = entryIndex?.let { workout.draft.entries[it] },
                    previous = entryIndex?.let { workout.previous(it) }.orEmpty(),
                    onChange = { changed -> entryIndex?.let { workout.change(it, changed) } },
                    onReplace = { choosing = item },
                    onRestore = { onChoose(item.slot, null) },
                )
            } else {
                ChecklistCard(item = item, id = "$today-$index")
            }
        }
        ExtraEntries(workout = workout, planned = matched.values.toSet(), suggest = suggest)
        choosing?.let { item ->
            ReplacementPicker(
                item = item,
                onPick = { exercise ->
                    onChoose(item.slot, exercise)
                    choosing = null
                },
                onDismiss = { choosing = null },
            )
        }
    }
}

/**
 * Тестовая тренировка по кнопке: заново определить рабочие веса, например после перерыва. Назначенную вручную
 * можно отменить; в самом начале тест в плане и так.
 */
@Composable
private fun TestSwitch(data: AppData, today: Long, onTest: (Boolean) -> Unit) {
    when {
        data.profile.testEpochDay == today ->
            TextButton(onClick = { onTest(false) }) { Text(stringResource(R.string.today_test_cancel)) }
        !FitnessTest.first(data, today) ->
            TextButton(onClick = { onTest(true) }) { Text(stringResource(R.string.today_test_start)) }
    }
}

/** Упражнения сверх плана и кнопка, чтобы добавить ещё одно. */
@Composable
private fun ExtraEntries(workout: TodayWorkout, planned: Set<Int>, suggest: (String) -> List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        workout.draft.entries.indices.filterNot { it in planned }.forEach { entryIndex ->
            EntryCard(
                entry = workout.draft.entries[entryIndex],
                previous = workout.previous(entryIndex),
                suggest = suggest,
                onChange = { workout.change(entryIndex, it) },
                onRemove = { workout.remove(entryIndex) },
            )
        }
        OutlinedButton(onClick = workout::add) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Spacer(Modifier.width(Gap))
            Text(stringResource(R.string.editor_add_exercise))
        }
    }
}

/** Разминка или заминка: галочка только для себя, в Дневник не пишется. */
@Composable
private fun ChecklistCard(item: PlanItem, id: String) {
    var ticked by rememberSaveable(id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(modifier = Modifier.padding(end = ScreenPadding, top = Gap, bottom = Gap)) {
            Checkbox(checked = ticked, onCheckedChange = { ticked = it })
            Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                Text(text = item.title, style = MaterialTheme.typography.titleSmall)
                // В разминке и заминке сначала шаги, подводящие подходы идут за ними.
                if (!ticked) {
                    item.notes.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                    SetLines(item)
                }
            }
        }
    }
}

/** Упражнение плана: что сделать, подсказки и подходы строками с отметкой «сделан». */
@Composable
private fun WorkItemCard(
    item: PlanItem,
    entry: EntryDraft?,
    previous: List<SetGroup>,
    onChange: (EntryDraft) -> Unit,
    onReplace: () -> Unit,
    onRestore: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(SmallGap)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            val hint = MaterialTheme.colorScheme.onSurfaceVariant
            item.notes.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = hint) }
            if (item.replaced) {
                Text(
                    text = stringResource(R.string.today_instead, item.slot),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (entry == null) SetLines(item) else SetTable(entry = entry, previous = previous, onChange = onChange)
            Row {
                TextButton(onClick = onReplace) { Text(stringResource(R.string.today_replace)) }
                if (item.replaced) TextButton(onClick = onRestore) { Text(stringResource(R.string.today_restore)) }
            }
        }
    }
}

/**
 * Черновик сегодняшней тренировки: строки плана и записанные подходы. Каждое изменение сразу сохраняется,
 * если всё отмеченное введено правильно; запись, сделанная со стороны (голосом, в редакторе), перечитывается.
 */
@Stable
private class TodayWorkout(
    initial: SessionDraft,
    synced: WorkoutSession?,
    shape: List<Pair<Exercise?, String>>,
    private val data: () -> AppData,
    private val onSave: (WorkoutSession) -> Unit,
    private val onDelete: (Long) -> Unit,
) {
    /** Что сейчас на экране: записанное и ещё не отмеченное. */
    var draft by mutableStateOf(initial)
        private set

    /** Запись, которую черновик сохранил последней или с которой собран. */
    var synced by mutableStateOf(synced)
        private set

    /** Упражнения плана, по которым собран черновик. */
    var shape by mutableStateOf(shape)
        private set

    /** Собрать черновик заново по плану [plan] и записи [existing]. */
    fun reload(plan: DayPlan, existing: WorkoutSession?) {
        synced = existing
        shape = plan.shape()
        draft = SessionDraft.fromPlan(plan, draft.epochDay, existing).withoutBlank()
    }

    /** Подходы упражнения [index] в прошлый раз. */
    fun previous(index: Int): List<SetGroup> = data().previousSets(draft.entries[index], draft.epochDay, draft.id)

    /** Заменить упражнение [index] и сохранить. */
    fun change(index: Int, entry: EntryDraft) {
        commit(draft.copy(entries = draft.entries.mapIndexed { i, old -> if (i == index) entry else old }))
    }

    /** Убрать упражнение [index] и сохранить. */
    fun remove(index: Int) {
        commit(draft.copy(entries = draft.entries.filterIndexed { i, _ -> i != index }))
    }

    /** Добавить пустое упражнение сверх плана. */
    fun add() {
        draft = draft.copy(entries = draft.entries + EntryDraft.empty())
    }

    private fun commit(changed: SessionDraft) {
        val id = if (changed.id == 0L) data().nextId else changed.id
        val session = changed.toSession()?.copy(id = id)
        draft = changed
        if (session != null) {
            draft = changed.copy(id = id)
            synced = session
            onSave(session)
        } else if (changed.entries.all { it.skipped } && changed.id != 0L) {
            draft = changed.copy(id = 0)
            synced = null
            onDelete(changed.id)
        }
    }
}

@Composable
private fun rememberTodayWorkout(
    data: AppData,
    today: Long,
    plan: DayPlan,
    onSave: (WorkoutSession) -> Unit,
    onDelete: (Long) -> Unit,
): TodayWorkout {
    val existing = data.sessions.filter { it.epochDay == today }.maxByOrNull { it.id }
    val currentData by rememberUpdatedState(data)
    val save by rememberUpdatedState(onSave)
    val delete by rememberUpdatedState(onDelete)
    val workout = remember(today) {
        TodayWorkout(
            initial = SessionDraft.fromPlan(plan, today, existing).withoutBlank(),
            synced = existing,
            shape = plan.shape(),
            data = { currentData },
            onSave = { save(it) },
            onDelete = { delete(it) },
        )
    }
    // Запись изменили не здесь (голосом, в редакторе) или сменился план: черновик собирается заново.
    LaunchedEffect(existing, plan) {
        if (existing != workout.synced || plan.shape() != workout.shape) workout.reload(plan, existing)
    }
    return workout
}

/** Упражнения плана без весов: веса меняются от записанных подходов, а состав — только при замене. */
internal fun DayPlan.shape(): List<Pair<Exercise?, String>> = items.map { it.exercise to it.title }

/** Без пустого упражнения-заготовки: в день без плана список начинается пустым. */
internal fun SessionDraft.withoutBlank(): SessionDraft =
    copy(entries = entries.filterNot { it.nameMissing && it.sets.all { set -> set.blank } })

private val SmallGap = 8.dp

@Composable
private fun SetLines(item: PlanItem) {
    item.sets.forEach { Text(text = it.label, style = MaterialTheme.typography.bodyLarge) }
}
