package com.kurmish.fitness.core

import kotlin.math.roundToInt

/** Расчёты весов: оценка максимума, проценты от ПМ, округление под блины. */
object Strength {
    /** Шаг округления: самые маленькие блины по 1,25 кг с каждой стороны. */
    const val PLATE_STEP = 2.5

    /** Вес пустого грифа. */
    const val EMPTY_BAR = 20.0

    /** Больше этого числа повторов оценка максимума слишком неточна. */
    const val MAX_REPS_FOR_ESTIMATE = 10

    /** Сколько повторов оставлять в запасе, когда вес подбирается по числу повторов. */
    const val RESERVE = 2

    private const val EPLEY_DIVISOR = 30.0
    private const val PERCENT = 100.0

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

    /** Вес на [reps] повторов с запасом [reserve] повторов до отказа (формула Эпли наоборот). */
    fun forReps(maxKg: Double, reps: Int, reserve: Int = RESERVE): Double =
        roundToPlates(maxKg / (1 + (reps + reserve) / EPLEY_DIVISOR))

    /** Процент от ПМ, который составляет вес. */
    fun percentOfMax(weightKg: Double, maxKg: Double): Int = (weightKg / maxKg * PERCENT).roundToInt()
}
