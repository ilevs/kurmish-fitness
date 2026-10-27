package com.kurmish.fitness.core

/** Нагрузка дня недели. */
internal enum class DayLoad {
    /** Основные движения по схеме цели или периода. */
    Heavy,

    /** Те же типы движения лёгким весом на 12 повторов: второй раз за неделю без лишней усталости. */
    Light,

    /** Без силовой работы: лёгкие упражнения своим весом, растяжка и МФР. */
    Recovery,
}

/**
 * Упражнение дня.
 *
 * @property exercise упражнение по умолчанию, его название — пункт плана для замены.
 * @property second второе упражнение на этот тип в неделе: 3 × 10 с запасом, а не схема основных.
 */
internal class SplitSlot(val exercise: Exercise, val second: Boolean = false)

/**
 * День недельного сплита.
 *
 * @property title название в плане.
 * @property slots упражнения по порядку.
 * @property load нагрузка.
 */
internal class SplitDay(val title: String, val slots: List<SplitSlot>, val load: DayLoad = DayLoad.Heavy)

/**
 * Как разложить тренировки по неделе при любом числе дней от 1 до 7.
 *
 * - 1–3 дня: тренировки на всё тело А и Б по очереди. Каждая состоит из всех шести типов движения,
 *   за две тренировки плоскости уравновешены. Одна тренировка в неделю только поддерживает форму.
 * - 4 дня: верх и низ по два раза, каждая часть тела дважды в неделю. Тяжёлое движение у каждого дня своё:
 *   горизонтальные жим и тяга, присед, вертикальные жим и тяга, становая; остальные плоскости идут вторым
 *   упражнением 3 × 10.
 * - 5 дней: те же четыре дня и лёгкая тренировка Б на всё тело. Она стоит перед двумя днями отдыха,
 *   потому что в ней вертикальный жим.
 * - 6 дней: верх и низ по три раза. Каждое движение за неделю идёт тяжело, вторым упражнением и легко.
 * - 7 дней: шесть дней как выше и один восстановительный. Без дня отдыха не обойтись, поэтому седьмой
 *   день без силовой работы.
 */
internal object WeekSplit {
    /** Тренировок в неделю, которые поддерживает планировщик. */
    val days: IntRange = 1..7

    private const val FULL_BODY_MAX = 3
    private const val UPPER_LOWER = 4
    private const val WITH_LIGHT = 5
    private const val TWICE_UPPER_LOWER = 6

    /** Общая тренировка А: присед, горизонтальные жим и тяга. */
    val fullA = SplitDay(
        "Общая тренировка А",
        listOf(
            SplitSlot(Exercise.Squat),
            SplitSlot(Exercise.Bench),
            SplitSlot(Exercise.BentOverRow),
            SplitSlot(Exercise.Hyperextension),
            SplitSlot(Exercise.FacePull),
            SplitSlot(Exercise.Abs),
        ),
    )

    /** Общая тренировка Б: становая, вертикальные жим и тяга, сплит-присед. */
    val fullB = SplitDay(
        "Общая тренировка Б",
        listOf(
            SplitSlot(Exercise.Deadlift),
            SplitSlot(Exercise.OverheadPress),
            SplitSlot(Exercise.PullUp),
            SplitSlot(Exercise.SplitSquat, second = true),
            SplitSlot(Exercise.ExternalRotation),
            SplitSlot(Exercise.SidePlank),
        ),
    )

    private val upperHorizontal = SplitDay(
        "Верх: жим лёжа и тяга в наклоне",
        listOf(
            SplitSlot(Exercise.Bench),
            SplitSlot(Exercise.BentOverRow),
            SplitSlot(Exercise.OverheadPress, second = true),
            SplitSlot(Exercise.PullUp, second = true),
            SplitSlot(Exercise.FacePull),
            SplitSlot(Exercise.Abs),
        ),
    )

    private val lowerSquat = SplitDay(
        "Низ: присед",
        listOf(
            SplitSlot(Exercise.Squat),
            SplitSlot(Exercise.StiffLegDeadlift, second = true),
            SplitSlot(Exercise.SplitSquat, second = true),
            SplitSlot(Exercise.ExternalRotation),
            SplitSlot(Exercise.LegRaise),
        ),
    )

    private val upperVertical = SplitDay(
        "Верх: жим стоя и подтягивания",
        listOf(
            SplitSlot(Exercise.OverheadPress),
            SplitSlot(Exercise.PullUp),
            SplitSlot(Exercise.InclineBench, second = true),
            SplitSlot(Exercise.DumbbellRow, second = true),
            SplitSlot(Exercise.ExternalRotation),
            SplitSlot(Exercise.SidePlank),
        ),
    )

    private val lowerDeadlift = SplitDay(
        "Низ: становая",
        listOf(
            SplitSlot(Exercise.Deadlift),
            SplitSlot(Exercise.FrontSquat, second = true),
            SplitSlot(Exercise.Lunge, second = true),
            SplitSlot(Exercise.FacePull),
            SplitSlot(Exercise.Abs),
        ),
    )

    private val lightFullB = SplitDay("Лёгкая тренировка Б на всё тело", fullB.slots, DayLoad.Light)
    private val lightUpper = SplitDay("Лёгкий верх", upperHorizontal.slots, DayLoad.Light)
    private val lightLower = SplitDay("Лёгкий низ", lowerSquat.slots, DayLoad.Light)

    /** Восстановительный день: лёгкие упражнения своим весом, после каждого подхода растяжка и МФР. */
    val recovery = SplitDay(
        "Восстановительная тренировка",
        listOf(
            SplitSlot(Exercise.PushUp),
            SplitSlot(Exercise.InvertedRow),
            SplitSlot(Exercise.GluteBridge),
            SplitSlot(Exercise.Lunge),
            SplitSlot(Exercise.ReverseCrunch),
        ),
        DayLoad.Recovery,
    )

    private val upperLower = listOf(upperHorizontal, lowerSquat, upperVertical, lowerDeadlift)

    /** Дни по порядку для [count] тренировок в неделю; при 1–3 цикл из двух тренировок идёт через недели. */
    fun week(count: Int): List<SplitDay> = when (count.coerceIn(days)) {
        in 1..FULL_BODY_MAX -> listOf(fullA, fullB)
        UPPER_LOWER -> upperLower
        WITH_LIGHT -> upperLower + lightFullB
        TWICE_UPPER_LOWER -> upperLower + lightUpper + lightLower
        else -> upperLower + lightUpper + lightLower + recovery
    }

    /** День для тренировки по счёту [done] (с 0) при [count] тренировках в неделю. */
    fun day(count: Int, done: Int): SplitDay = week(count).let { it[done % it.size] }

    /** Как устроена неделя и как расставить дни отдыха. */
    fun note(count: Int): String = when (count.coerceIn(days)) {
        1 ->
            "Одна тренировка в неделю только поддерживает форму, для роста нужно хотя бы две. " +
                "Тренировки А и Б чередуются по неделям."
        2 -> "Тренировки А и Б чередуются, между ними два-три дня отдыха, например пн и чт."
        FULL_BODY_MAX -> "Тренировки А и Б чередуются через день, после третьей тренировки недели два дня отдыха."
        UPPER_LOWER ->
            "Неделя: верх, низ, отдых, верх, низ. Каждая часть тела дважды в неделю, " +
                "у каждого дня своё тяжёлое движение."
        WITH_LIGHT -> "Неделя: верх, низ, верх, низ и лёгкая тренировка на всё тело перед двумя днями отдыха."
        TWICE_UPPER_LOWER ->
            "Неделя: верх и низ по три раза. Каждое движение идёт тяжело, вторым упражнением " +
                "и в лёгкий день."
        else -> "Шесть силовых дней и один восстановительный: без отдыха мышцы не успевают восстановиться."
    }
}
