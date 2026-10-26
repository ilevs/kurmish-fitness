package com.kurmish.fitness.core

import kotlin.math.roundToInt

/**
 * Показатели нагрузки тренировки, как в тренировочном дневнике.
 *
 * @property tonnageKg тоннаж: сумма вес × повторы × подходы (для собственного веса вес считается за 1).
 * @property lifts КПШ — количество подъёмов штанги, то есть всех повторов.
 * @property averageKg средний вес: тоннаж / КПШ.
 * @property relativeIntensity относительная интенсивность: средний % от ПМ, взвешенный по повторам,
 *   по упражнениям с известным ПМ; null, если ни для одного упражнения ПМ нет.
 * @property weightedIntensity интенсивность с весовыми коэффициентами: тоннаж каждого подхода,
 *   умноженный на коэффициент его зоны % от ПМ и на коэффициент воздействия упражнения.
 * @property minutes оценка времени тренировки в минутах, включая 30 минут на разминку и заминку.
 */
data class LoadMetrics(
    val tonnageKg: Double,
    val lifts: Int,
    val averageKg: Double,
    val relativeIntensity: Double?,
    val weightedIntensity: Double,
    val minutes: Int,
)

/** Считает [LoadMetrics] по формулам тренировочного дневника. */
object LoadCalculator {
    private const val BASE_MINUTES = 30
    private const val NO_ZONE = 0.30
    private const val PERCENT = 100

    /** Коэффициенты зон для 30–49 %: каждые 5 % свой коэффициент. */
    private val lowZones = listOf(0.35 to 0.18, 0.40 to 0.28, 0.45 to 0.38, 0.50 to 0.47)

    /** Коэффициенты для 50–100 % с шагом 1 %. */
    private val highZones = listOf(
        0.50, 0.51, 0.53, 0.54, 0.56, 0.58, 0.60, 0.62, 0.65, 0.68,
        0.70, 0.71, 0.73, 0.75, 0.78, 0.80, 0.82, 0.84, 0.87, 0.89,
        0.90, 0.95, 1.00, 1.07, 1.14, 1.20, 1.27, 1.34, 1.40, 1.47,
        1.53, 1.60, 1.67, 1.74, 1.82, 1.90, 2.05, 2.20, 2.40, 2.60,
        2.80, 3.00, 3.20, 3.60, 4.00, 4.50, 5.00, 7.50, 12.0, 18.0,
        25.0,
    )

    /** Минуты на подход в зависимости от % ПМ: до 45, 55, 65, 75, 85 % и выше. */
    private val minuteZones = listOf(0.45 to 0.5, 0.55 to 0.8, 0.65 to 0.9, 0.75 to 1.0, 0.85 to 1.1)
    private const val TOP_MINUTE_FACTOR = 1.2
    private const val HEAVY_FROM = 0.8
    private const val LIGHT_UP_TO = 0.4
    private const val HEAVY_MINUTES = 6.0
    private const val MEDIUM_MINUTES = 3.0
    private const val LIGHT_MINUTES = 1.5

    /** Показатели тренировки; [maxOf] даёт ПМ упражнения или null. */
    fun session(session: WorkoutSession, maxOf: (Exercise) -> Double?): LoadMetrics {
        var tonnage = 0.0
        var lifts = 0
        var weighted = 0.0
        var minutes = 0.0
        var percentReps = 0.0
        var repsWithMax = 0
        session.entries.forEach { entry ->
            val factor = entry.exercise?.loadFactor ?: Exercise.COMPOUND
            val max = entry.exercise?.let(maxOf)
            entry.sets.forEach { set ->
                val reps = set.reps * set.sets
                val weight = set.weightKg ?: 1.0
                val share = if (max != null && set.weightKg != null) share(set.weightKg, max) else 0.0
                tonnage += weight * reps
                lifts += reps
                weighted += weight * reps * zoneFactor(share) * factor
                minutes += set.sets * minuteFactor(share) * minutesPerSet(factor)
                if (max != null && set.weightKg != null) {
                    percentReps += share * reps
                    repsWithMax += reps
                }
            }
        }
        return LoadMetrics(
            tonnageKg = tonnage,
            lifts = lifts,
            averageKg = if (lifts == 0) 0.0 else tonnage / lifts,
            relativeIntensity = if (repsWithMax == 0) null else percentReps / repsWithMax,
            weightedIntensity = weighted,
            minutes = if (lifts == 0) 0 else (minutes + BASE_MINUTES).roundToInt(),
        )
    }

    /** Доля от ПМ, округлённая до процента, как в дневнике. */
    fun share(weightKg: Double, maxKg: Double): Double = (weightKg / maxKg * PERCENT).roundToInt() / PERCENT.toDouble()

    /** Коэффициент зоны интенсивности: ниже 30 % подход не учитывается. */
    fun zoneFactor(share: Double): Double {
        val percent = (share * PERCENT).roundToInt()
        return when {
            share < NO_ZONE -> 0.0
            percent < PERCENT / 2 -> lowZones.first { share < it.first }.second
            else -> highZones[(percent - PERCENT / 2).coerceAtMost(highZones.lastIndex)]
        }
    }

    private fun minuteFactor(share: Double): Double =
        minuteZones.firstOrNull { share < it.first }?.second ?: TOP_MINUTE_FACTOR

    private fun minutesPerSet(factor: Double): Double = when {
        factor >= HEAVY_FROM -> HEAVY_MINUTES
        factor <= LIGHT_UP_TO -> LIGHT_MINUTES
        else -> MEDIUM_MINUTES
    }
}
