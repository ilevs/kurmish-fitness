package com.kurmish.fitness.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Всё, что приложение хранит на устройстве.
 *
 * @property profile данные атлета.
 * @property sessions записанные тренировки, новые в конце.
 * @property telegram настройки и состояние синхронизации с Telegram.
 */
@Serializable
data class AppData(
    val profile: Profile = Profile(),
    val sessions: List<WorkoutSession> = emptyList(),
    val telegram: TelegramState = TelegramState(),
) {
    /** Следующий свободный номер записи. */
    val nextId: Long
        get() = (sessions.maxOfOrNull { it.id } ?: 0L) + 1

    /**
     * Лучшая оценка разового максимума по дневнику за последние [days] дней.
     *
     * @return упражнение → оценка ПМ в кг и дата подхода, по которому она получена.
     */
    fun estimatedMaxes(today: Long, days: Int = RECENT_DAYS): Map<Exercise, MaxEstimate> = sessions
        .filter { today - it.epochDay in 0..days }
        .flatMap { session ->
            session.entries.flatMap { entry ->
                entry.sets.mapNotNull { set ->
                    val weight = set.weightKg ?: return@mapNotNull null
                    val estimate = Strength.estimateMax(weight, set.reps) ?: return@mapNotNull null
                    entry.exercise?.let { it to MaxEstimate(estimate, session.epochDay) }
                }
            }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, estimates) -> estimates.maxBy { it.kg } }

    /** Действующий ПМ: введённый вручную, а если его нет, то оценка по дневнику. */
    fun currentMax(exercise: Exercise, today: Long): Double? =
        profile.manualMaxes[exercise] ?: estimatedMaxes(today)[exercise]?.kg

    /** Значения по умолчанию. */
    companion object {
        /** За сколько дней дневник учитывается при оценке ПМ. */
        const val RECENT_DAYS = 60
    }
}

/**
 * Оценка максимума.
 *
 * @property kg вес.
 * @property epochDay дата подхода.
 */
data class MaxEstimate(val kg: Double, val epochDay: Long)

/** Хранит [AppData] одним JSON-файлом в памяти приложения, без сети. */
class JournalStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    /** Читает данные; при отсутствии или порче файла возвращает пустые. */
    fun load(): AppData = runCatching { json.decodeFromString<AppData>(file.readText()) }.getOrDefault(AppData())

    /** Записывает атомарно: через временный файл, чтобы не потерять дневник при сбое. */
    fun save(data: AppData) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(AppData.serializer(), data))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }
}
