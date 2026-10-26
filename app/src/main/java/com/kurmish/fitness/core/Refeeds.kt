package com.kurmish.fitness.core

/** Расписание рефидов по диетической категории и интенсивности тренировок. */
internal object Refeeds {
    private const val WEEK = 7
    private const val MAX_FREE_MEALS = 2
    private const val CATEGORY_ONE = 1
    private const val CATEGORY_TWO = 2
    private const val CATEGORY_THREE = 3
    private const val CATEGORY_THREE_NO_REFEED_DAYS = 42L

    // Углеводы за рефид на килограмм сухой массы.
    private val carbsPerLeanKg = mapOf(RefeedLength.FiveHours to 3..6, RefeedLength.OneDay to 8..12)

    /**
     * Строка таблицы рефидов.
     *
     * @property length длительность.
     * @property every раз во сколько дней.
     * @property alternative другой вариант из той же строки.
     */
    data class Plan(val length: RefeedLength, val every: IntRange, val alternative: String? = null)

    // Таблица рефидов: (категория по жиру, тренируется интенсивно) → рефид.
    private val table = mapOf(
        (CATEGORY_ONE to true) to Plan(RefeedLength.OneDay, 7..7, "или 5 часов каждые 3–5 дней"),
        (CATEGORY_ONE to false) to Plan(RefeedLength.OneDay, 7..10),
        (CATEGORY_TWO to true) to Plan(RefeedLength.FiveHours, 7..7, "или 1 день каждые 10–14 дней"),
        (CATEGORY_TWO to false) to Plan(RefeedLength.FiveHours, 14..14, "или 1 день каждые 14 дней"),
        (CATEGORY_THREE to true) to Plan(RefeedLength.FiveHours, 10..14),
        (CATEGORY_THREE to false) to Plan(RefeedLength.FiveHours, 21..21),
    )

    /** Строка таблицы рефидов для категории [category]. */
    fun plan(category: Int, intense: Boolean): Plan = table.getValue(category to intense)

    /** Следующий рефид: от последнего отмеченного или от начала фазы. */
    fun next(context: DietContext): Refeed {
        val plan = plan(context.category, context.intense)
        val last = context.settings.refeedDays.filter { it >= context.start }.maxOrNull()
        val wait = if (context.category == CATEGORY_THREE) CATEGORY_THREE_NO_REFEED_DAYS else plan.every.first.toLong()
        val next = last?.plus(plan.every.first) ?: (context.start + wait)
        val perKg = carbsPerLeanKg.getValue(plan.length)
        return Refeed(
            length = plan.length,
            everyDays = plan.every,
            nextEpochDay = next,
            due = context.today >= next,
            doneToday = last == context.today,
            carbsG = (context.leanKg * perKg.first).toInt()..(context.leanKg * perKg.last).toInt(),
            alternative = plan.alternative,
        )
    }

    /** Сколько свободных приёмов пищи в неделю: еженедельный рефид занимает место одного из них. */
    fun freeMealsPerWeek(refeed: Refeed?): Int =
        if (refeed != null && refeed.everyDays.first <= WEEK) MAX_FREE_MEALS - 1 else MAX_FREE_MEALS
}
