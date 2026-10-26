package com.kurmish.fitness.core

/**
 * Интегральная программа: у каждого базового движения свой круг из трёх типов дня по три недели —
 * максимальные усилия, объём и динамика. Движения сдвинуты по кругу, поэтому в одну неделю, например,
 * у приседа максимальные усилия, у жима объём, а у тяги динамика.
 *
 * - Максимальные усилия: 9–11 синглов до максимума дня в вариации движения; вариация меняется каждую неделю,
 *   ориентир последнего сингла — 95 % от ПМ этой вариации (рекорд по ней ведётся в Дневнике отдельно).
 * - Объём: соревновательное движение, 70 → 75 → 80 % ПМ.
 * - Динамика: 55 → 60 → 65 % ПМ, много коротких быстрых подходов.
 *
 * Подсобка — повторным методом, на профилактику суставов. Жим идёт два раза в неделю: во второй день
 * у него следующий по кругу тип.
 */
internal object IntegralProgram {
    private const val BLOCK = 3
    private const val MICROCYCLES = 9
    private const val MAX_PERCENT = 95

    private enum class DayType { Max, Volume, Dynamic }

    private val rotation = DayType.entries

    private val volume = listOf("5×5" to 70, "6×4" to 75, "6×3" to 80)
    private val dynamic = listOf("12×2" to 55, "10×3" to 60, "8×2" to 65)

    /**
     * Базовое движение.
     *
     * @property main соревновательное движение.
     * @property variations вариации для дней максимальных усилий, по неделе на каждую.
     * @property shift с какого типа дня движение начинает круг.
     * @property accessory подсобка: название и схема.
     */
    private class Lift(
        val main: Exercise,
        val variations: List<Exercise>,
        val shift: Int,
        val accessory: List<Pair<String, String>>,
    )

    private val squat = Lift(
        main = Exercise.Squat,
        variations = listOf(Exercise.PauseSquat, Exercise.BoxSquat, Exercise.FrontSquat),
        shift = 0,
        accessory = listOf("Разгибания голени в тренажёре" to "3×15-20", "Выпады" to "3×10", "Пресс" to "3×15"),
    )
    private val bench = Lift(
        main = Exercise.Bench,
        variations = listOf(Exercise.PauseBench, Exercise.BoardPress, Exercise.CloseGripBench),
        shift = 1,
        accessory = listOf("Подъём на бицепс" to "3×12-15", "Лицевая тяга" to "3×15-20", "Отжимания" to "3×мах"),
    )
    private val deadlift = Lift(
        main = Exercise.Deadlift,
        variations = listOf(Exercise.DeficitDeadlift, Exercise.BlockPull, Exercise.PauseDeadlift),
        shift = 2,
        accessory = listOf("Гиперэкстензия" to "3×12-15", "Тяга в наклоне" to "3×10", "Планка" to "3×мах"),
    )

    /** Программа на 4 дня и 9 микроциклов. */
    val program = TrainingProgram(
        id = "integral",
        title = "Интегральная",
        note = "У приседа, жима и тяги свой круг по 3 недели: максимальные усилия (синглы до максимума дня " +
            "в вариации) → объём (соревновательное движение 70–80 % ПМ) → динамика (55–65 % ПМ, быстро). " +
            "Подсобка — до 2 повторов в запасе, на здоровье суставов. После программы — разгрузочная неделя.",
        daysPerWeek = 4,
        goal = Goal.Strength,
        sessions = (0 until MICROCYCLES).flatMap { week ->
            listOf(
                ProgramSession("Д1 · присед", week + 1, day(squat, week)),
                ProgramSession("Д2 · жим", week + 1, day(bench, week)),
                ProgramSession("Д3 · тяга", week + 1, day(deadlift, week)),
                ProgramSession("Д4 · жим", week + 1, day(bench, week, extra = 1)),
            )
        },
    )

    private fun day(lift: Lift, week: Int, extra: Int = 0): List<ProgramItem> = listOf(main(lift, week, extra)) +
        lift.accessory.map { (name, scheme) -> ProgramItem(name, scheme, null, emptyList()) }

    private fun main(lift: Lift, week: Int, extra: Int): ProgramItem {
        val step = week % BLOCK
        return when (rotation[(lift.shift + week / BLOCK + extra) % rotation.size]) {
            DayType.Max -> {
                val variation = lift.variations[step % lift.variations.size]
                ProgramItem(
                    name = "${variation.title}: синглы до максимума дня",
                    scheme = "1×1, перед ним 8–10 синглов с шагом 5–10 %; это максимум дня без надрыва",
                    maxOf = variation,
                    percents = listOf(MAX_PERCENT),
                )
            }
            DayType.Volume -> volume[step].let { (scheme, percent) ->
                ProgramItem("${lift.main.title}: объём", scheme, lift.main, listOf(percent))
            }
            DayType.Dynamic -> dynamic[step].let { (scheme, percent) ->
                val name = "${lift.main.title}: динамика"
                ProgramItem(name, "$scheme, каждый повтор взрывно", lift.main, listOf(percent))
            }
        }
    }
}
