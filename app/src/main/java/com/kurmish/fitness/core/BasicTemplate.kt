package com.kurmish.fitness.core

/**
 * Общая тренировка на всё тело, когда программа не выбрана.
 *
 * В каждой тренировке по одному упражнению каждого типа: жим, тяга, колено, таз, вращение плеча наружу
 * и кор, всего шесть. Две тренировки чередуются через раз и уравновешивают друг друга по плоскостям:
 * в А присед, горизонтальный жим и горизонтальная тяга, в Б становая, вертикальный жим, вертикальная тяга
 * и сплит-присед. Подходы, повторы и процент от ПМ основных упражнений зависят от цели в профиле, а если
 * включён годовой план, — от текущего периода. Раз в две недели порядок упражнений меняется: первым идёт
 * другое движение, и привыкание к одной последовательности не тормозит прогресс.
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

    private val dayA = listOf(
        Exercise.Squat,
        Exercise.Bench,
        Exercise.BentOverRow,
        Exercise.Hyperextension,
        Exercise.FacePull,
        Exercise.Abs,
    )
    private val dayB = listOf(
        Exercise.Deadlift,
        Exercise.OverheadPress,
        Exercise.PullUp,
        Exercise.SplitSquat,
        Exercise.ExternalRotation,
        Exercise.SidePlank,
    )

    /** Второе упражнение на ноги в тренировке: 3 × 10 с запасом, а не схема основных. */
    private val secondLegs = setOf(Exercise.SplitSquat)

    /** Упражнения на одну ногу или руку: повторы на каждую сторону. */
    private val oneSide = setOf(
        Exercise.SplitSquat,
        Exercise.Lunge,
        Exercise.SingleLegDeadlift,
        Exercise.PistolSquat,
        Exercise.DumbbellRow,
        Exercise.SidePlank,
    )

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
        val scheme = year?.let { schemeFor(it.period) } ?: schemeFor(profile.goal)
        val second = done % 2 == 1
        val rotated = done / (profile.daysPerWeek * 2) % 2 == 1
        val day = (if (second) dayB else dayA).let { if (rotated) it.rotated() else it }
        val stretch = profile.shoulders.chest?.weak == true
        val task = year?.label ?: "цель «${profile.goal.title}»"
        return DayPlan(
            title = "Общая тренировка ${if (second) "Б" else "А"}, $task",
            items = day.map { base ->
                val chosen = profile.pool[base.title] ?: base
                val max = data.workingMax(chosen, today)
                val last = data.lastSet(chosen, today)
                val item = if (base in secondLegs) secondItem(chosen, max, last) else item(chosen, scheme, max, last)
                val pull = chosen.movement == Movement.HorizontalPull || chosen.movement == Movement.VerticalPull
                val notes = if (stretch && pull) item.notes + FitnessTest.STRETCH else item.notes
                item.copy(slot = base.title, notes = notes)
            },
            note = listOfNotNull(
                year?.let { "${it.period.task}." },
                "Тренировки А и Б чередуются, порядок упражнений меняется раз в две недели.",
                "Программу по микроциклам можно выбрать во вкладке «Циклы».",
            ).joinToString(" "),
        )
    }

    /** Другой порядок: первым идёт второе движение, третье и четвёртое меняются местами. */
    private fun List<Exercise>.rotated(): List<Exercise> =
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

    private fun secondItem(exercise: Exercise, max: WorkingMax?, last: SetGroup?): PlanItem {
        val guess = WeightAdvice.byReps(exercise, SECOND_REPS, max, last)
        val weight = guess?.first.takeUnless { exercise.bodyweight }
        val notes = listOfNotNull(
            "$SECOND_REPS повторов на каждую ногу".takeIf { exercise in oneSide },
            guess?.second ?: CHOOSE_WEIGHT.takeUnless { exercise.bodyweight },
        )
        val sets = PlannedSets(SECOND_SETS, SECOND_REPS, weight)
        return PlanItem(PlanPart.Work, exercise.title, exercise, listOf(sets), notes)
    }
}
