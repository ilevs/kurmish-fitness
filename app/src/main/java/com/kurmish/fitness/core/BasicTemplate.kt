package com.kurmish.fitness.core

/**
 * Общая тренировка на всё тело, когда программа не выбрана.
 *
 * Две тренировки чередуются через раз и уравновешивают друг друга по плоскостям: в А присед,
 * горизонтальный жим и горизонтальная тяга, в Б становая, вертикальный жим и вертикальная тяга.
 * Подходы, повторы и процент от ПМ зависят от цели в профиле.
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

    private val dayA =
        listOf(Exercise.Squat, Exercise.Bench, Exercise.BentOverRow, Exercise.Hyperextension, Exercise.Abs)
    private val dayB = listOf(Exercise.Deadlift, Exercise.OverheadPress, Exercise.PullUp, Exercise.Abs)

    /** Типы движения, где хватает лёгкого веса и 3 × 15. */
    private val accessory = setOf(Movement.Core, Movement.Rotation, Movement.Arms)

    private fun schemeFor(goal: Goal): Scheme = when (goal) {
        Goal.Strength -> Scheme(sets = 5, reps = 5, percent = 80)
        Goal.Endurance -> Scheme(sets = 3, reps = 12, percent = 60)
        Goal.Peak -> Scheme(sets = 3, reps = 3, percent = 85)
        Goal.Mass -> Scheme(sets = 4, reps = 8, percent = 70)
        Goal.Mixed -> Scheme(sets = 4, reps = 6, percent = 75)
    }

    /** План общей тренировки; [done] — сколько тренировок уже записано до сегодня. */
    fun plan(data: AppData, today: Long, done: Int): DayPlan {
        val goal = data.profile.goal
        val scheme = schemeFor(goal)
        val second = done % 2 == 1
        return DayPlan(
            title = "Общая тренировка ${if (second) "Б" else "А"}, цель «${goal.title}»",
            items = (if (second) dayB else dayA).map { base ->
                val chosen = data.profile.pool[base.title] ?: base
                val max = data.workingMax(chosen, today)
                item(chosen, scheme, max, data.lastSet(chosen, today)).copy(slot = base.title)
            },
            note = "Тренировки А и Б чередуются. Программу по микроциклам можно выбрать в профиле.",
        )
    }

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
        return PlanItem(PlanPart.Work, exercise.title, exercise, listOf(sets), listOfNotNull(note))
    }
}
