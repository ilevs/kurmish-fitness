package com.kurmish.fitness.core

import kotlin.math.roundToInt

/**
 * Подтягивания и брусья, где поднимается своё тело. Полная нагрузка в них — собственный вес плюс
 * отягощение, а в гравитроне — собственный вес минус противовес: чем больше противовес, тем легче.
 * ПМ таких движений ведётся по полной нагрузке, поэтому подходы своим весом, с поясом и в гравитроне
 * сравнимы между собой, а вес на снаряде — отягощение или противовес — считается от собственного веса.
 */
internal object BodyweightLoad {
    private const val STEP = 2.5
    private const val PERCENT = 100.0

    private val assisted = mapOf(
        Exercise.AssistedPullUp to Exercise.PullUp,
        Exercise.AssistedDips to Exercise.Dips,
    )
    private val own = setOf(Exercise.PullUp, Exercise.ChinUp, Exercise.Dips)

    /** Движение, по которому ведётся ПМ полной нагрузки; null — обычное упражнение со снарядом. */
    fun base(exercise: Exercise): Exercise? = assisted[exercise] ?: exercise.takeIf { it in own }

    /** Гравитрон: вес в записи — противовес, а не нагрузка. */
    fun assisted(exercise: Exercise): Boolean = exercise in assisted

    /** Полная нагрузка подхода: собственный вес [bodyweightKg] плюс отягощение или минус противовес [weightKg]. */
    fun effective(exercise: Exercise, weightKg: Double?, bodyweightKg: Double): Double {
        val extra = weightKg ?: 0.0
        return if (assisted(exercise)) bodyweightKg - extra else bodyweightKg + extra
    }

    /**
     * Вес на снаряде для полной нагрузки [targetKg].
     *
     * @return противовес гравитрона или отягощение, null — своим весом; и пояснение.
     */
    fun onMachine(exercise: Exercise, targetKg: Double, bodyweightKg: Double): Pair<Double?, String> {
        val target = "нагрузка ≈ ${targetKg.roundToInt()} кг при своём весе ${bodyweightKg.kg()}"
        val gap = steps(bodyweightKg - targetKg)
        return when {
            assisted(exercise) && gap > 0 -> gap to "Противовес ${gap.kg()}: $target. Чем больше противовес, тем легче"
            assisted(exercise) -> null to "Нагрузка не меньше своего веса: делай без гравитрона, $target"
            gap < 0 -> -gap to "Отягощение ${(-gap).kg()}: $target"
            gap > 0 -> null to "Своим весом тяжело: в гравитроне противовес ${gap.kg()}, $target"
            else -> null to "Своим весом, $target"
        }
    }

    /** Подходы по процентам [percents] от ПМ полной нагрузки [max]: противовес или отягощение на каждый. */
    fun byPercents(
        exercise: Exercise,
        percents: List<Int>,
        sets: Int,
        reps: String,
        max: WorkingMax,
    ): List<PlannedSets> {
        val body = max.bodyweightKg ?: return listOf(PlannedSets(sets, reps, null))
        val weights = percents.map { onMachine(exercise, max.kg * it / PERCENT, body).first }
        return when {
            weights.isEmpty() -> listOf(PlannedSets(sets, reps, null))
            weights.size == 1 -> listOf(PlannedSets(sets, reps, weights.single()))
            else -> weights.map { PlannedSets(1, reps, it) }
        }
    }

    /** Что значит вес в плане упражнения [exercise] при ПМ полной нагрузки [max]. */
    fun hint(exercise: Exercise, max: WorkingMax): String {
        val body = max.bodyweightKg?.let { " при своём весе ${it.kg()}" }.orEmpty()
        return if (assisted(exercise)) {
            "Вес — противовес гравитрона: чем больше, тем легче. ПМ полной нагрузки ${max.kg.kg()}$body"
        } else {
            "Вес — отягощение на поясе, без веса — своим весом. ПМ полной нагрузки ${max.kg.kg()}$body"
        }
    }

    private fun steps(kg: Double): Double = (kg / STEP).roundToInt() * STEP
}
