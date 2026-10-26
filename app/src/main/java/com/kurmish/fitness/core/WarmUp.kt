package com.kurmish.fitness.core

/**
 * Подводящий подход.
 *
 * @property weightKg вес на штанге.
 * @property sets подходов.
 * @property reps повторов; бывает диапазоном, например «3-5».
 */
data class WarmUpSet(val weightKg: Double, val sets: Int, val reps: String) {
    /** «60 кг 1×3-5». */
    val label: String
        get() = "${weightKg.kg()} $sets×$reps"
}

/**
 * Специальная разминка: подводящие подходы к первому рабочему весу по таблицам для приседа,
 * становой тяги и жима лёжа. Веса — доли рабочего веса; «пустой гриф» значит 20 кг.
 */
object WarmUp {
    private const val BAR = 0.0

    /** Шаг таблицы: доля рабочего веса (0 — пустой гриф), подходы и повторы. */
    private class Step(val share: Double, val sets: Int, val reps: String)

    /** Таблица для рабочих весов до [upToKg] включительно. */
    private class Bracket(val upToKg: Double, val steps: List<Step>)

    private fun s(share: Double, sets: Int, reps: String) = Step(share, sets, reps)

    private val squat = listOf(
        Bracket(
            upToKg = 100.0,
            steps = listOf(
                s(BAR, 3, "5"),
                s(0.4, 1, "5"),
                s(0.55, 1, "3"),
                s(0.7, 1, "2"),
                s(0.8, 1, "1"),
                s(0.9, 1, "1")
            ),
        ),
        Bracket(
            upToKg = 150.0,
            steps = listOf(
                s(BAR, 3, "5"),
                s(0.333, 1, "5"),
                s(0.4666, 1, "5"),
                s(0.6, 1, "3"),
                s(0.736, 1, "2"),
                s(0.84, 1, "2"),
                s(0.934, 1, "1"),
            ),
        ),
        Bracket(
            upToKg = 200.0,
            steps = listOf(
                s(0.3, 1, "5"),
                s(0.5, 1, "3-5"),
                s(0.6, 1, "2"),
                s(0.7, 1, "2"),
                s(0.8, 1, "2"),
                s(0.9, 1, "1"),
                s(0.95, 1, "1"),
            ),
        ),
        Bracket(
            upToKg = 250.0,
            steps = listOf(
                s(0.28, 1, "5"), s(0.4, 1, "3-5"), s(0.48, 1, "2"), s(0.56, 1, "2"), s(0.64, 1, "2"),
                s(0.72, 1, "2"), s(0.8, 1, "1"), s(0.88, 1, "1"), s(0.94, 1, "1"),
            ),
        ),
        Bracket(
            upToKg = Double.MAX_VALUE,
            steps = listOf(
                s(0.25, 1, "5"), s(0.35, 1, "5"), s(0.42, 1, "5"), s(0.5, 1, "3"), s(0.6, 1, "3"),
                s(0.667, 1, "2"), s(0.733, 1, "2"), s(0.8, 1, "2"), s(0.867, 1, "1"), s(0.935, 1, "1"),
            ),
        ),
    )

    private val deadlift = listOf(
        Bracket(
            upToKg = 100.0,
            steps = listOf(
                s(BAR, 1, "10"),
                s(0.4, 2, "5"),
                s(0.55, 2, "3"),
                s(0.62, 1, "1"),
                s(0.7, 1, "1"),
                s(0.85, 1, "1")
            ),
        ),
        Bracket(
            upToKg = 150.0,
            steps = listOf(
                s(BAR, 1, "10"),
                s(0.333, 2, "5"),
                s(0.4666, 2, "3"),
                s(0.6, 1, "2"),
                s(0.736, 1, "1"),
                s(0.8, 1, "1"),
                s(0.9, 1, "1"),
            ),
        ),
        Bracket(
            upToKg = 200.0,
            steps = listOf(
                s(0.3, 1, "8"),
                s(0.5, 2, "5"),
                s(0.6, 2, "3"),
                s(0.7, 1, "2"),
                s(0.8, 1, "1"),
                s(0.85, 1, "1"),
                s(0.9, 1, "1"),
            ),
        ),
        Bracket(
            upToKg = 250.0,
            steps = listOf(
                s(0.28, 1, "8"), s(0.4, 2, "5"), s(0.48, 2, "2"), s(0.56, 1, "2"), s(0.64, 1, "2"),
                s(0.72, 1, "1"), s(0.8, 1, "1"), s(0.85, 1, "1"), s(0.9, 1, "1"),
            ),
        ),
        Bracket(
            upToKg = Double.MAX_VALUE,
            steps = listOf(
                s(0.25, 1, "8"), s(0.35, 2, "5"), s(0.42, 2, "3"), s(0.5, 2, "2"), s(0.6, 1, "2"),
                s(0.667, 1, "1"), s(0.733, 1, "1"), s(0.8, 1, "1"), s(0.867, 1, "1"), s(0.935, 1, "1"),
            ),
        ),
    )

    private val bench = listOf(
        Bracket(
            upToKg = 100.0,
            steps = listOf(
                s(BAR, 1, "15"),
                s(0.4, 1, "8"),
                s(0.55, 1, "5"),
                s(0.7, 1, "3"),
                s(0.8, 1, "2"),
                s(0.9, 1, "1")
            ),
        ),
        Bracket(
            upToKg = 150.0,
            steps = listOf(
                s(BAR, 1, "15"),
                s(0.333, 1, "8"),
                s(0.4666, 2, "5"),
                s(0.6, 1, "5"),
                s(0.736, 1, "2"),
                s(0.84, 1, "2"),
                s(0.934, 1, "1"),
            ),
        ),
        Bracket(
            upToKg = 200.0,
            steps = listOf(
                s(BAR, 1, "15"),
                s(0.3, 1, "8"),
                s(0.45, 2, "5"),
                s(0.6, 1, "3"),
                s(0.7, 1, "2"),
                s(0.8, 1, "2"),
                s(0.9, 1, "1"),
                s(0.95, 1, "1"),
            ),
        ),
        Bracket(
            upToKg = Double.MAX_VALUE,
            steps = listOf(
                s(BAR, 1, "15"), s(0.28, 1, "5"), s(0.4, 1, "3-5"), s(0.48, 1, "2"), s(0.56, 1, "2"),
                s(0.64, 1, "2"), s(0.72, 1, "2"), s(0.8, 1, "1"), s(0.88, 1, "1"), s(0.94, 1, "1"),
            ),
        ),
    )

    private fun weightOf(step: Step, workingKg: Double): Double =
        if (step.share == BAR) Strength.EMPTY_BAR else Strength.roundToPlates(workingKg * step.share)

    /** Таблица для упражнения: присед и становая — свои, жимы штанги — по жиму лёжа, для остальных нет. */
    private fun tableFor(exercise: Exercise): List<Bracket>? = when (exercise) {
        Exercise.Squat -> squat
        Exercise.Deadlift -> deadlift
        Exercise.Bench, Exercise.MidGripBench, Exercise.CloseGripBench, Exercise.OverheadPress -> bench
        else -> null
    }

    /** Подводящие подходы к первому рабочему весу [workingKg]; пустой список для упражнений без таблицы. */
    fun sets(exercise: Exercise, workingKg: Double): List<WarmUpSet> {
        val table = tableFor(exercise)
        if (table == null || workingKg <= Strength.EMPTY_BAR) return emptyList()
        val bracket = table.first { workingKg <= it.upToKg }
        return bracket.steps
            .map { step -> WarmUpSet(weightOf(step, workingKg), step.sets, step.reps) }
            .filter { it.weightKg < workingKg }
            .distinctBy { it.weightKg }
    }
}
