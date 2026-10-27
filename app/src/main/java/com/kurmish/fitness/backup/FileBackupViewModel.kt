package com.kurmish.fitness.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kurmish.fitness.core.AppData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Разовая копия Дневника: сохранить в файл, загрузить из файла, отправить в другое приложение. */
class FileBackupViewModel(application: Application) : AndroidViewModel(application) {
    private val copies = JournalCopies(application)
    private val state = MutableStateFlow(FileStatus.Idle)

    /** Итог последнего действия. */
    val status: StateFlow<FileStatus> = state.asStateFlow()

    /** Сохраняет копию в выбранный файл [uri]. */
    fun exportTo(uri: Uri) {
        perform { copies.save(uri) }
    }

    /** Заменяет Дневник содержимым файла [uri]. */
    fun importFrom(uri: Uri, onRestored: (AppData) -> Unit) {
        perform {
            val copy = copies.load(uri)
            copy.data?.let { withContext(Dispatchers.Main) { onRestored(it) } }
            copy.status
        }
    }

    /** Готовит копию для отправки и отдаёт её в [onReady]. */
    fun prepareShare(onReady: (File) -> Unit) {
        perform {
            val file = copies.shareFile(LocalDate.now())
            file?.let { withContext(Dispatchers.Main) { onReady(it) } }
            if (file == null) FileStatus.Empty else FileStatus.Idle
        }
    }

    private fun perform(action: suspend () -> FileStatus) {
        state.value = FileStatus.Working
        viewModelScope.launch { state.value = withContext(Dispatchers.IO) { action() } }
    }
}
