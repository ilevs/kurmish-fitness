package com.kurmish.fitness.core

/**
 * ПМ, от которого считаются рабочие веса.
 *
 * @property kg вес.
 * @property from упражнение, через ПМ которого оценён этот; null — свой ПМ упражнения.
 */
data class WorkingMax(val kg: Double, val from: Exercise? = null)

/**
 * Примерные соотношения силы между упражнениями.
 *
 * Пока у упражнения нет своего ПМ (ни в профиле, ни по Дневнику), он оценивается по ПМ близкого
 * упражнения. Коэффициенты взяты с запасом в лёгкую сторону: первая тренировка покажет точнее,
 * а после записи в Дневник вес посчитается уже по своему ПМ. Тренажёры и гантели сюда не входят:
 * вес в них слишком зависит от снаряда.
 */
internal object MaxRatios {
    /** Упражнение → по каким ПМ его можно оценить, в порядке предпочтения. */
    private val ratios: Map<Exercise, List<Pair<Exercise, Double>>> = mapOf(
        Exercise.Squat to listOf(Exercise.Deadlift to 0.85, Exercise.FrontSquat to 1.2),
        Exercise.Deadlift to listOf(Exercise.Squat to 1.15),
        Exercise.FrontSquat to listOf(Exercise.Squat to 0.8, Exercise.Deadlift to 0.65),
        Exercise.StiffLegDeadlift to listOf(Exercise.Deadlift to 0.7, Exercise.Squat to 0.75),
        Exercise.GoodMorning to listOf(Exercise.Squat to 0.4, Exercise.Deadlift to 0.35),
        Exercise.Bench to listOf(Exercise.MidGripBench to 1.0, Exercise.CloseGripBench to 1.1),
        Exercise.MidGripBench to listOf(Exercise.Bench to 0.95),
        Exercise.CloseGripBench to listOf(Exercise.Bench to 0.85),
        Exercise.InclineBench to listOf(Exercise.Bench to 0.75),
        Exercise.OverheadPress to listOf(Exercise.Bench to 0.6, Exercise.PushPress to 0.75),
        Exercise.PushPress to listOf(Exercise.OverheadPress to 1.2, Exercise.Bench to 0.75),
        Exercise.BentOverRow to listOf(Exercise.Bench to 0.7, Exercise.Deadlift to 0.4),
        Exercise.PowerClean to listOf(Exercise.Bench to 0.75, Exercise.Deadlift to 0.45),
        Exercise.BicepsCurl to listOf(Exercise.Bench to 0.35),
        Exercise.FrenchPress to listOf(Exercise.CloseGripBench to 0.35, Exercise.Bench to 0.3),
    )

    /** ПМ [exercise] по ПМ близких упражнений [ownMax]; null, если оценить не по чему. */
    fun estimate(exercise: Exercise, ownMax: (Exercise) -> Double?): WorkingMax? =
        ratios[exercise].orEmpty().firstNotNullOfOrNull { (basis, share) ->
            ownMax(basis)?.let { WorkingMax(it * share, basis) }
        }
}

/** Вес и пояснение к нему, когда своего ПМ под рукой нет. */
internal object WeightAdvice {
    /** Пояснение к весам в процентах [percents] от ПМ [max]. */
    fun percentNote(percents: String, max: WorkingMax?): String = when {
        max == null -> "$percents от ПМ: введи ПМ в профиле"
        max.from == null -> "$percents от ПМ"
        else -> "$percents от ПМ ≈ ${max.kg.kg()}: ${estimated(max.from)}"
    }

    /**
     * Вес на [reps] повторов, когда программа не задаёт процент: от своего ПМ с запасом, как в последней
     * записи Дневника или от ПМ близкого упражнения.
     *
     * @return вес и пояснение; null, если упражнение со своим весом или подобрать не по чему.
     */
    fun byReps(exercise: Exercise, reps: String, max: WorkingMax?, last: SetGroup?): Pair<Double, String>? {
        val count = reps.substringBefore('-').toIntOrNull()
        val reserve = "с запасом ${Strength.RESERVE} повтора"
        return when {
            exercise.bodyweight || count == null -> null
            max != null && max.from == null -> Strength.forReps(max.kg, count) to "Вес от ПМ ${max.kg.kg()} $reserve"
            last?.weightKg != null ->
                last.weightKg to "Вес из последней записи в Дневнике: ${last.reps} × ${last.weightKg.kg()}"
            max?.from != null -> Strength.forReps(max.kg, count) to "Вес $reserve, ${estimated(max.from)}"
            else -> null
        }
    }

    private fun estimated(from: Exercise): String =
        "ПМ оценён по упражнению «${from.title}», после записи в Дневник вес уточнится"
}
