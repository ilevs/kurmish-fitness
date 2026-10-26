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
import com.kurmish.fitness.core.DietPhase
import com.kurmish.fitness.core.DietSettings
import com.kurmish.fitness.core.Lifestyle
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.Sex
import com.kurmish.fitness.core.activityLevel
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
                    title = stringResource(R.string.diet_lifestyle),
                    options = Lifestyle.entries.map { it to it.title },
                    selected = diet.lifestyle,
                    onSelect = { change(diet.copy(lifestyle = it)) },
                )
                val activity = profile.activityLevel()
                Text(
                    text = stringResource(
                        R.string.diet_activity_auto,
                        diet.lifestyle.hint,
                        activity.title.lowercase(),
                        activity.hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
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
                BodyFields(diet = diet, onChange = change)
            }
        }
    }
}

@Composable
private fun BodyFields(diet: DietSettings, onChange: (DietSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        NumberField(
            label = stringResource(R.string.diet_fat),
            value = diet.bodyFatPercent,
            onValueChange = { onChange(diet.copy(bodyFatPercent = it)) },
        )
        NumberField(
            label = stringResource(R.string.diet_height),
            value = diet.heightCm,
            onValueChange = { onChange(diet.copy(heightCm = it)) },
        )
        NumberField(
            label = stringResource(R.string.diet_age),
            value = diet.age?.toDouble(),
            onValueChange = { onChange(diet.copy(age = it?.toInt())) },
        )
    }
}

private fun Int.signed(): String = if (this > 0) "+$this" else "$this"
