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
    private const val CHOOSE_WEIGHT = "Вес подбери так, чтобы последний повтор давался с запасом. " +
        "После записи в Дневник приложение посчитает вес само"

    private val dayA =
        listOf(Exercise.Squat, Exercise.Bench, Exercise.BentOverRow, Exercise.Hyperextension, Exercise.Abs)
    private val dayB = listOf(Exercise.Deadlift, Exercise.OverheadPress, Exercise.PullUp, Exercise.Abs)

    /** Пока нет своего ПМ, вес вспомогательного движения считается от ПМ жима лёжа с этим коэффициентом. */
    private val fromBench = mapOf(Exercise.BentOverRow to 0.7, Exercise.OverheadPress to 0.6)

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
            items = (if (second) dayB else dayA).map { item(it, scheme) { lift -> data.currentMax(lift, today) } },
            note = "Тренировки А и Б чередуются. Программу по микроциклам можно выбрать в профиле.",
        )
    }

    private fun item(exercise: Exercise, scheme: Scheme, maxOf: (Exercise) -> Double?): PlanItem {
        val sets = when {
            exercise == Exercise.PullUp -> PlannedSets(scheme.sets, "мах", null)
            exercise.bodyweight -> PlannedSets(CORE_SETS, CORE_REPS, null)
            else -> {
                val reps = if (exercise == Exercise.Deadlift) minOf(scheme.reps, DEADLIFT_MAX_REPS) else scheme.reps
                PlannedSets(scheme.sets, reps.toString(), weight(exercise, scheme.percent, maxOf))
            }
        }
        val note = when {
            exercise == Exercise.PullUp -> "Оставляй 1–2 повтора в запасе"
            exercise.bodyweight -> null
            sets.weightKg == null -> CHOOSE_WEIGHT
            else -> "${scheme.percent} % от ПМ"
        }
        return PlanItem(PlanPart.Work, exercise.title, exercise, listOf(sets), listOfNotNull(note))
    }

    private fun weight(exercise: Exercise, percent: Int, maxOf: (Exercise) -> Double?): Double? {
        val own = maxOf(exercise)
        val borrowed = fromBench[exercise]?.let { share -> maxOf(Exercise.Bench)?.times(share) }
        return (own ?: borrowed)?.let { Strength.percentOf(it, percent) }
    }
}
