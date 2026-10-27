package com.kurmish.fitness.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.kurmish.fitness.R
import com.kurmish.fitness.core.Feeling
import com.kurmish.fitness.core.ParsedWorkout
import com.kurmish.fitness.core.WorkoutParser
import com.kurmish.fitness.ui.voice.OfflineSpeech
import com.kurmish.fitness.ui.voice.VoiceFailure
import com.kurmish.fitness.ui.voice.VoiceState
import com.kurmish.fitness.ui.voice.rememberOfflineSpeech

private const val InputMaxLines = 4

/**
 * Ввод тренировки одной строкой на самом верху Дневника: текст, микрофон и «Сохранить» внутри поля,
 * под ним живой предпросмотр разбора.
 */
@Composable
internal fun RecordForm(onSave: (String, ParsedWorkout) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { WorkoutParser.parse(text) }
    val saved = stringResource(R.string.log_saved)
    val speech = rememberOfflineSpeech { spoken ->
        text = listOf(text.trim(), spoken).filter { it.isNotEmpty() }.joinToString(", ")
        status = ""
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Gap)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                status = ""
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.log_hint), maxLines = 1) },
            maxLines = InputMaxLines,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VoiceButton(speech, onMessage = { status = it })
                    IconButton(
                        enabled = parsed.entries.isNotEmpty(),
                        onClick = {
                            onSave(text, parsed)
                            text = ""
                            status = saved
                        },
                    ) { Icon(painterResource(R.drawable.ic_send), stringResource(R.string.log_save)) }
                }
            },
        )
        voiceStatus(speech.state)?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        if (status.isNotEmpty()) Text(text = status, color = MaterialTheme.colorScheme.primary)
        if (text.isNotBlank()) ParsedPreview(parsed)
    }
}

/** Микрофон включает диктовку, квадрат выключает. Разрешение на микрофон спрашивается при первом нажатии. */
@Composable
private fun VoiceButton(speech: OfflineSpeech, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val denied = stringResource(R.string.log_voice_denied)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start() else onMessage(denied)
    }
    val active = speech.state == VoiceState.Listening
    val idle = speech.state == VoiceState.Idle || speech.state is VoiceState.Failed
    val onClick = {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        when {
            active -> speech.stop()
            allowed -> speech.start()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    if (active) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.log_voice_stop))
        }
    } else {
        IconButton(onClick = onClick, enabled = idle) {
            Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.log_voice))
        }
    }
}

@Composable
private fun voiceStatus(state: VoiceState): String? = when (state) {
    VoiceState.Idle -> null
    is VoiceState.Downloading -> stringResource(R.string.log_voice_downloading, state.percent)
    VoiceState.Preparing -> stringResource(R.string.log_voice_preparing)
    VoiceState.Listening -> stringResource(R.string.log_voice_listening)
    VoiceState.Finishing -> stringResource(R.string.log_voice_finishing)
    is VoiceState.Failed -> when (state.reason) {
        VoiceFailure.Download -> stringResource(R.string.log_voice_download_failed)
        VoiceFailure.Microphone -> stringResource(R.string.log_voice_failed)
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
