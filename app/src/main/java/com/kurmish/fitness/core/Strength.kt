package com.kurmish.fitness.core

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Расчёты весов: оценка максимума, проценты от ПМ, округление под блины. */
object Strength {
    /** Шаг округления: самые маленькие блины по 1,25 кг с каждой стороны. */
    const val PLATE_STEP = 2.5

    /** Вес пустого грифа. */
    const val EMPTY_BAR = 20.0

    /** Больше этого числа повторов оценка максимума слишком неточна. */
    const val MAX_REPS_FOR_ESTIMATE = 10

    private const val EPLEY_DIVISOR = 30.0
    private const val PERCENT = 100.0
    private const val WARM_UP_TOP = 0.9
    private const val WARM_UP_STEP_KG = 25.0
    private const val MAX_WARM_UP_STEPS = 5

    /** Оценивает разовый максимум по подходу (формула Эпли); null, если повторов слишком много. */
    fun estimateMax(weightKg: Double, reps: Int): Double? = when {
        reps < 1 || reps > MAX_REPS_FOR_ESTIMATE -> null
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / EPLEY_DIVISOR)
    }

    /** Округляет вес до шага блинов, но не легче пустого грифа. */
    fun roundToPlates(weightKg: Double): Double =
        maxOf(EMPTY_BAR, (weightKg / PLATE_STEP).roundToInt() * PLATE_STEP)

    /** Вес на заданный процент от ПМ, округлённый под блины. */
    fun percentOf(maxKg: Double, percent: Int): Double = roundToPlates(maxKg * percent / PERCENT)

    /** Процент от ПМ, который составляет вес. */
    fun percentOfMax(weightKg: Double, maxKg: Double): Int = (weightKg / maxKg * PERCENT).roundToInt()

    /**
     * Подводящие подходы к рабочему весу: от пустого грифа, равными шагами,
     * от [maxReps] повторов на грифе вниз, но не меньше [minReps], последний подход около 90 % рабочего.
     */
    fun warmUp(workingKg: Double, maxReps: Int, minReps: Int = 1): List<SetGroup> {
        val top = workingKg * WARM_UP_TOP
        if (top <= EMPTY_BAR) return listOf(SetGroup(EMPTY_BAR, maxReps))
        val steps = ceil((top - EMPTY_BAR) / WARM_UP_STEP_KG).toInt().coerceIn(1, MAX_WARM_UP_STEPS)
        val bar = SetGroup(EMPTY_BAR, maxReps)
        val climb = (1..steps).map { step ->
            val weight = roundToPlates(EMPTY_BAR + (top - EMPTY_BAR) * step / steps)
            val reps = (maxReps - step * maxReps / (steps + 1)).coerceIn(minReps, maxReps)
            SetGroup(weight, reps)
        }
        return (listOf(bar) + climb).distinctBy { it.weightKg }
    }
}
