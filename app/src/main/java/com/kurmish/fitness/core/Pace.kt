package com.kurmish.fitness.core

/**
 * Темп и отдых между подходами для рабочего упражнения.
 *
 * Темп записывается четырьмя цифрами: секунды на опускание, паузу внизу, подъём и паузу вверху; X — взрывной
 * подъём. Обычный темп 2010. Отдых зависит от числа повторов: чем тяжелее подход, тем дольше.
 */
internal object Pace {
    /** Пояснение к записи темпа для плана на день. */
    const val LEGEND = "Темп: секунды на опускание, паузу внизу, подъём и паузу вверху; X — взрывно."

    private const val HEAVY_REPS = 5
    private const val MEDIUM_REPS = 12
    private val number = Regex("""\d+""")
    private val accessories = setOf(Movement.Core, Movement.Rotation, Movement.Arms)

    /** «Темп 2010, отдых 2–3 мин» для [exercise] с повторами [reps] в периоде [period]. */
    fun note(exercise: Exercise, reps: String?, period: Period?): String =
        "Темп ${tempo(period)}, ${rest(exercise, reps, period)}"

    private fun tempo(period: Period?): String = when (period) {
        Period.Power -> "20X0"
        Period.Preparatory, Period.Hypertrophy -> "3010"
        else -> "2010"
    }

    private fun rest(exercise: Exercise, reps: String?, period: Period?): String {
        val count = reps?.let { number.find(it) }?.value?.toInt()
        val accessory = exercise.movement in accessories || exercise.loadFactor == Exercise.ISOLATION
        return when {
            period == Period.Metabolic -> "отдых 30–60 с"
            period == Period.Power -> "отдых 2–3 мин"
            accessory -> "отдых 60 с"
            count == null -> "отдых 1,5–2 мин"
            count <= HEAVY_REPS && exercise.isMainLift -> "отдых 3–5 мин"
            count <= HEAVY_REPS -> "отдых 2–3 мин"
            count <= MEDIUM_REPS -> "отдых 1,5–2 мин"
            else -> "отдых 60 с"
        }
    }
}
