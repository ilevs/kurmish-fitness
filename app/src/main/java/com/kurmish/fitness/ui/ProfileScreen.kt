package com.kurmish.fitness.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.kurmish.fitness.R
import com.kurmish.fitness.backup.BackupUi
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.CycleCatalog
import com.kurmish.fitness.core.Discipline
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.Goal
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.Programs
import com.kurmish.fitness.core.Rank
import com.kurmish.fitness.core.Strength
import com.kurmish.fitness.core.TrainingProgram

private val DayOptions = listOf(2, 3, 4, 5)
private val MaxLifts = listOf(Exercise.Squat, Exercise.Bench, Exercise.Deadlift, Exercise.OverheadPress)
private const val ShownCycles = 5

/** Профиль атлета, подбор цикла и бэкап Дневника. */
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
            title = stringResource(R.string.profile_discipline),
            options = Discipline.entries.map { it to it.title },
            selected = profile.discipline,
            onSelect = { onProfileChange(profile.copy(discipline = it)) },
        )
        ChoiceRow(
            title = stringResource(R.string.profile_rank),
            options = Rank.entries.map { it to it.title },
            selected = profile.rank,
            onSelect = { onProfileChange(profile.copy(rank = it)) },
        )
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
        NumberField(
            label = stringResource(R.string.profile_correction),
            value = profile.correctionPercent,
            onValueChange = { onProfileChange(profile.copy(correctionPercent = it ?: 0.0)) },
        )
        NaturalSwitch(profile = profile, onProfileChange = onProfileChange)
        DietSettingsCard(profile = profile, today = today, onProfileChange = onProfileChange)
        MaxFields(data = data, today = today, onProfileChange = onProfileChange)
        ProgramCard(data = data, today = today, onProfileChange = onProfileChange)
        CyclesCard(profile)
        BackupCard(
            state = backup,
            onConnect = onBackupConnect,
            onBackup = onBackupNow,
            onRestore = onRestore,
            onDisconnect = onBackupDisconnect,
        )
    }
}

@Composable
private fun NaturalSwitch(profile: Profile, onProfileChange: (Profile) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        Switch(checked = profile.natural, onCheckedChange = { onProfileChange(profile.copy(natural = it)) })
        Text(stringResource(R.string.profile_natural))
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

@Composable
private fun ProgramCard(data: AppData, today: Long, onProfileChange: (Profile) -> Unit) {
    val profile = data.profile
    val choose = { id: String? ->
        if (id != profile.programId) {
            onProfileChange(profile.copy(programId = id, programStartEpochDay = id?.let { today }))
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.profile_program), style = MaterialTheme.typography.titleMedium)
            ProgramOption(
                title = stringResource(R.string.profile_program_none),
                summary = stringResource(R.string.profile_program_none_summary),
                selected = profile.programId == null,
                onClick = { choose(null) },
            )
            Programs.all.forEach { program ->
                val selected = program.id == profile.programId
                ProgramOption(
                    title = program.title,
                    summary = program.summary,
                    selected = selected,
                    onClick = { choose(program.id) },
                    mismatch = program.mismatches(profile).joinToString("; ").ifEmpty { null },
                )
                if (selected) ProgramDetails(program, data, today, onProfileChange)
            }
        }
    }
}

@Composable
private fun ProgramOption(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    mismatch: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = summary, style = MaterialTheme.typography.bodySmall)
            mismatch?.let {
                Text(
                    text = stringResource(R.string.profile_program_mismatch, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ProgramDetails(
    program: TrainingProgram,
    data: AppData,
    today: Long,
    onProfileChange: (Profile) -> Unit,
) {
    val profile = data.profile
    val progress = Programs.progress(program, profile.programStartEpochDay ?: today, data.sessions)
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(program.note, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.profile_program_progress, progress.done, program.sessions.size))
        OutlinedButton(onClick = { onProfileChange(profile.copy(programStartEpochDay = today)) }) {
            Text(stringResource(R.string.profile_program_restart))
        }
    }
}

@Composable
private fun CyclesCard(profile: Profile) {
    val matches = remember(profile) { CycleCatalog.select(profile).filter { it.fits }.take(ShownCycles) }
    if (matches.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.profile_cycles), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.profile_cycles_hint), style = MaterialTheme.typography.bodySmall)
            matches.forEach { match ->
                Text(
                    text = "${match.cycle.id} · ${match.cycle.kind.title}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(match.cycle.note)
            }
        }
    }
}

@Composable
internal fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
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
