package com.kurmish.fitness.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kurmish.fitness.R
import com.kurmish.fitness.backup.BackupUi

/**
 * Что делает с бэкапом на Яндекс Диск экран резервной копии.
 *
 * @property onConnect подключить диск по логину и паролю приложения.
 * @property onBackup сохранить копию сейчас.
 * @property onRestore восстановить Дневник с диска.
 * @property onDisconnect отключить диск.
 */
internal class YandexActions(
    val onConnect: (String, String) -> Unit,
    val onBackup: () -> Unit,
    val onRestore: () -> Unit,
    val onDisconnect: () -> Unit,
)

/** Резервная копия Дневника: Яндекс Диск, Google Диск и файл. Открывается из Профиля, [onBack] возвращает туда. */
@Composable
internal fun BackupScreen(
    state: BackupUi,
    yandex: YandexActions,
    files: FileBackups,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        SubScreenHeader(title = stringResource(R.string.backup_screen_title), onBack = onBack)
        BackupCard(
            state = state,
            onConnect = yandex.onConnect,
            onBackup = yandex.onBackup,
            onRestore = yandex.onRestore,
            onDisconnect = yandex.onDisconnect,
        )
        DriveBackupCard(state = files.drive, actions = files.driveActions)
        FileBackupCard(status = files.status, actions = files.actions)
    }
}

/** Заголовок экрана, открытого из Профиля: стрелка назад и название. */
@Composable
internal fun SubScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.nav_back))
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}
