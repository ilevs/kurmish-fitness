package com.kurmish.fitness.core

/**
 * Ряд одинаковых подходов плана.
 *
 * @property sets сколько подходов.
 * @property reps повторы: число, диапазон «8-10» или «мах».
 * @property weightKg вес снаряда; null — свой вес или вес подбирается по самочувствию.
 */
data class PlannedSets(val sets: Int, val reps: String, val weightKg: Double?) {
    /** «3 × 5 по 60 кг» или «3 × мах». */
    val label: String
        get() = "$sets × $reps" + weightKg?.let { " по ${it.kg()}" }.orEmpty()
}

/** Часть занятия: от неё зависит, как пункт выглядит в чек-листе. */
enum class PlanPart {
    /** Разминка перед первым упражнением. */
    WarmUp,

    /** Рабочее упражнение. */
    Work,

    /** Заминка в конце. */
    CoolDown,
}

/**
 * Пункт чек-листа на сегодня.
 *
 * @property part разминка, упражнение или заминка.
 * @property title что делать.
 * @property exercise упражнение каталога, по которому запись в Дневнике закрывает пункт.
 * @property sets подходы с весом и повторами.
 * @property notes пояснения: шаги разминки, проценты от ПМ, особенности схемы.
 */
data class PlanItem(
    val part: PlanPart,
    val title: String,
    val exercise: Exercise?,
    val sets: List<PlannedSets>,
    val notes: List<String> = emptyList(),
) {
    /** Пункт для озвучки. */
    val speech: String
        get() = (listOf(title) + sets.map { it.label } + notes).joinToString(", ")

    /** Записи за сегодня, которые закрывают пункт: то же упражнение каталога. */
    fun recordedIn(entries: List<ExerciseEntry>): List<ExerciseEntry> =
        if (exercise == null) emptyList() else entries.filter { it.exercise == exercise }
}

/**
 * Конкретная тренировка на сегодня.
 *
 * @property title какая это тренировка: день программы или вариант общей тренировки.
 * @property items пункты по порядку: разминка, упражнения, заминка.
 * @property note что важно знать о плане в целом; null, если нечего добавить.
 */
data class DayPlan(val title: String, val items: List<PlanItem>, val note: String? = null) {
    /** План целиком для озвучки. */
    val speech: String
        get() = (listOf("$title.") + listOfNotNull(note) + items.map { it.speech + "." }).joinToString(" ")
}
