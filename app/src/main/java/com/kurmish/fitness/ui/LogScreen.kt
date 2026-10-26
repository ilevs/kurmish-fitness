package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurmish.fitness.R
import com.kurmish.fitness.core.Feeling
import com.kurmish.fitness.core.ParsedWorkout
import com.kurmish.fitness.core.WorkoutParser
import com.kurmish.fitness.ui.voice.rememberSpeechInput

private val InputMinHeight = 160.dp

/** Ввод тренировки текстом или голосом с живым предпросмотром разбора. */
@Composable
internal fun LogScreen(onSave: (String, ParsedWorkout) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { WorkoutParser.parse(text) }
    val voiceMissing = stringResource(R.string.log_voice_missing)
    val saved = stringResource(R.string.log_saved)
    val listen = rememberSpeechInput(prompt = stringResource(R.string.log_voice_prompt)) { spoken ->
        text = listOf(text.trim(), spoken).filter { it.isNotEmpty() }.joinToString(", ")
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                status = ""
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = InputMinHeight),
            placeholder = { Text(stringResource(R.string.log_hint)) },
        )
        Row {
            FilledTonalButton(onClick = { if (!listen()) status = voiceMissing }) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = null)
                Spacer(Modifier.width(Gap))
                Text(stringResource(R.string.log_voice))
            }
            Spacer(Modifier.width(Gap))
            Button(
                enabled = parsed.entries.isNotEmpty(),
                onClick = {
                    onSave(text, parsed)
                    text = ""
                    status = saved
                },
            ) { Text(stringResource(R.string.log_save)) }
        }
        if (status.isNotEmpty()) Text(text = status, color = MaterialTheme.colorScheme.primary)
        ParsedPreview(parsed)
    }
}

@Composable
private fun ParsedPreview(parsed: ParsedWorkout) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(text = stringResource(R.string.log_preview), style = MaterialTheme.typography.titleMedium)
        if (parsed.entries.isEmpty()) {
            Text(stringResource(R.string.log_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        parsed.entries.forEach { entry -> Text(entry.label()) }
        feelingLabel(parsed.feeling)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun feelingLabel(feeling: Feeling): String? = when (feeling) {
    Feeling.Easy -> stringResource(R.string.feeling_easy)
    Feeling.Hard -> stringResource(R.string.feeling_hard)
    Feeling.Normal -> null
}
