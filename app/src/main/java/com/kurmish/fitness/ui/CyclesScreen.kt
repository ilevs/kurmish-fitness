package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.CycleCatalog
import com.kurmish.fitness.core.CycleDesign
import com.kurmish.fitness.core.MacroBlock
import com.kurmish.fitness.core.MacroPosition
import com.kurmish.fitness.core.Macrocycle
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.ProgramSession
import com.kurmish.fitness.core.TrainingProgram
import com.kurmish.fitness.core.kg
import java.util.Collections

private const val ShownCycles = 5

/**
 * Циклы: макроцикл из блоков-программ, программы и свои циклы с редактором, расписание текущего блока
 * и справочные циклы методики.
 */
@Composable
internal fun CyclesScreen(
    data: AppData,
    today: Long,
    onProfileChange: (Profile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = data.profile
    val position = remember(data, today) { Macrocycle.position(profile, data.sessions, today) }
    var editing by remember { mutableStateOf<CycleDesign?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        MacroCard(profile = profile, position = position, today = today, onProfileChange = onProfileChange)
        ProgramsCard(
            profile = profile,
            onAdd = { id -> onProfileChange(profile.withBlocks(profile.blockIds + id, today)) },
            onEdit = { editing = it },
        )
        position?.let { Schedule(program = it.program, next = it.progress.next) }
        CyclesCard(profile)
    }
    editing?.let { design ->
        CycleEditor(
            initial = design,
            onSave = { saved ->
                onProfileChange(profile.withDesign(saved))
                editing = null
            },
            onDelete = if (profile.designs.any { it.id == design.id }) {
                {
                    onProfileChange(profile.withoutDesign(design.id, today))
                    editing = null
                }
            } else {
                null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MacroCard(
    profile: Profile,
    position: MacroPosition?,
    today: Long,
    onProfileChange: (Profile) -> Unit,
) {
    val ids = profile.blockIds
    val change = { changed: List<String> -> onProfileChange(profile.withBlocks(changed, today)) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.cycles_macro), style = MaterialTheme.typography.titleMedium)
            if (ids.isEmpty()) {
                Text(text = stringResource(R.string.cycles_macro_empty), style = MaterialTheme.typography.bodySmall)
            }
            Macrocycle.blocks(profile, today).forEachIndexed { index, block ->
                BlockRow(
                    index = index,
                    block = block,
                    position = position?.takeIf { it.index == index },
                    onMove = { delta -> change(ids.swapped(index, index + delta)) },
                    onRemove = { change(ids.filterIndexed { i, _ -> i != index }) },
                    last = index == ids.lastIndex,
                )
            }
            if (ids.isNotEmpty()) {
                OutlinedButton(onClick = { onProfileChange(profile.copy(programStartEpochDay = today)) }) {
                    Text(stringResource(R.string.profile_program_restart))
                }
            }
        }
    }
}

@Composable
private fun BlockRow(
    index: Int,
    block: MacroBlock,
    position: MacroPosition?,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    last: Boolean,
) {
    Column {
        Text(
            text = stringResource(R.string.cycles_block, index + 1, block.program.title),
            style = MaterialTheme.typography.titleSmall,
            color = if (position != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "≈ ${block.startEpochDay.shortDate()} – ${block.endEpochDay.shortDate()} · ${block.program.summary}",
            style = MaterialTheme.typography.bodySmall,
        )
        position?.let {
            Text(
                text = stringResource(R.string.profile_program_progress, it.progress.done, block.program.sessions.size),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row {
            if (index > 0) {
                TextButton(onClick = { onMove(-1) }) { Text(stringResource(R.string.cycles_block_up)) }
            }
            if (!last) {
                TextButton(onClick = { onMove(1) }) { Text(stringResource(R.string.cycles_block_down)) }
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.cycles_block_remove)) }
        }
    }
}

@Composable
private fun ProgramsCard(profile: Profile, onAdd: (String) -> Unit, onEdit: (CycleDesign) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.cycles_programs), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.cycles_programs_hint), style = MaterialTheme.typography.bodySmall)
            profile.programs.forEach { program ->
                HorizontalDivider()
                ProgramRow(program = program, profile = profile, onAdd = onAdd, onEdit = onEdit)
            }
            val fresh = { CycleDesign.empty(CycleDesign.nextId(profile.designs), profile.goal) }
            OutlinedButton(onClick = { onEdit(fresh()) }) {
                Text(stringResource(R.string.cycles_new))
            }
        }
    }
}

@Composable
private fun ProgramRow(
    program: TrainingProgram,
    profile: Profile,
    onAdd: (String) -> Unit,
    onEdit: (CycleDesign) -> Unit,
) {
    val design = profile.designs.firstOrNull { it.id == program.id }
    Column {
        Text(text = program.title, style = MaterialTheme.typography.titleSmall)
        Text(text = program.summary, style = MaterialTheme.typography.bodySmall)
        program.mismatches(profile).joinToString("; ").ifEmpty { null }?.let {
            Text(
                text = stringResource(R.string.profile_program_mismatch, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (program.note.isNotBlank()) Text(text = program.note, style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { onAdd(program.id) }) { Text(stringResource(R.string.cycles_add_block)) }
            if (design != null) {
                TextButton(onClick = { onEdit(design) }) { Text(stringResource(R.string.cycles_edit)) }
            } else {
                TextButton(onClick = { onEdit(CycleDesign.copyOf(program, CycleDesign.nextId(profile.designs))) }) {
                    Text(stringResource(R.string.cycles_copy))
                }
            }
        }
    }
}

@Composable
private fun Schedule(program: TrainingProgram, next: ProgramSession?) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(
            text = stringResource(R.string.cycles_schedule, program.title),
            style = MaterialTheme.typography.titleMedium,
        )
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
                text = listOfNotNull(item.name, item.scheme, item.kg?.kg()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
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

private fun List<String>.swapped(from: Int, to: Int): List<String> =
    toMutableList().apply { Collections.swap(this, from, to) }
