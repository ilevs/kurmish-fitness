package com.kurmish.fitness.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurmish.fitness.backup.Backup
import com.kurmish.fitness.backup.BackupSettings
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.JournalStore
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.WorkoutSession
import com.kurmish.fitness.core.withSaved
import com.kurmish.fitness.core.withoutSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Держит дневник и профиль и сохраняет их в файл на устройстве. После каждого сохранения
 * вызывает [onSaved]: так копия уходит на Яндекс Диск.
 */
class TrainerViewModel(
    private val store: JournalStore,
    private val io: CoroutineDispatcher,
    private val onSaved: () -> Unit = {},
) : ViewModel() {
    private val state = MutableStateFlow(AppData())
    private val saving = Mutex()

    /** Текущие данные. */
    val data: StateFlow<AppData> = state.asStateFlow()

    init {
        viewModelScope.launch(io) {
            val loaded = store.load()
            state.update { current -> if (current.sessions.isEmpty()) loaded else current }
        }
    }

    /** Сегодняшняя дата в днях с 1970-01-01. */
    fun today(): Long = LocalDate.now().toEpochDay()

    /** Сохраняет запись: новую (с номером 0) добавляет, исправленную заменяет. */
    fun saveWorkout(session: WorkoutSession) {
        change { data -> data.withSaved(session) }
    }

    /** Удаляет запись дневника. */
    fun deleteWorkout(id: Long) {
        change { data -> data.withoutSession(id) }
    }

    /** Заменяет Дневник и профиль копией из бэкапа. */
    fun restore(restored: AppData) {
        change { restored }
    }

    /** Обновляет профиль. */
    fun updateProfile(profile: Profile) {
        change { data -> data.copy(profile = profile) }
    }

    private fun change(transform: (AppData) -> AppData) {
        state.update(transform)
        // Пишем всегда последнее состояние: сохранения из разных потоков не перезапишут новое старым.
        viewModelScope.launch(io) {
            saving.withLock { store.save(state.value) }
            onSaved()
        }
    }

    /** Фабрика: дневник лежит во внутренней памяти приложения. */
    companion object {
        /** Создаёт модель с файлом дневника приложения. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app: Application = checkNotNull(this[APPLICATION_KEY])
                TrainerViewModel(
                    store = JournalStore(Backup.journalFile(app)),
                    io = Dispatchers.IO,
                    onSaved = { if (BackupSettings(app).connected) Backup.schedule(app) },
                )
            }
        }
    }
}
