package com.kurmish.fitness.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.core.Exercise
import com.kurmish.fitness.core.LadderStep
import com.kurmish.fitness.core.Movement
import com.kurmish.fitness.core.Planes

/**
 * Справочник упражнений по плоскостям: в каждом типе движения лестница ступеней 1–6, у кора — часть
 * кора, у нежелательных упражнений — почему их лучше заменить. Открывается из Циклов.
 */
@Composable
internal fun PlanesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.cycles_back))
            }
            Text(text = stringResource(R.string.planes_open), style = MaterialTheme.typography.titleLarge)
        }
        Text(text = stringResource(R.string.planes_hint), style = MaterialTheme.typography.bodySmall)
        Movement.entries.forEach { PlaneCard(it) }
    }
}

@Composable
private fun PlaneCard(movement: Movement) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = movement.plane, style = MaterialTheme.typography.titleMedium)
            Planes.ladder(movement).forEach { StepRows(it) }
        }
    }
}

@Composable
private fun StepRows(step: LadderStep) {
    Column {
        Text(
            text = stringResource(R.string.planes_step, step.level),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        step.exercises.forEach { ExerciseRow(it) }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise) {
    val zone = Planes.coreZone(exercise)
    Column {
        Text(
            text = zone?.let { stringResource(R.string.planes_zone, exercise.title, it.title) } ?: exercise.title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Planes.discouraged[exercise]?.let {
            Text(
                text = stringResource(R.string.planes_discouraged, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Карточка в Циклах, открывающая справочник по плоскостям. */
@Composable
internal fun PlanesCard(onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.planes_open), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.planes_card_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.planes_open)) }
        }
    }
}
