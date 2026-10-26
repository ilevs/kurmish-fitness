package com.kurmish.fitness.core

import kotlinx.serialization.Serializable

/**
 * Одна строка подходов: вес × повторы × подходы.
 *
 * @property weightKg вес снаряда; null для собственного веса.
 * @property reps повторы в подходе.
 * @property sets сколько подходов сделано с этим весом.
 */
@Serializable
data class SetGroup(val weightKg: Double?, val reps: Int, val sets: Int = 1)

/**
 * Упражнение в тренировке.
 *
 * @property exercise упражнение из каталога; null, если название не узнано.
 * @property customName название как его произнесли, для неузнанных упражнений.
 * @property sets выполненные подходы.
 */
@Serializable
data class ExerciseEntry(val exercise: Exercise?, val customName: String? = null, val sets: List<SetGroup>) {
    /** Название для экрана. */
    val displayName: String
        get() = exercise?.title ?: customName.orEmpty().replaceFirstChar { it.uppercaseChar() }
}

/** Самочувствие по итогам тренировки: от него зависит совет по проценту корректировки. */
@Serializable
enum class Feeling {
    /** Было легко. */
    Easy,

    /** Обычная нагрузка. */
    Normal,

    /** Было тяжело. */
    Hard,
}

/**
 * Записанная тренировка.
 *
 * @property id уникальный номер записи.
 * @property epochDay дата тренировки (дни с 1970-01-01).
 * @property rawText исходный текст или распознанная речь.
 * @property entries разобранные упражнения.
 * @property feeling самочувствие, если о нём сказали.
 * @property telegramMessageId номер копии записи в чате с ботом; null, пока копия не отправлена.
 * @property telegramParts сообщения боту, из которых собрана запись; пусто для записей из приложения.
 */
@Serializable
data class WorkoutSession(
    val id: Long,
    val epochDay: Long,
    val rawText: String,
    val entries: List<ExerciseEntry>,
    val feeling: Feeling = Feeling.Normal,
    val telegramMessageId: Long? = null,
    val telegramParts: List<TelegramPart> = emptyList(),
) {
    /** Запись пришла из Telegram. */
    val fromTelegram: Boolean
        get() = telegramParts.isNotEmpty()

    /** Запись сделана в приложении и ещё не отправлена в Telegram. */
    val awaitsTelegram: Boolean
        get() = telegramMessageId == null && !fromTelegram
}

/**
 * Сообщение боту, ставшее частью записи.
 *
 * @property messageId номер сообщения в чате.
 * @property text текст сообщения.
 */
@Serializable
data class TelegramPart(val messageId: Long, val text: String)
