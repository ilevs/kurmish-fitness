package com.kurmish.fitness.core

/**
 * Общая тренировка, когда программа не выбрана.
 *
 * Как разложить тренировки по неделе, решает [WeekSplit] по числу тренировок в профиле: при 1–3 тренировки
 * А и Б на всё тело по очереди, при 4–7 верх и низ, лёгкие дни и восстановительный день. В тренировке
 * на всё тело по одному упражнению каждого типа: жим, тяга, колено, таз, вращение плеча наружу и кор.
 * Подходы, повторы и процент от ПМ основных упражнений зависят от цели в профиле, а если включён годовой
 * план, — от текущего периода. Раз в две недели порядок упражнений меняется: первым идёт другое движение,
 * и привыкание к одной последовательности не тормозит прогресс.
 */
internal object BasicTemplate {
    /**
     * Схема рабочих подходов.
     *
     * @property sets подходов.
     * @property reps повторов в подходе.
     * @property percent процент от ПМ.
     */
    private class Scheme(val sets: Int, val reps: Int, val percent: Int)

    private const val CORE_SETS = 3
    private const val CORE_REPS = "15"
    private const val DEADLIFT_MAX_REPS = 6
    private const val MAX_REPS = "мах"
    private const val CHOOSE_WEIGHT = "Вес подбери так, чтобы последний повтор давался с запасом. " +
        "После записи в Дневник приложение посчитает вес само"

    private const val ROTATED = 4
    private const val SECOND_SETS = 3
    private const val SECOND_REPS = "10"
    private const val LIGHT_SETS = 3
    private const val LIGHT_REPS = 12
    private const val LIGHT_PERCENT = 55
    private const val RECOVERY_SETS = 2
    private const val RECOVERY_REPS = "15"
    private const val RECOVERY_NOTE = "Легко, без отказа. После каждого подхода 30 с растяжки и 30 с МФР этой зоны"

    /** Упражнения на одну ногу или руку: повторы на каждую сторону. */
    private val oneSide = setOf(
        Exercise.SplitSquat,
        Exercise.Lunge,
        Exercise.SingleLegDeadlift,
        Exercise.PistolSquat,
        Exercise.DumbbellRow,
        Exercise.SidePlank,
    )

    /** Лёгкий день: не тяжелее 3 × 12 на 55 %, а если схема периода уже легче, то она. */
    private fun Scheme.light(): Scheme =
        if (percent <= LIGHT_PERCENT) this else Scheme(LIGHT_SETS, maxOf(reps, LIGHT_REPS), LIGHT_PERCENT)

    /** Как выполнять, если по названию неочевидно. */
    private val hints = mapOf(Exercise.SidePlank to "Опускай таз к полу и поднимай обратно")

    /** Типы движения, где хватает лёгкого веса и 3 × 15. */
    private val accessory = setOf(Movement.Core, Movement.Rotation, Movement.Arms)

    private fun schemeFor(period: Period): Scheme = when (period) {
        Period.Preparatory -> Scheme(sets = 3, reps = 12, percent = 55)
        Period.Hypertrophy -> Scheme(sets = 4, reps = 10, percent = 65)
        Period.Deload -> Scheme(sets = 2, reps = 8, percent = 50)
        Period.Strength -> Scheme(sets = 5, reps = 4, percent = 82)
        Period.Power -> Scheme(sets = 5, reps = 3, percent = 60)
        Period.Metabolic -> Scheme(sets = 3, reps = 15, percent = 50)
        Period.Recovery -> Scheme(sets = 2, reps = 12, percent = 50)
    }

    private fun schemeFor(goal: Goal): Scheme = when (goal) {
        Goal.Strength -> Scheme(sets = 5, reps = 5, percent = 80)
        Goal.Endurance -> Scheme(sets = 3, reps = 12, percent = 60)
        Goal.Peak -> Scheme(sets = 3, reps = 3, percent = 85)
        Goal.Mass -> Scheme(sets = 4, reps = 8, percent = 70)
        Goal.Mixed -> Scheme(sets = 4, reps = 6, percent = 75)
    }

    /** План общей тренировки; [done] — сколько тренировок уже записано до сегодня. */
    fun plan(data: AppData, today: Long, done: Int): DayPlan {
        val profile = data.profile
        val year = YearPlan.position(profile.yearPlanStartEpochDay, today)
        val base = year?.let { schemeFor(it.period) } ?: schemeFor(profile.goal)
        val split = WeekSplit.day(profile.daysPerWeek, done)
        val scheme = if (split.load == DayLoad.Light) base.light() else base
        val rotated = done / (profile.daysPerWeek * 2) % 2 == 1
        val slots = if (rotated && split.load != DayLoad.Recovery) split.slots.rotated() else split.slots
        val stretch = profile.shoulders.chest?.weak == true
        val task = year?.label ?: "цель «${profile.goal.title}»"
        return DayPlan(
            title = "${split.title}, $task",
            items = slots.map { slot ->
                val chosen = profile.pool[slot.exercise.title] ?: slot.exercise
                val max = data.workingMax(chosen, today)
                val last = data.lastSet(chosen, today)
                val item = slotItem(chosen, slot, split.load, scheme, max, last)
                val pull = chosen.movement == Movement.HorizontalPull || chosen.movement == Movement.VerticalPull
                val notes = if (stretch && pull) item.notes + FitnessTest.STRETCH else item.notes
                item.copy(slot = slot.exercise.title, notes = notes)
            },
            note = listOfNotNull(
                year?.let { "${it.period.task}." },
                WeekSplit.note(profile.daysPerWeek),
                "Порядок упражнений меняется раз в две недели.",
                "Программу по микроциклам можно выбрать в Профиле, в разделе «Циклы».",
            ).joinToString(" "),
        )
    }

    /** Другой порядок: первым идёт второе движение, третье и четвёртое меняются местами. */
    private fun List<SplitSlot>.rotated(): List<SplitSlot> =
        take(ROTATED).chunked(2).flatMap { it.reversed() } + drop(ROTATED)

    private fun heavy(exercise: Exercise, scheme: Scheme, max: WorkingMax?): PlannedSets {
        val reps = if (exercise == Exercise.Deadlift) minOf(scheme.reps, DEADLIFT_MAX_REPS) else scheme.reps
        return if (max?.bodyweightKg != null) {
            BodyweightLoad.byPercents(exercise, listOf(scheme.percent), scheme.sets, "$reps", max).single()
        } else {
            PlannedSets(scheme.sets, "$reps", max?.let { Strength.percentOf(it.kg, scheme.percent, exercise.barbell) })
        }
    }

    private fun item(exercise: Exercise, scheme: Scheme, max: WorkingMax?, last: SetGroup?): PlanItem {
        val light = exercise.movement in accessory || exercise.loadFactor == Exercise.ISOLATION
        val guess = if (light) WeightAdvice.byReps(exercise, CORE_REPS, max, last) else null
        val sets = when {
            light -> PlannedSets(CORE_SETS, CORE_REPS, guess?.first)
            exercise.bodyweight -> PlannedSets(scheme.sets, MAX_REPS, null)
            else -> heavy(exercise, scheme, max)
        }
        val note = when {
            sets.reps == MAX_REPS -> "Оставляй 1–2 повтора в запасе"
            exercise.bodyweight -> null
            light -> guess?.second ?: "Лёгкий вес, без отказа"
            max?.bodyweightKg != null -> BodyweightLoad.hint(exercise, max)
            sets.weightKg == null -> CHOOSE_WEIGHT
            else -> WeightAdvice.percentNote("${scheme.percent} %", max, exercise)
        }
        val side = "${sets.reps} повторов на каждую сторону".takeIf { exercise in oneSide }
        val notes = listOfNotNull(side, hints[exercise], note)
        return PlanItem(PlanPart.Work, exercise.title, exercise, listOf(sets), notes)
    }

    @Suppress("LongParameterList")
    private fun slotItem(
        exercise: Exercise,
        slot: SplitSlot,
        load: DayLoad,
        scheme: Scheme,
        max: WorkingMax?,
        last: SetGroup?,
    ): PlanItem = when {
        load == DayLoad.Recovery -> recoveryItem(exercise)
        slot.second -> secondItem(exercise, max, last)
        else -> item(exercise, scheme, max, last)
    }

    private fun recoveryItem(exercise: Exercise): PlanItem {
        val side = "$RECOVERY_REPS повторов на каждую сторону".takeIf { exercise in oneSide }
        val sets = PlannedSets(RECOVERY_SETS, RECOVERY_REPS, null)
        return PlanItem(PlanPart.Work, exercise.title, exercise, listOf(sets), listOfNotNull(side, RECOVERY_NOTE))
    }

    private fun secondItem(exercise: Exercise, max: WorkingMax?, last: SetGroup?): PlanItem {
        val guess = WeightAdvice.byReps(exercise, SECOND_REPS, max, last)
        val weight = guess?.first.takeUnless { exercise.bodyweight }
        val reps = if (exercise.bodyweight && exercise !in oneSide) MAX_REPS else SECOND_REPS
        val notes = listOfNotNull(
            "$reps повторов на каждую ${if (exercise.movement == Movement.Squat) "ногу" else "сторону"}"
                .takeIf { exercise in oneSide },
            "Оставляй 1–2 повтора в запасе".takeIf { reps == MAX_REPS },
            guess?.second ?: CHOOSE_WEIGHT.takeUnless { exercise.bodyweight },
        )
        val sets = PlannedSets(SECOND_SETS, reps, weight)
        return PlanItem(PlanPart.Work, exercise.title, exercise, listOf(sets), notes)
    }
}
