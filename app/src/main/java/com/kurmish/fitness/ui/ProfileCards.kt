package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.DietPhase
import com.kurmish.fitness.core.DietSettings
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.FlexibleDiet
import com.kurmish.fitness.core.Goal
import com.kurmish.fitness.core.Macrocycle
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.Sex
import com.kurmish.fitness.core.Strength

private val DayOptions = (1..7).toList()
private val MaxLifts = listOf(Exercise.Squat, Exercise.Bench, Exercise.Deadlift, Exercise.OverheadPress)

/** Карточка блока Профиля: заголовок, пояснение и содержимое; с [onClick] вся карточка нажимается. */
@Composable
private fun SectionCard(
    title: String,
    hint: String?,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body = @Composable {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            hint?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            content()
        }
    }
    if (onClick == null) {
        Card(modifier = Modifier.fillMaxWidth()) { body() }
    } else {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { body() }
    }
}

/**
 * Вес и тело: вес (его вводят чаще всего, это и взвешивание за сегодня), пол и процент жира или рост и возраст
 * для его оценки.
 */
@Composable
internal fun BodyCard(profile: Profile, onWeight: (Double) -> Unit, onProfileChange: (Profile) -> Unit) {
    val diet = profile.diet
    val change = { settings: DietSettings -> onProfileChange(profile.copy(diet = settings)) }
    SectionCard(title = stringResource(R.string.profile_body), hint = stringResource(R.string.profile_body_hint)) {
        NumberField(
            label = stringResource(R.string.profile_bodyweight),
            value = profile.bodyweightKg,
            onValueChange = { kg ->
                if (kg != null && kg > 0) onWeight(kg) else onProfileChange(profile.copy(bodyweightKg = null))
            },
        )
        ChoiceRow(
            title = stringResource(R.string.diet_sex),
            options = Sex.entries.map { it to it.title },
            selected = diet.sex,
            onSelect = { change(diet.copy(sex = it)) },
        )
        NumberField(
            label = stringResource(R.string.diet_fat),
            value = diet.bodyFatPercent,
            onValueChange = { change(diet.copy(bodyFatPercent = it)) },
        )
        // Рост и возраст нужны только для оценки процента жира, когда его не ввели.
        if (diet.bodyFatPercent == null) {
            NumberField(
                label = stringResource(R.string.diet_height),
                value = diet.heightCm,
                onValueChange = { change(diet.copy(heightCm = it)) },
            )
            NumberField(
                label = stringResource(R.string.diet_age),
                value = diet.age?.toDouble(),
                onValueChange = { change(diet.copy(age = it?.toInt())) },
            )
        }
    }
}

/** Цель и график: задача и сколько тренировок в неделю. */
@Composable
internal fun GoalCard(profile: Profile, onProfileChange: (Profile) -> Unit) {
    SectionCard(title = stringResource(R.string.profile_goal_section), hint = null) {
        ChoiceRow(
            title = stringResource(R.string.profile_goal),
            options = Goal.entries.map { it to it.title },
            selected = profile.goal,
            onSelect = { onProfileChange(profile.copy(goal = it)) },
        )
        ChoiceRow(
            title = stringResource(R.string.profile_days),
            options = DayOptions.map { it to it.toString() },
            selected = profile.daysPerWeek,
            onSelect = { onProfileChange(profile.copy(daysPerWeek = it)) },
        )
    }
}

/** Коротко о макроцикле: текущий блок и сколько в нём сделано; по нажатию открываются циклы. */
@Composable
internal fun CyclesCard(data: AppData, today: Long, onOpen: () -> Unit) {
    val position = remember(data, today) { Macrocycle.position(data.profile, data.sessions, today) }
    SectionCard(
        title = stringResource(R.string.cycles_title),
        hint = stringResource(R.string.cycles_macro_empty).takeIf { position == null },
        onClick = onOpen,
    ) {
        position?.let {
            Text(stringResource(R.string.cycles_block, it.index + 1, it.program.title))
            Text(
                text = stringResource(R.string.profile_program_progress, it.progress.done, it.program.sessions.size),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onOpen) { Text(stringResource(R.string.profile_cycles_open)) }
    }
}

/** Сила: ПМ вручную; в пустом поле подсказкой оценка по Дневнику. */
@Composable
internal fun StrengthCard(data: AppData, today: Long, onProfileChange: (Profile) -> Unit) {
    val estimates = remember(data, today) { data.estimatedMaxes(today) }
    SectionCard(
        title = stringResource(R.string.profile_strength),
        hint = stringResource(R.string.profile_strength_hint),
    ) {
        MaxLifts.forEach { lift ->
            NumberField(
                label = stringResource(R.string.profile_max, lift.title.lowercase()),
                value = data.profile.manualMaxes[lift],
                placeholder = estimates[lift]?.kg?.let { Strength.roundToPlates(it).toString() },
                onValueChange = { value ->
                    val current = data.profile.manualMaxes
                    val maxes = if (value == null) current - lift else current + (lift to value)
                    onProfileChange(data.profile.copy(manualMaxes = maxes))
                },
            )
        }
    }
}

/** Питание на сегодня, если оно включено; настройки питания ниже, среди редких. */
@Composable
internal fun TodayDiet(data: AppData, today: Long, diet: DietActions) {
    val phase = data.profile.diet.phase
    if (phase == DietPhase.Off) return
    DietCard(
        phase = phase,
        day = remember(data, today) { FlexibleDiet.day(data, today) },
        missing = FlexibleDiet.missing(data),
        actions = diet,
    )
}

/** Вход в резервную копию Дневника. */
@Composable
internal fun BackupEntryCard(onOpen: () -> Unit) {
    SectionCard(
        title = stringResource(R.string.backup_screen_title),
        hint = stringResource(R.string.backup_screen_hint),
        onClick = onOpen,
    ) {
        TextButton(onClick = onOpen) { Text(stringResource(R.string.backup_screen_open)) }
    }
}
