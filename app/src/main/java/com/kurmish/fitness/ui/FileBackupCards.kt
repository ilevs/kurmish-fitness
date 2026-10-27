package com.kurmish.fitness.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.kurmish.fitness.R
import com.kurmish.fitness.backup.DriveBackupUi
import com.kurmish.fitness.backup.FileStatus
import com.kurmish.fitness.backup.JournalCopies
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val JsonType = "application/json"
private const val DriveFileName = "kurmish-fitness.json"
private val AnyFile = arrayOf("*/*")
private val Errors = setOf(FileStatus.BadBackup, FileStatus.Failed)

/**
 * Действия с файлом копии на Google Диске.
 *
 * @property onCreate запомнить новый файл и записать в него копию.
 * @property onRestoreFrom восстановить Дневник из выбранного файла и дальше обновлять его.
 * @property onBackup записать копию сейчас.
 * @property onRestore восстановить Дневник из запомненного файла.
 * @property onUnlink забыть файл.
 */
internal class DriveActions(
    val onCreate: (Uri) -> Unit,
    val onRestoreFrom: (Uri) -> Unit,
    val onBackup: () -> Unit,
    val onRestore: () -> Unit,
    val onUnlink: () -> Unit,
)

/**
 * Разовые действия с копией в файле.
 *
 * @property onExport сохранить копию в выбранный файл.
 * @property onImport заменить Дневник содержимым выбранного файла.
 * @property onShare подготовить файл копии для отправки.
 */
internal class FileActions(
    val onExport: (Uri) -> Unit,
    val onImport: (Uri) -> Unit,
    val onShare: ((File) -> Unit) -> Unit,
)

/**
 * Всё для карточек копии в файле.
 *
 * @property drive состояние Google Диска.
 * @property driveActions действия с Google Диском.
 * @property status итог последнего разового действия с файлом.
 * @property actions разовые действия с файлом.
 */
internal class FileBackups(
    val drive: DriveBackupUi,
    val driveActions: DriveActions,
    val status: FileStatus,
    val actions: FileActions,
)

/** Бэкап на Google Диск: файл выбирается в системном окне, дальше копия обновляется после каждой записи. */
@Composable
internal fun DriveBackupCard(state: DriveBackupUi, actions: DriveActions, modifier: Modifier = Modifier) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JsonType)) { uri ->
        uri?.let(actions.onCreate)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions.onRestoreFrom)
    }
    val linked = state.fileName != null
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.drive_title), style = MaterialTheme.typography.titleMedium)
            if (linked) {
                Text(stringResource(R.string.drive_linked, state.fileName.orEmpty()))
            } else {
                Text(stringResource(R.string.drive_intro))
            }
            Buttons(
                working = state.status == FileStatus.Working,
                primary = if (linked) R.string.backup_now else R.string.drive_create,
                onPrimary = if (linked) actions.onBackup else ({ create.launch(DriveFileName) }),
                secondary = if (linked) R.string.backup_restore else R.string.drive_choose,
                onSecondary = { confirm = true },
            )
            if (linked) TextButton(onClick = actions.onUnlink) { Text(stringResource(R.string.backup_disconnect)) }
            DriveStatus(state)
        }
    }
    if (confirm) {
        ConfirmRestore(
            onConfirm = {
                confirm = false
                if (linked) actions.onRestore() else open.launch(AnyFile)
            },
            onDismiss = { confirm = false },
        )
    }
}

/** Копия в отдельный файл: сохранить, загрузить или отправить, например в «Избранное» Telegram. */
@Composable
internal fun FileBackupCard(status: FileStatus, actions: FileActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.files_share_title)
    var confirm by rememberSaveable { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JsonType)) { uri ->
        uri?.let(actions.onExport)
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions.onImport)
    }
    val working = status == FileStatus.Working
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text = stringResource(R.string.files_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.files_intro))
            Buttons(
                working = working,
                primary = R.string.files_save,
                onPrimary = { save.launch(JournalCopies.fileName(LocalDate.now())) },
                secondary = R.string.files_load,
                onSecondary = { confirm = true },
            )
            OutlinedButton(enabled = !working, onClick = { actions.onShare { share(context, it, shareTitle) } }) {
                Text(stringResource(R.string.files_share))
            }
            statusText(status)?.let { StatusText(it, error = status in Errors) }
        }
    }
    if (confirm) {
        ConfirmRestore(
            onConfirm = {
                confirm = false
                load.launch(AnyFile)
            },
            onDismiss = { confirm = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(working: Boolean, primary: Int, onPrimary: () -> Unit, secondary: Int, onSecondary: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Button(enabled = !working, onClick = onPrimary) { Text(stringResource(primary)) }
        OutlinedButton(enabled = !working, onClick = onSecondary) { Text(stringResource(secondary)) }
    }
}

@Composable
private fun ConfirmRestore(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = { Text(stringResource(R.string.files_restore_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_restore)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

@Composable
private fun DriveStatus(state: DriveBackupUi) {
    val text = when {
        state.status != FileStatus.Idle -> statusText(state.status)
        state.lost -> stringResource(R.string.files_failed)
        else -> state.lastSuccessEpochSecond?.let { second ->
            val time = Instant.ofEpochSecond(second).atZone(ZoneId.systemDefault()).format(BackupTimeFormat)
            stringResource(R.string.backup_last, time)
        }
    }
    val error = state.status in Errors || (state.status == FileStatus.Idle && state.lost)
    text?.let { StatusText(it, error) }
}

@Composable
private fun StatusText(text: String, error: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun statusText(status: FileStatus): String? = when (status) {
    FileStatus.Working -> stringResource(R.string.files_working)
    FileStatus.Saved -> stringResource(R.string.files_saved)
    FileStatus.Restored -> stringResource(R.string.backup_restored)
    FileStatus.BadBackup -> stringResource(R.string.files_bad)
    FileStatus.Failed -> stringResource(R.string.files_failed)
    FileStatus.Empty -> stringResource(R.string.files_empty)
    FileStatus.Idle -> null
}

/** Открывает меню «Поделиться» с файлом [file]. */
private fun share(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(JsonType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title))
}
