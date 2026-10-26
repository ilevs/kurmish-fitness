package com.kurmish.fitness.core

import kotlinx.serialization.Serializable

/** Спортивный разряд или уровень подготовки, по возрастанию. */
@Serializable
enum class Rank(val title: String) {
    Novice("Без разряда"),
    Third("III разряд"),
    Second("II разряд"),
    First("I разряд"),
    Kms("КМС"),
    Ms("МС"),
    Msmk("МСМК"),
}

/** Направление подготовки. */
@Serializable
enum class Discipline(val title: String) {
    Powerlifting("Троеборье"),
    Bench("Жим лёжа"),
    DeadliftBench("Тяга и жим"),
    OverheadPress("Жим стоя"),
    Biceps("Подъём на бицепс"),
    Bodybuilding("Бодибилдинг"),
    Armwrestling("Армрестлинг"),
}

/** Задача периода. */
@Serializable
enum class Goal(val title: String) {
    Strength("Сила"),
    Endurance("Силовая выносливость"),
    Peak("Выход на пик"),
    Mass("Масса"),
    Mixed("Смешанный"),
}

/**
 * Данные атлета, от которых зависят советы.
 *
 * @property bodyweightKg собственный вес; null, если не указан.
 * @property discipline направление.
 * @property rank уровень подготовки.
 * @property goal задача периода.
 * @property daysPerWeek сколько тренировок в неделю атлет может себе позволить.
 * @property correctionPercent процент корректировки: на сколько растёт расчётный ПМ от недели к неделе.
 * @property natural тренируется без фармакологии.
 * @property manualMaxes ПМ, введённые вручную, в кг.
 * @property programId выбранная программа из [Programs]; null — без программы.
 * @property programStartEpochDay с какого дня записи дневника считаются тренировками программы.
 */
@Serializable
data class Profile(
    val bodyweightKg: Double? = null,
    val discipline: Discipline = Discipline.Powerlifting,
    val rank: Rank = Rank.Second,
    val goal: Goal = Goal.Strength,
    val daysPerWeek: Int = DEFAULT_DAYS,
    val correctionPercent: Double = DEFAULT_CORRECTION,
    val natural: Boolean = true,
    val manualMaxes: Map<Exercise, Double> = emptyMap(),
    val programId: String? = null,
    val programStartEpochDay: Long? = null,
) {
    /** Значения по умолчанию. */
    companion object {
        /** Три тренировки в неделю. */
        const val DEFAULT_DAYS = 3

        /** Рекомендованный процент корректировки для натурального атлета. */
        const val DEFAULT_CORRECTION = 0.5
    }
}
