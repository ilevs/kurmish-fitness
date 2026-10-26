package com.kurmish.fitness.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.JournalStore
import com.kurmish.fitness.core.ParsedWorkout
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.WorkoutSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Держит дневник и профиль, сохраняет их в файл на устройстве. */
class TrainerViewModel(private val store: JournalStore, private val io: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow(AppData())

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

    /** Записывает разобранную тренировку. */
    fun addWorkout(rawText: String, parsed: ParsedWorkout) {
        change { data ->
            val session = WorkoutSession(
                id = data.nextId,
                epochDay = today() + parsed.dayOffset,
                rawText = rawText.trim(),
                entries = parsed.entries,
                feeling = parsed.feeling,
            )
            data.copy(sessions = data.sessions + session)
        }
    }

    /** Удаляет запись дневника. */
    fun deleteWorkout(id: Long) {
        change { data -> data.copy(sessions = data.sessions.filterNot { it.id == id }) }
    }

    /** Обновляет профиль. */
    fun updateProfile(profile: Profile) {
        change { data -> data.copy(profile = profile) }
    }

    private fun change(transform: (AppData) -> AppData) {
        val updated = state.updateAndGet(transform)
        viewModelScope.launch(io) { store.save(updated) }
    }

    /** Фабрика: дневник лежит во внутренней памяти приложения. */
    companion object {
        /** Создаёт модель с файлом дневника приложения. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app: Application = checkNotNull(this[APPLICATION_KEY])
                TrainerViewModel(JournalStore(File(app.filesDir, "journal.json")), Dispatchers.IO)
            }
        }
    }
}
