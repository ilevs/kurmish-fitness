package com.kurmish.fitness.core

/**
 * Правило саморегулирующейся волны для одного упражнения.
 *
 * Вес считается от ПМ на начало блока: в первом подходе [startPercent] плюс [incrementPercent] за каждый
 * микроцикл волны, следующие подходы тяжелее на [setStepPercent] (0 — все подходы с одним весом).
 * Повторов делается сколько выйдет: вес растёт, даже если план по повторам не выполнен.
 *
 * @property sets подходов.
 * @property reps плановое число повторов.
 * @property startPercent % ПМ в первом подходе первого микроцикла волны.
 * @property setStepPercent насколько % ПМ каждый следующий подход тяжелее предыдущего.
 * @property incrementPercent прибавка в % ПМ за микроцикл.
 * @property endReps волна кончается, когда в самом тяжёлом подходе вышло не больше стольких повторов.
 * @property worstSet считать по худшему подходу с самым тяжёлым весом (все подходы схемы должны выйти);
 *   иначе по лучшему.
 */
data class WaveRule(
    val sets: Int,
    val reps: Int,
    val startPercent: Double,
    val setStepPercent: Double,
    val incrementPercent: Double,
    val endReps: Int,
    val worstSet: Boolean = false,
) {
    /** % ПМ по подходам на шаге [step] волны (0 — первый микроцикл). */
    fun percents(step: Int): List<Double> =
        (0 until sets).map { startPercent + step * incrementPercent + it * setStepPercent }

    /** «4×10» или «3×2». */
    val scheme: String
        get() = "$sets×$reps"
}

/**
 * Фаза саморегулирующейся программы: две волны одной схемы.
 *
 * @property title схема фазы для заголовка, например «4×10».
 * @property days тренировки микроцикла по порядку: название дня и упражнения.
 * @property estimate сколько микроциклов фаза обычно длится, для примерного расписания.
 */
data class WavePhase(val title: String, val days: List<Pair<String, List<ProgramItem>>>, val estimate: Int) {
    /** Ведущее упражнение: первое волновое в первом дне. По нему решается, когда кончается волна. */
    val lead: ProgramItem
        get() = days.first().second.first { it.wave != null }
}

/**
 * Где атлет находится в саморегулирующейся программе.
 *
 * @property phase номер фазы, с 0; равен числу фаз, если программа пройдена.
 * @property wave номер волны в фазе, с 0.
 * @property step микроцикл волны, с 0: от него считается вес.
 * @property record лучший вес ведущего упражнения на конец волны в этой фазе; null, если волн ещё не было.
 * @property phaseStart номер записи с начала блока, с которой началась фаза: от ПМ на эту дату считаются веса.
 * @property length сколько тренировок заняла программа, если она пройдена; иначе null.
 */
data class WavePosition(
    val phase: Int,
    val wave: Int,
    val step: Int,
    val record: Double?,
    val phaseStart: Int,
    val length: Int?,
)

/**
 * Саморегулирующиеся линейные циклы: старт далеко от максимума, вес растёт каждый микроцикл, повторы — сколько
 * выйдет. Когда в самом тяжёлом подходе ведущего упражнения остаётся [WaveRule.endReps] повторов, волна кончена:
 * со следующего микроцикла все упражнения программы откатываются к весам второго микроцикла и идут снова,
 * задача — побить рекорд волны. Две волны на фазу, потом схема короче.
 */
object Waves {
    /** Волн в фазе. */
    const val PER_PHASE = 2

    private const val PERCENT = 100.0

    /**
     * Положение в программе [plan] для следующей тренировки после записей Дневника [history] с начала блока.
     *
     * @param days тренировок в микроцикле.
     * @param lead упражнение, которое атлет делает вместо ведущего пункта фазы; null — то, что в программе.
     */
    fun position(
        plan: List<WavePhase>,
        days: Int,
        history: List<WorkoutSession>,
        lead: (ProgramItem) -> Exercise? = { it.maxOf },
    ): WavePosition {
        var at = WavePosition(phase = 0, wave = 0, step = 0, record = null, phaseStart = 0, length = null)
        var ended = false
        for (index in 0..history.size) {
            if (index > 0 && index % days == 0) {
                at = nextMicrocycle(at, ended, index)
                ended = false
                if (at.phase == plan.size) return at.copy(length = index)
            }
            val item = plan[at.phase].lead
            val rule = item.wave
            val top = history.getOrNull(index)?.let { topSet(it, lead(item), rule?.worstSet == true) }
            if (rule != null && top != null && top.reps <= rule.endReps) {
                ended = true
                at = at.copy(record = maxOf(at.record ?: 0.0, top.weightKg ?: 0.0))
            }
        }
        return at
    }

    /** Положение с нового микроцикла, который начинается записью [index]; [ended] — прошлая волна кончилась. */
    private fun nextMicrocycle(at: WavePosition, ended: Boolean, index: Int): WavePosition = when {
        !ended -> at.copy(step = at.step + 1)
        at.wave + 1 < PER_PHASE -> at.copy(wave = at.wave + 1, step = 1)
        else -> WavePosition(at.phase + 1, wave = 0, step = 0, record = null, phaseStart = index, length = null)
    }

    /**
     * Самый тяжёлый подход [exercise] в записи: с этим весом лучшее число повторов или, если [worst], худшее;
     * null, если упражнения в записи нет.
     */
    internal fun topSet(session: WorkoutSession, exercise: Exercise?, worst: Boolean = false): SetGroup? {
        val sets = session.entries.filter { exercise != null && it.exercise == exercise }.flatMap { it.sets }
        val heaviest = sets.maxOfOrNull { it.weightKg ?: 0.0 } ?: return null
        val top = sets.filter { (it.weightKg ?: 0.0) == heaviest }
        return if (worst) top.minBy { it.reps } else top.maxBy { it.reps }
    }

    /** Подходы по правилу [rule] на шаге [step] от ПМ [maxKg]; без ПМ — схема без веса. */
    fun sets(rule: WaveRule, step: Int, maxKg: Double?, barbell: Boolean): List<PlannedSets> {
        val reps = rule.reps.toString()
        if (maxKg == null) return listOf(PlannedSets(rule.sets, reps, null))
        val weights = rule.percents(step).map { Strength.round(maxKg * it / PERCENT, barbell) }
        return if (weights.distinct().size == 1) {
            listOf(PlannedSets(rule.sets, reps, weights.first()))
        } else {
            weights.map { PlannedSets(1, reps, it) }
        }
    }

    /** Программа для списка и расписания: фазы подряд, каждая на примерное число микроциклов. */
    fun program(id: String, title: String, note: String, goal: Goal, phases: List<WavePhase>): TrainingProgram {
        val starts = phases.runningFold(0) { total, phase -> total + phase.estimate }
        return TrainingProgram(
            id = id,
            title = title,
            note = note,
            daysPerWeek = phases.first().days.size,
            goal = goal,
            sessions = phases.flatMapIndexed { index, phase ->
                (1..phase.estimate).flatMap { week ->
                    phase.days.map { (day, items) -> ProgramSession(day, starts[index] + week, items) }
                }
            },
            waves = phases,
        )
    }
}
