package com.kurmish.fitness.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurmish.fitness.core.AppData
import com.kurmish.fitness.core.HttpTelegramApi
import com.kurmish.fitness.core.JournalStore
import com.kurmish.fitness.core.ParsedWorkout
import com.kurmish.fitness.core.Profile
import com.kurmish.fitness.core.SyncReport
import com.kurmish.fitness.core.TelegramApi
import com.kurmish.fitness.core.TelegramException
import com.kurmish.fitness.core.TelegramState
import com.kurmish.fitness.core.TelegramSync
import com.kurmish.fitness.core.WorkoutSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes

/** Как прошла последняя синхронизация с Telegram. */
internal sealed interface SyncStatus {
    /** Ещё не запускалась. */
    data object Idle : SyncStatus

    /** Идёт обмен с Telegram. */
    data object Running : SyncStatus

    /** Прошла успешно. */
    data class Done(val report: SyncReport) : SyncStatus

    /** Нет сети: всё отправится, когда она появится. */
    data object Offline : SyncStatus

    /** Telegram ответил ошибкой. */
    data class Failed(val reason: String) : SyncStatus
}

/**
 * Держит дневник и профиль, сохраняет их в файл на устройстве и синхронизирует с ботом Telegram,
 * когда есть сеть.
 */
class TrainerViewModel(
    private val store: JournalStore,
    private val io: CoroutineDispatcher,
    private val telegramApi: (String) -> TelegramApi,
    networkAvailable: Flow<Unit>,
) : ViewModel() {
    private val state = MutableStateFlow(AppData())
    private val saving = Mutex()
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private val sync = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    /** Текущие данные. */
    val data: StateFlow<AppData> = state.asStateFlow()

    /** Состояние синхронизации с Telegram. */
    internal val syncStatus: StateFlow<SyncStatus> = sync.asStateFlow()

    init {
        viewModelScope.launch(io) {
            val loaded = store.load()
            state.update { current -> if (current.sessions.isEmpty()) loaded else current }
            syncLoop()
        }
        viewModelScope.launch { networkAvailable.collect { requestSync() } }
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
        requestSync()
    }

    /** Удаляет запись дневника; её копия в Telegram удалится при следующей синхронизации. */
    fun deleteWorkout(id: Long) {
        change { data ->
            val copy = data.sessions.firstOrNull { it.id == id }?.telegramMessageId
            val telegram = copy?.let { data.telegram.copy(pendingDeletes = data.telegram.pendingDeletes + it) }
            data.copy(sessions = data.sessions.filterNot { it.id == id }, telegram = telegram ?: data.telegram)
        }
        requestSync()
    }

    /** Обновляет профиль. */
    fun updateProfile(profile: Profile) {
        change { data -> data.copy(profile = profile) }
    }

    /**
     * Проверяет токен бота и включает синхронизацию. Если подключён другой бот, все записи
     * будут отправлены в его чат заново.
     */
    fun connectTelegram(token: String) {
        val trimmed = token.trim()
        viewModelScope.launch(io) {
            sync.value = SyncStatus.Running
            val bot = try {
                telegramApi(trimmed).getMe()
            } catch (rejected: TelegramException) {
                sync.value = SyncStatus.Failed(rejected.message.orEmpty())
                return@launch
            } catch (ignored: IOException) {
                sync.value = SyncStatus.Offline
                return@launch
            }
            change { data ->
                val telegram = data.telegram
                if (telegram.botId == bot.id) {
                    data.copy(telegram = telegram.copy(token = trimmed, botUsername = bot.username.orEmpty()))
                } else {
                    data.copy(
                        telegram = TelegramState(token = trimmed, botId = bot.id, botUsername = bot.username.orEmpty()),
                        sessions = data.sessions.map { it.copy(telegramMessageId = null) },
                    )
                }
            }
            requestSync()
        }
    }

    /** Выключает синхронизацию. Чат и отметки об отправке сохраняются на случай повторного подключения. */
    fun disconnectTelegram() {
        change { data -> data.copy(telegram = data.telegram.copy(token = "")) }
        sync.value = SyncStatus.Idle
    }

    /** Просит синхронизироваться как можно скорее. */
    fun requestSync() {
        syncRequests.trySend(Unit)
    }

    private suspend fun syncLoop() {
        while (viewModelScope.isActive) {
            if (state.value.telegram.connected) syncOnce()
            withTimeoutOrNull(SyncPeriod) { syncRequests.receive() }
        }
    }

    private fun syncOnce() {
        val token = state.value.telegram.token
        sync.value = SyncStatus.Running
        // Пока шла сеть, бота могли отключить или сменить: тогда результат этого прохода не нужен.
        val guarded: ((AppData) -> AppData) -> AppData = { transform ->
            change { data -> if (data.telegram.token == token) transform(data) else data }
        }
        sync.value = try {
            SyncStatus.Done(TelegramSync(telegramApi(token), ZoneId.systemDefault(), guarded).run())
        } catch (rejected: TelegramException) {
            SyncStatus.Failed(rejected.message.orEmpty())
        } catch (ignored: IOException) {
            SyncStatus.Offline
        }
    }

    private fun change(transform: (AppData) -> AppData): AppData {
        val updated = state.updateAndGet(transform)
        // Пишем всегда последнее состояние: сохранения из разных потоков не перезапишут новое старым.
        viewModelScope.launch(io) { saving.withLock { store.save(state.value) } }
        return updated
    }

    /** Фабрика: дневник лежит во внутренней памяти приложения. */
    companion object {
        private val SyncPeriod = 2.minutes

        /** Создаёт модель с файлом дневника приложения. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app: Application = checkNotNull(this[APPLICATION_KEY])
                TrainerViewModel(
                    store = JournalStore(File(app.filesDir, "journal.json")),
                    io = Dispatchers.IO,
                    telegramApi = ::HttpTelegramApi,
                    networkAvailable = app.networkAvailable(),
                )
            }
        }
    }
}

/** Сообщает о каждом появлении сети. */
internal fun Application.networkAvailable(): Flow<Unit> = callbackFlow {
    val manager = getSystemService(ConnectivityManager::class.java)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(Unit)
        }
    }
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}
