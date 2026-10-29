package com.kurmish.fitness.core

/**
 * Программы на 1–3 тренировки в неделю.
 *
 * - Поддержание: одна тренировка на всё тело. Подходов мало, но вес базовых движений не снижается:
 *   так форма держится при урезанном объёме.
 * - Всё тело дважды: тренировки А и Б, каждая часть тела два раза в неделю.
 * - Верх и низ через раз: три тренировки, верх и низ чередуются, каждая тренировка идёт 3 раза за 2 недели.
 *
 * Вес в диапазонах повторов («3×6-8») растёт по двойной прогрессии ([DoubleProgression]).
 */
internal object ShortPrograms {
    private const val MAINTENANCE_PERCENT = 78
    private const val MAINTENANCE_WEEKS = 4
    private const val TWICE_WORK_WEEKS = 6
    private const val UPPER_LOWER_WORK_WEEKS = 6
    private const val UPPER_LOWER_DELOAD_WEEKS = 2
    private const val EASY_WEEKS = 2

    /** Уровень упражнения в тренировке: от него зависят подходы и повторы. */
    private enum class Tier(val easy: String, val hard: String, val deload: String) {
        /** Базовое движение. */
        Main("3×6-8", "4×6-8", "2×6"),

        /** Становая: повторов меньше, чтобы техника не разваливалась к концу подхода. */
        Deadlift("3×5-6", "3×5-6", "2×5"),

        /** Второе упражнение на часть тела. */
        Second("2×10-12", "3×10-12", "2×10"),

        /** Руки, икры, пресс. */
        Small("1×12-15", "2×12-15", "1×12"),
    }

    private class Slot(val name: String, val tier: Tier)

    private fun item(name: String, scheme: String): ProgramItem = ProgramItem(name, scheme, null, emptyList())

    private fun easy(scheme: String) = "$scheme, запас 2–3 повтора: неделя вполсилы"

    private fun hard(scheme: String) = "$scheme, запас 1–2 повтора"

    private fun deload(scheme: String) = "$scheme, разгрузка: вес прошлой недели, без отказа"

    /** Одна тренировка в неделю: держим вес базовых движений, объём урезан. */
    val maintenance = TrainingProgram(
        id = "maintenance",
        title = "Поддержание",
        note = "Одна тренировка на всё тело сохраняет силу и мышцы, но не растит их: подходов мало, а вес " +
            "базовых движений прежний. Для отпуска, загруженного периода или сушки.",
        daysPerWeek = 1,
        goal = Goal.Mixed,
        sessions = (1..MAINTENANCE_WEEKS).map { week ->
            ProgramSession(
                "Всё тело",
                week,
                listOf(
                    ProgramItem("Присед", "3×5", Exercise.Squat, listOf(MAINTENANCE_PERCENT)),
                    ProgramItem("Жим лёжа", "3×5", Exercise.Bench, listOf(MAINTENANCE_PERCENT)),
                    item(Exercise.BentOverRow.title, "3×6-8"),
                    item(Exercise.StiffLegDeadlift.title, "2×6-8"),
                    item(Exercise.OverheadPress.title, "2×6-8"),
                    item(Exercise.PullUp.title, "2×мах, запас 1–2 повтора"),
                ),
            )
        },
    )

    private val twiceA = listOf(
        Slot(Exercise.Squat.title, Tier.Main),
        Slot(Exercise.StiffLegDeadlift.title, Tier.Main),
        Slot(Exercise.Bench.title, Tier.Main),
        Slot(Exercise.BentOverRow.title, Tier.Main),
        Slot(Exercise.BicepsCurl.title, Tier.Second),
    )

    private val twiceB = listOf(
        Slot(Exercise.Deadlift.title, Tier.Deadlift),
        Slot(Exercise.LegCurl.title, Tier.Second),
        Slot(Exercise.OverheadPress.title, Tier.Main),
        Slot(Exercise.PullUp.title, Tier.Main),
        Slot(Exercise.FrenchPress.title, Tier.Second),
    )

    /** Схема слота на неделе [week]: первые две недели вполсилы, потом тяжело, после [work] недель — разгрузка. */
    private fun scheme(slot: Slot, week: Int, work: Int): String = when {
        week > work -> deload(slot.tier.deload)
        week <= EASY_WEEKS -> easy(slot.tier.easy)
        else -> hard(slot.tier.hard)
    }

    private fun session(title: String, slots: List<Slot>, week: Int, work: Int) =
        ProgramSession(title, week, slots.map { item(it.name, scheme(it, week, work)) })

    /** Всё тело дважды в неделю: А и Б, шесть рабочих недель и неделя разгрузки. */
    val fullBodyTwice = TrainingProgram(
        id = "full-body-twice",
        title = "Всё тело дважды",
        note = "Тренировки А и Б на всё тело, между ними 2–3 дня отдыха. Первые две недели вполсилы, " +
            "потом вес растёт: когда во всех подходах взят верх диапазона повторов, прибавь шаг. " +
            "Седьмая неделя — разгрузка.",
        daysPerWeek = 2,
        goal = Goal.Mass,
        sessions = (1..TWICE_WORK_WEEKS + 1).flatMap { week ->
            listOf(
                session("Тренировка А", twiceA, week, TWICE_WORK_WEEKS),
                session("Тренировка Б", twiceB, week, TWICE_WORK_WEEKS),
            )
        },
    )

    private val upper = listOf(
        Slot(Exercise.Bench.title, Tier.Main),
        Slot(Exercise.BentOverRow.title, Tier.Main),
        Slot(Exercise.InclineBench.title, Tier.Second),
        Slot(Exercise.LatPulldown.title, Tier.Second),
        Slot(Exercise.FrenchPress.title, Tier.Small),
        Slot(Exercise.BicepsCurl.title, Tier.Small),
    )

    private val lower = listOf(
        Slot(Exercise.Squat.title, Tier.Main),
        Slot(Exercise.StiffLegDeadlift.title, Tier.Main),
        Slot(Exercise.LegPress.title, Tier.Second),
        Slot(Exercise.LegCurl.title, Tier.Second),
        Slot(Exercise.StandingCalfRaise.title, Tier.Small),
        Slot(Exercise.Abs.title, Tier.Small),
    )

    /** Верх и низ через раз, три тренировки в неделю: шесть рабочих недель и две разгрузки. */
    val upperLowerThree = TrainingProgram(
        id = "upper-lower-three",
        title = "Верх и низ через раз",
        note = "Три тренировки через день: на одной неделе верх, низ, верх, на следующей низ, верх, низ. " +
            "Первые две недели вполсилы, потом вес растёт: когда во всех подходах взят верх диапазона " +
            "повторов, прибавь шаг. Последние две недели — разгрузка.",
        daysPerWeek = 3,
        goal = Goal.Mass,
        sessions = (1..UPPER_LOWER_WORK_WEEKS + UPPER_LOWER_DELOAD_WEEKS).flatMap { week ->
            val order = if (week % 2 == 1) listOf(upper, lower, upper) else listOf(lower, upper, lower)
            order.map { slots ->
                session(if (slots === upper) "Верх" else "Низ", slots, week, UPPER_LOWER_WORK_WEEKS)
            }
        },
    )
}
