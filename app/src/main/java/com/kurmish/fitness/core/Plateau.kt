package com.kurmish.fitness.core

/**
 * Застой в упражнениях с весом: оценка ПМ за последние 4 недели не выше, чем за 8 недель до них.
 * Первое средство от застоя — частота: движение или его вариацию чаще в неделю, а не больше объёма за раз.
 */
internal object Plateau {
    private const val WINDOW = 28L
    private const val BEFORE_WINDOWS = 3
    private const val MIN_DAYS = 3

    /** Советы по упражнениям, которые стоят на месте: сначала присед, жим и становая. */
    fun lines(data: AppData, today: Long): List<String> = lifts(data).mapNotNull { lift ->
        val recentFrom = today - WINDOW + 1
        val recent = estimates(data, lift, recentFrom..today)
        val before = estimates(data, lift, (today - BEFORE_WINDOWS * WINDOW + 1) until recentFrom)
        val stuck = recent.size >= MIN_DAYS && before.isNotEmpty() && recent.max() <= before.max()
        if (stuck) {
            "«${lift.title}» 4 недели без роста: оценка ПМ не выше, чем раньше. Сначала повысь частоту — " +
                "ещё одна тренировка этого движения или его вариации в неделю, а не больше подходов за раз."
        } else {
            null
        }
    }

    /** Упражнения, которые делали с весом; у гравитрона вес помогает, и оценка ПМ к нему не подходит. */
    private fun lifts(data: AppData): List<Exercise> = data.sessions
        .flatMap { it.entries }
        .filter { entry -> entry.sets.any { it.weightKg != null } }
        .mapNotNull { it.exercise?.takeUnless(BodyweightLoad::assisted) }
        .distinct()
        .sortedByDescending { it.isMainLift }

    /** Лучшая оценка ПМ [lift] по каждому дню из [days]. */
    private fun estimates(data: AppData, lift: Exercise, days: LongRange): List<Double> = data.sessions
        .filter { it.epochDay in days }
        .mapNotNull { session ->
            session.entries
                .filter { it.exercise == lift }
                .flatMap { it.sets }
                .mapNotNull { set -> set.weightKg?.let { Strength.estimateMax(it, set.reps) } }
                .maxOrNull()
        }
}
