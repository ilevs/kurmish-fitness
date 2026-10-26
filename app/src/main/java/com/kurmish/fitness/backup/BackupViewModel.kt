package com.kurmish.fitness.backup

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.CloudBackup
import com.kurmish.fitness.core.CloudException
import com.kurmish.fitness.core.JournalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** Чем закончилось последнее действие с бэкапом. */
enum class BackupStatus {
    /** Ничего не делали. */
    Idle,

    /** Идёт обмен с Диском. */
    Working,

    /** Копия сохранена на Диске. */
    Saved,

    /** Дневник восстановлен из копии. */
    Restored,

    /** На Диске ещё нет копии. */
    NothingToRestore,

    /** Файл на Диске не похож на Дневник. */
    BadBackup,

    /** Нет сети: копия уйдёт, когда она появится. */
    Offline,

    /** Диск не принял логин или пароль приложения. */
    WrongPassword,
}

/**
 * Что показывает карточка бэкапа.
 *
 * @property connected вход на Диск задан.
 * @property login логин Яндекса.
 * @property lastSuccessEpochSecond когда копия последний раз ушла на Диск.
 * @property status итог последнего действия.
 */
data class BackupUi(
    val connected: Boolean = false,
    val login: String = "",
    val lastSuccessEpochSecond: Long? = null,
    val status: BackupStatus = BackupStatus.Idle,
)

/** Вход на Яндекс Диск, ручная отправка копии и восстановление Дневника. */
class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = BackupSettings(application)
    private val store = JournalStore(Backup.journalFile(application))
    private val state = MutableStateFlow(BackupUi())

    // Фоновая отправка меняет настройки сама: экран узнаёт об этом через подписку.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    /** Состояние для экрана. */
    val ui: StateFlow<BackupUi> = state.asStateFlow()

    init {
        settings.preferences.registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }

    /** Запоминает вход и сразу отправляет копию: так видно, что логин и пароль подошли. */
    fun connect(login: String, password: String) {
        settings.connect(login, password)
        backupNow()
    }

    /** Отправляет копию сейчас; без сети она уйдёт сама, когда сеть появится. */
    fun backupNow() {
        val disk = settings.disk() ?: return
        perform {
            val bytes = store.raw() ?: return@perform BackupStatus.Saved
            try {
                CloudBackup.upload(disk, bytes, LocalDate.now())
                settings.markSent(Backup.fingerprint(bytes), Instant.now().epochSecond)
                BackupStatus.Saved
            } catch (rejected: CloudException) {
                failure(rejected)
            } catch (ignored: IOException) {
                Backup.schedule(getApplication())
                BackupStatus.Offline
            }
        }
    }

    /** Скачивает копию и отдаёт её [onRestored]; сам Дневник заменяет вызывающий. */
    fun restore(onRestored: (AppData) -> Unit) {
        val disk = settings.disk() ?: return
        perform {
            try {
                val bytes = CloudBackup.download(disk) ?: return@perform BackupStatus.NothingToRestore
                val restored = store.decode(bytes) ?: return@perform BackupStatus.BadBackup
                withContext(Dispatchers.Main) { onRestored(restored) }
                BackupStatus.Restored
            } catch (rejected: CloudException) {
                failure(rejected)
            } catch (ignored: IOException) {
                BackupStatus.Offline
            }
        }
    }

    /** Забывает вход. Копии на Диске остаются. */
    fun disconnect() {
        settings.disconnect()
        Backup.cancel(getApplication())
        state.update { it.copy(status = BackupStatus.Idle) }
    }

    override fun onCleared() {
        settings.preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun perform(action: suspend () -> BackupStatus) {
        state.update { it.copy(status = BackupStatus.Working) }
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { action() }
            state.update { it.copy(status = status) }
        }
    }

    private fun failure(rejected: CloudException): BackupStatus = if (rejected.unauthorized) {
        settings.markAuthError()
        BackupStatus.WrongPassword
    } else {
        BackupStatus.Offline
    }

    private fun refresh() {
        state.update { current ->
            current.copy(
                connected = settings.connected,
                login = settings.login,
                lastSuccessEpochSecond = settings.lastSuccessEpochSecond,
                status = if (settings.authError) BackupStatus.WrongPassword else current.status,
            )
        }
    }
}
