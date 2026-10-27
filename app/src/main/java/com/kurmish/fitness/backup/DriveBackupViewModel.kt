package com.kurmish.fitness.backup

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kurmish.fitness.core.AppData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Что показывает карточка Google Диска.
 *
 * @property fileName имя файла копии; null — файл не выбран.
 * @property lastSuccessEpochSecond когда копия последний раз записана.
 * @property lost файл перестал открываться.
 * @property status итог последнего действия.
 */
data class DriveBackupUi(
    val fileName: String? = null,
    val lastSuccessEpochSecond: Long? = null,
    val lost: Boolean = false,
    val status: FileStatus = FileStatus.Idle,
)

/** Копия Дневника в файле на Google Диске, который обновляется после каждой записи. */
class DriveBackupViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = DriveSettings(application)
    private val copies = JournalCopies(application)
    private val state = MutableStateFlow(DriveBackupUi())
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    /** Состояние для экрана. */
    val ui: StateFlow<DriveBackupUi> = state.asStateFlow()

    init {
        settings.preferences.registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }

    /** Новый файл [uri]: запоминает его и сразу пишет копию. */
    fun create(uri: Uri) {
        perform { if (settings.link(uri)) write(uri) else FileStatus.Failed }
    }

    /** Существующая копия [uri]: восстанавливает из неё Дневник и дальше обновляет её. */
    fun restoreFrom(uri: Uri, onRestored: (AppData) -> Unit) {
        perform {
            val status = restore(uri, onRestored)
            if (status == FileStatus.Restored && !settings.link(uri)) FileStatus.Failed else status
        }
    }

    /** Записывает копию сейчас. */
    fun backupNow() {
        val uri = settings.uri ?: return
        perform { write(uri) }
    }

    /** Восстанавливает Дневник из выбранного файла. */
    fun restore(onRestored: (AppData) -> Unit) {
        val uri = settings.uri ?: return
        perform { restore(uri, onRestored) }
    }

    /** Забывает файл; сам файл на Диске остаётся. */
    fun unlink() {
        settings.unlink()
        DriveBackup.cancel(getApplication())
        state.update { it.copy(status = FileStatus.Idle) }
    }

    override fun onCleared() {
        settings.preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun write(uri: Uri): FileStatus {
        val status = copies.save(uri)
        val bytes = copies.raw()
        when {
            status == FileStatus.Saved && bytes != null ->
                settings.markSent(Backup.fingerprint(bytes), Instant.now().epochSecond)
            status == FileStatus.Failed -> settings.markLost()
        }
        return status
    }

    private suspend fun restore(uri: Uri, onRestored: (AppData) -> Unit): FileStatus {
        val copy = copies.load(uri)
        copy.data?.let { withContext(Dispatchers.Main) { onRestored(it) } }
        return copy.status
    }

    private fun perform(action: suspend () -> FileStatus) {
        state.update { it.copy(status = FileStatus.Working) }
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { action() }
            state.update { it.copy(status = status) }
        }
    }

    private fun refresh() {
        state.update { current ->
            current.copy(
                fileName = settings.uri?.let { settings.fileName },
                lastSuccessEpochSecond = settings.lastSuccessEpochSecond,
                lost = settings.lost,
            )
        }
    }
}
