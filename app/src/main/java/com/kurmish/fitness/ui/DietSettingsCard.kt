package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.Activity
import com.kurmish.fitness.core.DietPhase
import com.kurmish.fitness.core.DietSettings
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.Sex
import com.kurmish.fitness.core.withPhase

/** Настройки питания в профиле: фаза, пол, активность, дефицит, процент жира или рост и возраст. */
@Composable
internal fun DietSettingsCard(
    profile: Profile,
    today: Long,
    onProfileChange: (Profile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val diet = profile.diet
    val change = { settings: DietSettings -> onProfileChange(profile.copy(diet = settings)) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(stringResource(R.string.diet_settings), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.diet_settings_intro), style = MaterialTheme.typography.bodySmall)
            ChoiceRow(
                title = stringResource(R.string.diet_phase),
                options = DietPhase.entries.map { it to it.title },
                selected = diet.phase,
                onSelect = { change(diet.withPhase(it, today)) },
            )
            if (diet.phase != DietPhase.Off) {
                ChoiceRow(
                    title = stringResource(R.string.diet_sex),
                    options = Sex.entries.map { it to it.title },
                    selected = diet.sex,
                    onSelect = { change(diet.copy(sex = it)) },
                )
                ChoiceRow(
                    title = stringResource(R.string.diet_activity),
                    options = Activity.entries.map { it to it.title },
                    selected = diet.activity,
                    onSelect = { change(diet.copy(activity = it)) },
                )
                Text(diet.activity.hint, style = MaterialTheme.typography.bodySmall)
                ChoiceRow(
                    title = stringResource(R.string.diet_deficit),
                    options = DietSettings.DEFICIT_OPTIONS.map { it to "$it" },
                    selected = diet.deficitPercent,
                    onSelect = { change(diet.copy(deficitPercent = it)) },
                )
                if (diet.calorieCorrectionPercent != 0) {
                    Text(
                        text = stringResource(R.string.diet_correction, diet.calorieCorrectionPercent.signed()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                NumberField(
                    label = stringResource(R.string.diet_fat),
                    value = diet.bodyFatPercent,
                    onValueChange = { change(diet.copy(bodyFatPercent = it)) },
                )
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
}

private fun Int.signed(): String = if (this > 0) "+$this" else "$this"
