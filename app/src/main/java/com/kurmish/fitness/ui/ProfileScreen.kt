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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kurmish.fitness.core.DietPhase
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.FlexibleDiet
import com.kurmish.fitness.core.Goal
import com.kurmish.fitness.core.Macrocycle
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.Strength

private val DayOptions = (1..7).toList()
private val MaxLifts = listOf(Exercise.Squat, Exercise.Bench, Exercise.Deadlift, Exercise.OverheadPress)

/** Профиль атлета: данные, циклы, ПМ, питание и бэкап Дневника. Циклы открываются отдельным экраном. */
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
    onWeight: (Double) -> Unit,
    diet: DietActions,
    modifier: Modifier = Modifier
) {
    var cycles by rememberSaveable { mutableStateOf(false) }
    if (cycles) {
        CyclesScreen(data, today, onProfileChange, onBack = { cycles = false }, modifier = modifier)
        return
    }
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
            // Вес из профиля — это и взвешивание за сегодня: по нему считается питание.
            onValueChange = { kg ->
                if (kg != null && kg > 0) onWeight(kg) else onProfileChange(profile.copy(bodyweightKg = null))
            },
        )
        CyclesCard(data = data, today = today, onOpen = { cycles = true })
        TestCard(data = data, today = today, onAgain = onTest)
        DietSection(data = data, today = today, diet = diet, onProfileChange = onProfileChange)
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

/** Коротко о макроцикле: текущий блок и сколько в нём сделано; по нажатию открываются циклы. */
@Composable
private fun CyclesCard(data: AppData, today: Long, onOpen: () -> Unit) {
    val position = remember(data, today) { Macrocycle.position(data.profile, data.sessions, today) }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.cycles_title), style = MaterialTheme.typography.titleMedium)
            if (position == null) {
                Text(text = stringResource(R.string.cycles_macro_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.cycles_block, position.index + 1, position.program.title))
                Text(
                    text = stringResource(
                        R.string.profile_program_progress,
                        position.progress.done,
                        position.program.sessions.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.profile_cycles_open)) }
        }
    }
}

/** Настройки питания и, если питание включено, расчёт на сегодня. */
@Composable
private fun DietSection(data: AppData, today: Long, diet: DietActions, onProfileChange: (Profile) -> Unit) {
    val profile = data.profile
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        DietSettingsCard(profile = profile, today = today, onProfileChange = onProfileChange)
        if (profile.diet.phase != DietPhase.Off) {
            DietCard(
                phase = profile.diet.phase,
                day = remember(data, today) { FlexibleDiet.day(data, today) },
                missing = FlexibleDiet.missing(data),
                actions = diet,
            )
        }
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
