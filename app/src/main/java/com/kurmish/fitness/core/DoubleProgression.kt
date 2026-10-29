package com.kurmish.fitness.core

/**
 * Двойная прогрессия для схем с диапазоном повторов, например «3×6-8».
 *
 * Вес держится, пока во всех подходах не взят верх диапазона. Как только в прошлой тренировке все рабочие
 * подходы набрали верх, вес растёт на шаг, а повторы начинаются снова с нижней границы.
 */
internal object DoubleProgression {
    private val range = Regex("""^(\d+)-(\d+)$""")

    /** Шаг одной гантели: у гантелей прибавка 2,5 кг слишком крупная. */
    private const val DUMBBELL_STEP = 1.0

    /**
     * Вес на сегодня по прошлой тренировке [previous].
     *
     * @param reps повторы из схемы программы, например «6-8».
     * @param sets сколько рабочих подходов в схеме.
     * @return вес и пояснение; null, если в схеме нет диапазона, упражнение со своим весом или прошлой записи нет.
     */
    fun next(exercise: Exercise, reps: String, sets: Int, previous: List<SetGroup>): Pair<Double, String>? {
        val top = range.find(reps)?.groupValues?.get(2)?.toIntOrNull()
        val weight = previous.mapNotNull { it.weightKg }.maxOrNull()
        if (top == null || weight == null || exercise.bodyweight) return null
        val working = previous.filter { it.weightKg == weight }
        val done = working.sumOf { it.sets }
        val reached = done >= sets && working.all { it.reps >= top }
        val step = if (exercise.dumbbell) DUMBBELL_STEP else Strength.PLATE_STEP
        val (kg, note) = if (reached) {
            weight + step to "В прошлый раз во всех подходах $top повторов с ${weight.kg()}: " +
                "вес +${step.kg()}, повторы снова с нижней границы"
        } else {
            weight to "Вес прошлой тренировки: когда во всех $sets подходах будет $top повторов, вес вырастет"
        }
        return kg to if (exercise.dumbbell) "$note. Вес одной гантели" else note
    }

    /** Вес схемы [scheme] без процентов: по двойной прогрессии, а без прошлой тренировки — по [WeightAdvice.byReps]. */
    fun orByReps(
        exercise: Exercise,
        scheme: PlannedSets,
        max: WorkingMax?,
        last: SetGroup?,
        previous: List<SetGroup>,
    ): Pair<Double?, String>? = next(exercise, scheme.reps, scheme.sets, previous)
        ?: WeightAdvice.byReps(exercise, scheme.reps, max, last)
}
