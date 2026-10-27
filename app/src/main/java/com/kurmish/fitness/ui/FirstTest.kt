package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.FitnessTest
import com.kurmish.fitness.core.Goal
import com.kurmish.fitness.core.Grade
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.WorkoutSession

private val TestDays = (1..7).toList()

/**
 * Первичное тестирование: цель, тесты подвижности плеч и лучшие подходы в базовых упражнениях. Открывается
 * само при первом запуске и по кнопке в профиле.
 *
 * @param onFinish профиль с результатами и запись тестирования для Дневника (null, если весов нет).
 * @param onSkip закрыть без изменений; анкета больше не откроется сама.
 */
@Composable
internal fun FirstTestDialog(
    profile: Profile,
    today: Long,
    onFinish: (Profile, WorkoutSession?) -> Unit,
    onSkip: () -> Unit,
) {
    var draft by remember { mutableStateOf(profile) }
    var yearPlan by remember { mutableStateOf(profile.yearPlanStartEpochDay != null) }
    val weights = remember { mutableStateMapOf<Exercise, Double?>() }
    val reps = remember { mutableStateMapOf<Exercise, Double?>() }
    val finish = {
        val start = if (yearPlan) profile.yearPlanStartEpochDay ?: today else null
        val tested = FitnessTest.applyTo(draft, draft.shoulders).copy(onboarded = true, yearPlanStartEpochDay = start)
        val results = FitnessTest.lifts.associateWith { lift -> weights[lift] to (reps[lift]?.toInt() ?: 0) }
        onFinish(tested, FitnessTest.session(results, today))
    }
    Dialog(onDismissRequest = onSkip, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Gap),
            ) {
                Text(text = stringResource(R.string.test_title), style = MaterialTheme.typography.titleLarge)
                Text(text = stringResource(R.string.test_intro), style = MaterialTheme.typography.bodySmall)
                ProfileFields(draft = draft, onChange = { draft = it })
                WeightsCard(
                    weights = weights,
                    reps = reps,
                    onWeight = { lift, kg -> weights[lift] = kg },
                    onReps = { lift, count -> reps[lift] = count },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = yearPlan, onCheckedChange = { yearPlan = it })
                    Text(stringResource(R.string.test_year_plan))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    Button(onClick = finish) { Text(stringResource(R.string.editor_ok)) }
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.test_skip)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileFields(draft: Profile, onChange: (Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        ChoiceRow(
            title = stringResource(R.string.profile_goal),
            options = Goal.entries.map { it to it.title },
            selected = draft.goal,
            onSelect = { onChange(draft.copy(goal = it)) },
        )
        ChoiceRow(
            title = stringResource(R.string.profile_days),
            options = TestDays.map { it to it.toString() },
            selected = draft.daysPerWeek,
            onSelect = { onChange(draft.copy(daysPerWeek = it)) },
        )
        NumberField(
            label = stringResource(R.string.profile_bodyweight),
            value = draft.bodyweightKg,
            onValueChange = { onChange(draft.copy(bodyweightKg = it)) },
        )
        GradeCard(
            title = stringResource(R.string.test_chest_title),
            how = stringResource(R.string.test_chest_how),
            grade = draft.shoulders.chest,
            onGrade = { onChange(draft.copy(shoulders = draft.shoulders.copy(chest = it))) },
        )
        GradeCard(
            title = stringResource(R.string.test_overhead_title),
            how = stringResource(R.string.test_overhead_how),
            grade = draft.shoulders.overhead,
            onGrade = { onChange(draft.copy(shoulders = draft.shoulders.copy(overhead = it))) },
        )
    }
}

@Composable
private fun GradeCard(title: String, how: String, grade: Grade?, onGrade: (Grade?) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = how, style = MaterialTheme.typography.bodySmall)
            ChoiceRow(
                title = stringResource(R.string.test_grade),
                options = listOf<Pair<Grade?, String>>(null to stringResource(R.string.test_skip_grade)) +
                    Grade.entries.map { it to it.title },
                selected = grade,
                onSelect = onGrade,
            )
        }
    }
}

@Composable
private fun WeightsCard(
    weights: Map<Exercise, Double?>,
    reps: Map<Exercise, Double?>,
    onWeight: (Exercise, Double?) -> Unit,
    onReps: (Exercise, Double?) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.test_weights_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.test_weights_how), style = MaterialTheme.typography.bodySmall)
            FitnessTest.lifts.forEach { lift ->
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    Column(modifier = Modifier.weight(2f)) {
                        NumberField(
                            label = stringResource(R.string.test_weight, lift.title),
                            value = weights[lift],
                            onValueChange = { onWeight(lift, it) },
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        NumberField(
                            label = stringResource(R.string.test_reps),
                            value = reps[lift],
                            onValueChange = { onReps(lift, it) },
                        )
                    }
                }
            }
        }
    }
}

/** Карточка профиля: результаты тестов плеч, силовой баланс и повторное тестирование. */
@Composable
internal fun TestCard(data: AppData, today: Long, onAgain: () -> Unit) {
    val advice = remember(data, today) { FitnessTest.balance(data, today) }
    val skipped = stringResource(R.string.test_skip_grade)
    val shoulders = data.profile.shoulders
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.test_card_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.test_card_chest, shoulders.chest?.title ?: skipped))
            Text(stringResource(R.string.test_card_overhead, shoulders.overhead?.title ?: skipped))
            if (advice.isEmpty()) {
                Text(text = stringResource(R.string.test_card_balance_ok), style = MaterialTheme.typography.bodySmall)
            }
            advice.forEach { Text(it) }
            OutlinedButton(onClick = onAgain) { Text(stringResource(R.string.test_card_again)) }
        }
    }
}
