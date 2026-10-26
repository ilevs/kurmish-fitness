package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.CycleCatalog
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.ProgramSession
import com.kurmish.fitness.core.Programs
import com.kurmish.fitness.core.TrainingProgram

private const val ShownCycles = 5

/** Циклы: выбор программы, её расписание по микроциклам и справочные циклы методики. */
@Composable
internal fun CyclesScreen(
    data: AppData,
    today: Long,
    onProfileChange: (Profile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val program = Programs.byId(data.profile.programId)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        ProgramCard(data = data, today = today, onProfileChange = onProfileChange)
        if (program != null) Schedule(program = program, data = data, today = today)
        CyclesCard(data.profile)
    }
}

@Composable
private fun Schedule(program: TrainingProgram, data: AppData, today: Long) {
    val next = remember(program, data, today) {
        Programs.progress(program, data.profile.programStartEpochDay ?: today, data.sessions).next
    }
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(text = stringResource(R.string.cycles_schedule), style = MaterialTheme.typography.titleMedium)
        program.sessions.groupBy { it.microcycle }.forEach { (microcycle, sessions) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
                    Text(
                        text = stringResource(R.string.cycles_microcycle, microcycle, program.microcycles),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    sessions.forEach { session -> SessionLines(session = session, next = session == next) }
                }
            }
        }
    }
}

@Composable
private fun SessionLines(session: ProgramSession, next: Boolean) {
    Column {
        Text(
            text = if (next) stringResource(R.string.cycles_next, session.day) else session.day,
            style = MaterialTheme.typography.labelLarge,
            color = if (next) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        session.items.forEach { item ->
            Text(
                text = listOfNotNull(item.name, item.scheme).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
