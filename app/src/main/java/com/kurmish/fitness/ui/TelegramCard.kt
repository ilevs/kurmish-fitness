package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.kurmish.fitness.R
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.TelegramState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SyncTimeFormat = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru-RU"))
private const val BotFatherUrl = "https://t.me/BotFather"

/**
 * Синхронизация Дневника с ботом Telegram: подключение по токену, состояние, ручной запуск.
 *
 * @param data дневник и настройки бота.
 * @param status как прошла последняя синхронизация.
 * @param onConnect проверить токен и подключить бота.
 * @param onDisconnect выключить синхронизацию.
 * @param onSync синхронизировать сейчас.
 */
@Composable
internal fun TelegramCard(
    data: AppData,
    status: SyncStatus,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val telegram = data.telegram
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.telegram_title), style = MaterialTheme.typography.titleMedium)
            when {
                !telegram.connected -> ConnectForm(onConnect)
                telegram.chatId == null -> WaitingForStart(telegram, onSync, onDisconnect)
                else -> Connected(telegram, data.sessions.count { it.awaitsTelegram }, onSync, onDisconnect)
            }
            statusText(status, telegram)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status is SyncStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectForm(onConnect: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var token by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(stringResource(R.string.telegram_intro))
        OutlinedButton(onClick = { uriHandler.openUri(BotFatherUrl) }) {
            Text(stringResource(R.string.telegram_open_botfather))
        }
        OutlinedTextField(
            value = token,
            onValueChange = { token = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.telegram_token)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(enabled = token.isNotBlank(), onClick = { onConnect(token) }) {
            Text(stringResource(R.string.telegram_connect))
        }
    }
}

@Composable
private fun WaitingForStart(telegram: TelegramState, onSync: () -> Unit, onDisconnect: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(stringResource(R.string.telegram_press_start, telegram.botUsername))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Button(onClick = { uriHandler.openUri("https://t.me/${telegram.botUsername}") }) {
                Text(stringResource(R.string.telegram_open_bot))
            }
            OutlinedButton(onClick = onSync) { Text(stringResource(R.string.telegram_check)) }
        }
        TextButton(onClick = onDisconnect) { Text(stringResource(R.string.telegram_disconnect)) }
    }
}

@Composable
private fun Connected(telegram: TelegramState, pending: Int, onSync: () -> Unit, onDisconnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(stringResource(R.string.telegram_connected, telegram.botUsername))
        if (pending > 0) Text(stringResource(R.string.telegram_pending, pending))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Button(onClick = onSync) { Text(stringResource(R.string.telegram_sync)) }
            TextButton(onClick = onDisconnect) { Text(stringResource(R.string.telegram_disconnect)) }
        }
    }
}

@Composable
private fun statusText(status: SyncStatus, telegram: TelegramState): String? = when (status) {
    SyncStatus.Running -> stringResource(R.string.telegram_running)
    SyncStatus.Offline -> stringResource(R.string.telegram_offline)
    is SyncStatus.Failed -> stringResource(R.string.telegram_failed, status.reason)
    is SyncStatus.Done, SyncStatus.Idle -> telegram.lastSyncEpochSecond?.takeIf { telegram.connected }?.let { second ->
        val time = Instant.ofEpochSecond(second).atZone(ZoneId.systemDefault()).format(SyncTimeFormat)
        stringResource(R.string.telegram_last_sync, time)
    }
}
