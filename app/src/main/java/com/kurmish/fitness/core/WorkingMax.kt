package com.kurmish.fitness.core

/**
 * ПМ, от которого считаются рабочие веса.
 *
 * @property kg вес.
 * @property from упражнение, через ПМ которого оценён этот; null — свой ПМ упражнения.
 * @property fromBodyweight ПМ оценён по собственному весу, потому что других данных нет.
 * @property bodyweightKg собственный вес, если это ПМ полной нагрузки в подтягиваниях или брусьях
 *   (свой вес плюс отягощение): тогда вес на снаряде — отягощение или противовес, см. [BodyweightLoad].
 */
data class WorkingMax(
    val kg: Double,
    val from: Exercise? = null,
    val fromBodyweight: Boolean = false,
    val bodyweightKg: Double? = null,
) {
    /** Оценка, а не свой ПМ упражнения. */
    val estimated: Boolean
        get() = from != null || fromBodyweight
}

/**
 * Примерные соотношения силы между упражнениями.
 *
 * Пока у упражнения нет своего ПМ (ни в профиле, ни по Дневнику), он оценивается по ПМ близкого
 * упражнения. Если ПМ нет ни у одного близкого упражнения, стартовый ПМ базовых движений считается
 * от собственного веса как у новичка. Коэффициенты взяты с запасом в лёгкую сторону: первая
 * тренировка покажет точнее, а после записи в Дневник вес посчитается уже по своему ПМ. Для гантелей
 * коэффициент даёт вес одной гантели, для тренажёров — примерный вес, который придётся подстроить.
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
        Exercise.DumbbellPress to listOf(Exercise.Bench to 0.3),
        Exercise.MachineChestPress to listOf(Exercise.Bench to 0.8),
        Exercise.DumbbellShoulderPress to listOf(Exercise.OverheadPress to 0.35, Exercise.Bench to 0.2),
        Exercise.LateralRaise to listOf(Exercise.Bench to 0.1),
        Exercise.DumbbellRow to listOf(Exercise.Bench to 0.35, Exercise.Deadlift to 0.2),
        Exercise.SeatedCableRow to listOf(Exercise.Bench to 0.7, Exercise.Deadlift to 0.4),
        Exercise.LatPulldown to listOf(Exercise.Bench to 0.7),
        Exercise.Pullover to listOf(Exercise.Bench to 0.25),
        Exercise.FacePull to listOf(Exercise.Bench to 0.25),
        Exercise.ReverseFly to listOf(Exercise.Bench to 0.08),
        Exercise.LegPress to listOf(Exercise.Squat to 1.5),
        Exercise.HackSquat to listOf(Exercise.Squat to 0.8),
        Exercise.GobletSquat to listOf(Exercise.Squat to 0.3),
        Exercise.LegExtension to listOf(Exercise.Squat to 0.35),
        Exercise.LegCurl to listOf(Exercise.Deadlift to 0.25, Exercise.Squat to 0.3),
        Exercise.StandingCalfRaise to listOf(Exercise.Squat to 0.8),
        Exercise.SeatedCalfRaise to listOf(Exercise.Squat to 0.4),
    )

    /** Стартовый ПМ базовых движений новичка в долях собственного веса. */
    private val novice: Map<Exercise, Double> = mapOf(
        Exercise.Squat to 0.8,
        Exercise.Bench to 0.6,
        Exercise.Deadlift to 1.0,
        Exercise.OverheadPress to 0.4,
    )

    /**
     * ПМ [exercise] по ПМ близких упражнений [ownMax], а без них по собственному весу [bodyweightKg].
     *
     * @return оценка; null, если оценить не по чему.
     */
    fun estimate(exercise: Exercise, ownMax: (Exercise) -> Double?, bodyweightKg: Double? = null): WorkingMax? {
        val start = { lift: Exercise -> bodyweightKg?.let { weight -> novice[lift]?.times(weight) } }
        return byRatio(exercise, ownMax)
            ?: (start(exercise)?.let { WorkingMax(it) } ?: byRatio(exercise, start))?.copy(fromBodyweight = true)
    }

    private fun byRatio(exercise: Exercise, maxOf: (Exercise) -> Double?): WorkingMax? =
        ratios[exercise].orEmpty().firstNotNullOfOrNull { (basis, share) ->
            maxOf(basis)?.let { WorkingMax(it * share, basis) }
        }
}

/** Вес и пояснение к нему, когда своего ПМ под рукой нет. */
internal object WeightAdvice {
    private const val MAX_REPS = "мах"

    /** На сколько повторов подбирать вес, когда программа просит максимум повторов с отягощением. */
    private const val UNTIL_FAILURE_REPS = 10

    /** Пояснение к весам в процентах [percents] от ПМ [max] упражнения [exercise]. */
    fun percentNote(percents: String, max: WorkingMax?, exercise: Exercise? = null): String = when {
        max == null && exercise?.let(BodyweightLoad::base) != null ->
            "$percents от ПМ полной нагрузки: укажи свой вес в профиле и запиши подход в Дневник"
        max == null -> "$percents от ПМ: введи ПМ в профиле"
        max.bodyweightKg != null -> "$percents от ПМ ${max.kg.kg()}: свой вес плюс отягощение"
        !max.estimated -> "$percents от ПМ"
        else -> "$percents от ПМ ≈ ${max.kg.kg()}: ${source(max)}"
    }

    /**
     * Вес на [reps] повторов, когда программа не задаёт процент: от своего ПМ с запасом, как в последней
     * записи Дневника или от ПМ близкого упражнения.
     *
     * @return вес и пояснение; null, если упражнение со своим весом или подобрать не по чему.
     */
    fun byReps(exercise: Exercise, reps: String, max: WorkingMax?, last: SetGroup?): Pair<Double?, String>? {
        val count = if (reps == MAX_REPS) UNTIL_FAILURE_REPS else reps.substringBefore('-').toIntOrNull()
        val reserve = "с запасом ${Strength.RESERVE} повтора"
        val guess: Pair<Double?, String>? = when {
            count == null -> null
            max?.bodyweightKg != null -> byBodyweight(exercise, reps, count, max)
            exercise.bodyweight -> null
            max != null && !max.estimated ->
                Strength.forReps(max.kg, count, exercise.barbell) to "Вес от ПМ ${max.kg.kg()} $reserve"
            last?.weightKg != null ->
                last.weightKg to "Вес из последней записи в Дневнике: ${last.reps} × ${last.weightKg.kg()}"
            max != null -> Strength.forReps(max.kg, count, exercise.barbell) to "Вес $reserve, ${source(max)}"
            else -> null
        }
        return guess?.let { (kg, note) -> kg to if (exercise.dumbbell) "$note. Вес одной гантели" else note }
    }

    /** Отягощение или противовес на [count] повторов; «мах» своим весом — просто до отказа, без веса. */
    private fun byBodyweight(exercise: Exercise, reps: String, count: Int, max: WorkingMax): Pair<Double?, String>? {
        val body = max.bodyweightKg ?: return null
        return BodyweightLoad.onMachine(exercise, Strength.loadForReps(max.kg, count), body)
            .takeUnless { exercise.bodyweight && reps == MAX_REPS }
    }

    private fun source(max: WorkingMax): String = when {
        max.fromBodyweight -> "ПМ оценён по собственному весу как у новичка"
        else -> "ПМ оценён по упражнению «${max.from?.title}»"
    } + ", после записи в Дневник вес уточнится"
}
