package com.kurmish.fitness.core

/**
 * Итоги одной календарной недели (с понедельника).
 *
 * @property weekStart понедельник недели в днях с 1970-01-01.
 * @property sessions сколько было тренировок.
 * @property tonnageKg суммарный тоннаж.
 * @property lifts суммарное КПШ.
 * @property relativeIntensity средний % от ПМ, взвешенный по КПШ тренировок; null, если ПМ не было.
 * @property weightedIntensity сумма нагрузки с коэффициентами.
 */
data class WeekStats(
    val weekStart: Long,
    val sessions: Int,
    val tonnageKg: Double,
    val lifts: Int,
    val relativeIntensity: Double?,
    val weightedIntensity: Double,
)

/**
 * Точка графика ПМ.
 *
 * @property epochDay день тренировки.
 * @property kg лучшая за день оценка разового максимума.
 */
data class MaxPoint(val epochDay: Long, val kg: Double)

/**
 * Сводка за последние недели.
 *
 * @property sessions тренировок за период.
 * @property perWeek в среднем тренировок в неделю.
 * @property tonnageKg тоннаж за период.
 * @property daysSinceLast сколько дней прошло после последней тренировки; null, если тренировок не было.
 */
data class StatsSummary(val sessions: Int, val perWeek: Double, val tonnageKg: Double, val daysSinceLast: Long?)

/** Перекос по плоскостям, который стоит исправить. */
sealed interface BalanceIssue {
    /** Повторов в [more] заметно больше, чем в противоположном [less]. */
    data class Excess(val more: Movement, val less: Movement) : BalanceIssue

    /** Упражнений типа [movement] за период не было совсем. */
    data class Missing(val movement: Movement) : BalanceIssue
}

/** Статистика по Дневнику для графиков. Всё считается на устройстве. */
object Stats {
    /** Сколько недель показывают графики нагрузки. */
    const val WEEKS = 12

    /** За сколько дней считаются сводка и баланс движений. */
    const val RECENT_DAYS = 28

    private const val DAYS_IN_WEEK = 7
    private const val EXCESS = 1.5

    /** Икры почти не влияют на баланс плоскостей и не считаются в приседе. */
    private val calves = setOf(Exercise.StandingCalfRaise, Exercise.SeatedCalfRaise)

    /**
     * Противоположные типы движения: повторов в паре должно быть поровну. Перекос в сторону второго
     * (горизонтальных тяг, вертикальных жимов, таза) допустим и даже полезен, в сторону первого — нет.
     */
    private val opposites = listOf(
        Movement.HorizontalPush to Movement.HorizontalPull,
        Movement.VerticalPull to Movement.VerticalPush,
        Movement.Squat to Movement.Hinge,
    )
    private const val MONDAY_SHIFT = 3

    /** Понедельник недели, в которую входит день (1970-01-01 был четвергом). */
    fun weekStart(epochDay: Long): Long = epochDay - Math.floorMod(epochDay + MONDAY_SHIFT, DAYS_IN_WEEK)

    /** Последние [count] недель по порядку, включая текущую; недели без тренировок тоже есть, с нулями. */
    fun weeks(data: AppData, today: Long, count: Int = WEEKS): List<WeekStats> {
        val maxes = maxLookup(data, today)
        val current = weekStart(today)
        val byWeek = data.sessions.groupBy { weekStart(it.epochDay) }
        return (count - 1 downTo 0).map { back ->
            val start = current - back * DAYS_IN_WEEK
            val loads = byWeek[start].orEmpty().map { LoadCalculator.session(it, maxes) }
            val intensityLoads = loads.filter { it.relativeIntensity != null && it.lifts > 0 }
            val intensityLifts = intensityLoads.sumOf { it.lifts }
            WeekStats(
                weekStart = start,
                sessions = loads.size,
                tonnageKg = loads.sumOf { it.tonnageKg },
                lifts = loads.sumOf { it.lifts },
                relativeIntensity = if (intensityLifts == 0) {
                    null
                } else {
                    intensityLoads.sumOf { (it.relativeIntensity ?: 0.0) * it.lifts } / intensityLifts
                },
                weightedIntensity = loads.sumOf { it.weightedIntensity },
            )
        }
    }

    /** Лучшая оценка ПМ по каждому дню, когда упражнение делали с весом до 10 повторов. */
    fun maxHistory(data: AppData, exercise: Exercise): List<MaxPoint> = data.sessions
        .flatMap { session ->
            session.entries.filter { it.exercise == exercise && !BodyweightLoad.assisted(exercise) }.flatMap { entry ->
                entry.sets.mapNotNull { set ->
                    val weight = set.weightKg ?: return@mapNotNull null
                    Strength.estimateMax(weight, set.reps)?.let { MaxPoint(session.epochDay, it) }
                }
            }
        }
        .groupBy { it.epochDay }
        .map { (_, points) -> points.maxBy { it.kg } }
        .sortedBy { it.epochDay }

    /** Упражнения, для которых есть график ПМ: сначала соревновательные, потом по числу дней. */
    fun exercisesWithMaxes(data: AppData): List<Exercise> = Exercise.entries
        .map { it to maxHistory(data, it).size }
        .filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<Exercise, Int>> { it.first.isMainLift }.thenByDescending { it.second })
        .map { it.first }

    /** Повторы по типам движений за последние [days] дней; неузнанные упражнения не учитываются. */
    fun balance(data: AppData, today: Long, days: Int = RECENT_DAYS): Map<Movement, Int> {
        val reps = data.sessions
            .filter { today - it.epochDay in 0 until days }
            .flatMap { it.entries }
            .filter { it.exercise !in calves }
            .mapNotNull { entry ->
                entry.exercise?.let { it.movement to entry.sets.sumOf { set -> set.reps * set.sets } }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.sum() }
        return Movement.entries.associateWith { reps[it] ?: 0 }
    }

    /**
     * Что поправить в балансе [balance]: противоположные типы движения уравниваются по числу повторов,
     * вращение плеча наружу уравновешивает жимы и тяги, кор нужен всегда. Пусто, если всё в порядке.
     */
    fun balanceIssues(balance: Map<Movement, Int>): List<BalanceIssue> {
        fun reps(movement: Movement) = balance[movement] ?: 0
        if (balance.values.sum() == 0) return emptyList()
        val pairs = opposites.mapNotNull { (front, back) ->
            val a = reps(front)
            val b = reps(back)
            when {
                a > 0 && b == 0 -> BalanceIssue.Missing(back)
                b > 0 && a == 0 -> BalanceIssue.Missing(front)
                a > b * EXCESS -> BalanceIssue.Excess(front, back)
                else -> null
            }
        }
        val upper = Movement.entries.filter { it.isPush || it.isPull }.sumOf(::reps)
        val rotation = BalanceIssue.Missing(Movement.Rotation).takeIf { upper > 0 && reps(Movement.Rotation) == 0 }
        val core = BalanceIssue.Missing(Movement.Core).takeIf { reps(Movement.Core) == 0 }
        return pairs + listOfNotNull(rotation, core)
    }

    /** Сводка за последние [days] дней. */
    fun summary(data: AppData, today: Long, days: Int = RECENT_DAYS): StatsSummary {
        val maxes = maxLookup(data, today)
        val recent = data.sessions.filter { today - it.epochDay in 0 until days }
        val last = data.sessions.filter { it.epochDay <= today }.maxOfOrNull { it.epochDay }
        return StatsSummary(
            sessions = recent.size,
            perWeek = recent.size * DAYS_IN_WEEK / days.toDouble(),
            tonnageKg = recent.sumOf { LoadCalculator.session(it, maxes).tonnageKg },
            daysSinceLast = last?.let { today - it },
        )
    }

    /** ПМ каждого упражнения считается один раз, а не на каждый подход. */
    private fun maxLookup(data: AppData, today: Long): (Exercise) -> Double? {
        val maxes = Exercise.entries.associateWith { data.currentMax(it, today) }
        return { maxes[it] }
    }
}
