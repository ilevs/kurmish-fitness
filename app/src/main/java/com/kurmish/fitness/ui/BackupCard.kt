package com.kurmish.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import com.kurmish.fitness.backup.BackupStatus
import com.kurmish.fitness.backup.BackupUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val BackupTimeFormat = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru-RU"))
private const val AppPasswordsUrl = "https://id.yandex.ru/security/app-passwords"

/**
 * Бэкап Дневника на Яндекс Диск: вход, ручная отправка, восстановление на новом телефоне.
 *
 * @param state что показать.
 * @param onConnect запомнить логин и пароль приложения.
 * @param onBackup отправить копию сейчас.
 * @param onRestore заменить Дневник копией с Диска.
 * @param onDisconnect забыть вход.
 */
@Composable
internal fun BackupCard(
    state: BackupUi,
    onConnect: (String, String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
            if (state.connected) {
                Connected(state, onBackup, onRestore, onDisconnect)
            } else {
                LoginForm(onConnect)
            }
            statusText(state)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.status == BackupStatus.WrongPassword || state.status == BackupStatus.BadBackup) {
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
private fun LoginForm(onConnect: (String, String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(stringResource(R.string.backup_intro))
        OutlinedButton(onClick = { uriHandler.openUri(AppPasswordsUrl) }) {
            Text(stringResource(R.string.backup_open_passwords))
        }
        OutlinedTextField(
            value = login,
            onValueChange = { login = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.backup_login)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.backup_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(enabled = login.isNotBlank() && password.isNotBlank(), onClick = { onConnect(login, password) }) {
            Text(stringResource(R.string.backup_connect))
        }
    }
}

@Composable
private fun Connected(state: BackupUi, onBackup: () -> Unit, onRestore: () -> Unit, onDisconnect: () -> Unit) {
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(stringResource(R.string.backup_connected, state.login))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Button(enabled = state.status != BackupStatus.Working, onClick = onBackup) {
                Text(stringResource(R.string.backup_now))
            }
            OutlinedButton(enabled = state.status != BackupStatus.Working, onClick = { confirmRestore = true }) {
                Text(stringResource(R.string.backup_restore))
            }
        }
        TextButton(onClick = onDisconnect) { Text(stringResource(R.string.backup_disconnect)) }
        if (confirmRestore) {
            AlertDialog(
                onDismissRequest = { confirmRestore = false },
                title = { Text(stringResource(R.string.backup_restore_title)) },
                text = { Text(stringResource(R.string.backup_restore_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmRestore = false
                            onRestore()
                        },
                    ) { Text(stringResource(R.string.backup_restore)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.editor_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun statusText(state: BackupUi): String? = when (state.status) {
    BackupStatus.Working -> stringResource(R.string.backup_working)
    BackupStatus.Restored -> stringResource(R.string.backup_restored)
    BackupStatus.NothingToRestore -> stringResource(R.string.backup_nothing)
    BackupStatus.BadBackup -> stringResource(R.string.backup_bad)
    BackupStatus.Offline -> stringResource(R.string.backup_offline)
    BackupStatus.WrongPassword -> stringResource(R.string.backup_wrong_password)
    BackupStatus.Saved, BackupStatus.Idle -> state.lastSuccessEpochSecond?.takeIf { state.connected }?.let { second ->
        val time = Instant.ofEpochSecond(second).atZone(ZoneId.systemDefault()).format(BackupTimeFormat)
        stringResource(R.string.backup_last, time)
    }
}
