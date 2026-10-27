package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.kurmish.fitness.R
import com.kurmish.fitness.backup.BackupUi
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.Goal
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.Strength

private val DayOptions = listOf(2, 3, 4, 5)
private val MaxLifts = listOf(Exercise.Squat, Exercise.Bench, Exercise.Deadlift, Exercise.OverheadPress)

/** Профиль атлета: данные, ПМ, питание и бэкап Дневника. */
@Composable
internal fun ProfileScreen(
    data: AppData,
    today: Long,
    backup: BackupUi,
    onProfileChange: (Profile) -> Unit,
    onBackupConnect: (String, String) -> Unit,
    onBackupNow: () -> Unit,
    onRestore: () -> Unit,
    onBackupDisconnect: () -> Unit,
    files: FileBackups,
    onTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = data.profile
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
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
        NumberField(
            label = stringResource(R.string.profile_bodyweight),
            value = profile.bodyweightKg,
            onValueChange = { onProfileChange(profile.copy(bodyweightKg = it)) },
        )
        TestCard(data = data, today = today, onAgain = onTest)
        DietSettingsCard(profile = profile, today = today, onProfileChange = onProfileChange)
        MaxFields(data = data, today = today, onProfileChange = onProfileChange)
        BackupCard(
            state = backup,
            onConnect = onBackupConnect,
            onBackup = onBackupNow,
            onRestore = onRestore,
            onDisconnect = onBackupDisconnect,
        )
        DriveBackupCard(state = files.drive, actions = files.driveActions)
        FileBackupCard(status = files.status, actions = files.actions)
    }
}

@Composable
private fun MaxFields(data: AppData, today: Long, onProfileChange: (Profile) -> Unit) {
    val estimates = remember(data, today) { data.estimatedMaxes(today) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        // Ряд переносится на новую строку, чтобы выбранный вариант не уезжал за край экрана.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
internal fun NumberField(label: String, value: Double?, onValueChange: (Double?) -> Unit, placeholder: String? = null) {
    var text by rememberSaveable { mutableStateOf(value?.plain().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val number = input.replace(',', '.').toDoubleOrNull()
            if (input.isBlank() || number != null) onValueChange(number)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private fun Double.plain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
