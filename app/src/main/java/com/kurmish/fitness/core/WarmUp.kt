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
 * Разминка под первое упражнение тренировки.
 *
 * Общая часть зависит от типа движения: кардио только перед ногами, суставная разминка, круг лёгких
 * упражнений того же типа с вращением плеча наружу, противоположной тягой и кором, растяжка и мобильность.
 * Подводящие подходы к первому рабочему весу — по таблицам для приседа, становой тяги и жима лёжа,
 * для остальных упражнений шагами около 20 % рабочего веса. Веса — доли рабочего веса; «пустой гриф» значит 20 кг.
 */
object WarmUp {
    private const val BAR = 0.0

    /** Доли рабочего веса в подводящих подходах без таблицы: шаг около 20 %, последний на 90 %. */
    private val genericShares = listOf(0.2, 0.4, 0.6, 0.8)
    private const val LAST_SHARE = 0.9
    private const val LAST_REPS = "1-2"
    private const val MAX_WARM_UP_REPS = 8

    /** Что разминать, какой круг сделать и какую мобильность добавить перед типом движения. */
    private class Routine(val joints: String, val circle: String, val mobility: String)

    private val routines = mapOf(
        Movement.HorizontalPush to Routine(
            joints = "запястья, локти, плечи, грудной отдел",
            circle = "отжимания от простых к сложным, вращения плеча наружу, тяга блока к груди или подтягивания " +
                "на низкой перекладине, подъём корпуса и гиперэкстензия",
            mobility = "прогибы грудного отдела на валике из полотенца",
        ),
        Movement.VerticalPush to Routine(
            joints = "запястья, локти, плечи, грудной и поясничный отдел; колени и таз, если жим стоя",
            circle = "отжимания уголком или лёгкий жим гантелей вверх, вращения плеча наружу, вертикальная тяга, " +
                "боковая планка с опусканием таза",
            mobility = "плечи: стоя лицом к стене, рука вверх, наклоны корпуса к стене",
        ),
        Movement.HorizontalPull to Routine(
            joints = "весь позвоночник, запястья, локти, плечи, колени и таз",
            circle = "подтягивания на низкой перекладине, вращения плеча наружу лёжа на боку, румынская тяга " +
                "на одной ноге без веса, наклоны в сторону",
            mobility = "плечевой пояс: палка над головой широким хватом и назад",
        ),
        Movement.VerticalPull to Routine(
            joints = "запястья, локти, плечи, грудной отдел",
            circle = "подтягивания с опорой ногами или тяга резины из-за головы, лицевая тяга, подъём на бицепс " +
                "обратным хватом, скручивания с руками над головой",
            mobility = "плечи: стоя лицом к стене, рука вверх, наклоны корпуса к стене",
        ),
        Movement.Squat to Routine(
            joints = "поясничный и грудной отдел, колени, таз, голеностоп",
            circle = "приседания с руками перед грудью, сплит-приседы, мостик или тяга на одной ноге без веса, " +
                "боковая планка и гиперэкстензия, присед с палкой над головой",
            mobility = "голеностоп: из выпада колено вперёд, пятка на полу; длинный выпад 15–30 секунд",
        ),
        Movement.Hinge to Routine(
            joints = "поясничный и грудной отдел, таз, колени, голеностоп",
            circle = "мостик или тяга на одной ноге без веса, приседания без веса, боковая планка и гиперэкстензия, " +
                "приставные шаги с резиной",
            mobility = "таз: перекаты с ноги на ногу в широкой стойке; длинный выпад 15–30 секунд",
        ),
        Movement.Rotation to Routine(
            joints = "запястья, локти, плечи, колени, таз и весь позвоночник",
            circle = "приседания и мостик без веса, вращения плеча наружу с гантелью, тяга в наклоне к груди " +
                "с лёгким весом, гиперэкстензия",
            mobility = "локти и плечи: штанга на груди, поочерёдно выводить локти вперёд",
        ),
    )

    /** Ноги первым упражнением: перед ними 3–5 минут кардио. */
    private val legs = setOf(Movement.Squat, Movement.Hinge, Movement.Rotation)

    /** Шаги общей разминки перед первым упражнением [first]; null — первое упражнение не из базы. */
    fun routine(first: Exercise?): List<String> {
        val movement = first?.movement
        val routine = routines[movement]
        return listOfNotNull(
            "3–5 минут велотренажёра, степпера или ходьбы, не бега".takeIf { movement in legs },
            "Суставная разминка от мелких суставов к крупным" + routine?.let { ": ${it.joints}" }.orEmpty(),
            "Круг 2–3 раза по 6–8 повторов, без отказа: " +
                (routine?.circle ?: "приседания, отжимания, гиперэкстензия, скручивания"),
            "Активная растяжка мышц первого упражнения и 15–30 секунд пассивной растяжки противоположных",
            routine?.let { "Мобильность — ${it.mobility}" },
        )
    }

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

    /**
     * Подводящие подходы к первому рабочему весу [workingKg] на [workingReps] повторов. По таблице, если она есть,
     * иначе шагами около 20 % рабочего веса: повторов вдвое меньше, чем в рабочем подходе, но не больше 8,
     * к верху меньше, последний подход на 90 % на 1–2 повтора. Пусто, если разминаться подходами не с чем.
     */
    fun sets(exercise: Exercise, workingKg: Double, workingReps: Int? = null): List<WarmUpSet> {
        val table = tableFor(exercise) ?: return generic(exercise, workingKg, workingReps)
        val steps = if (workingKg <= Strength.EMPTY_BAR) emptyList() else table.first { workingKg <= it.upToKg }.steps
        return steps
            .map { step -> WarmUpSet(weightOf(step, workingKg), step.sets, step.reps) }
            .filter { it.weightKg < workingKg }
            .distinctBy { it.weightKg }
    }

    private fun generic(exercise: Exercise, workingKg: Double, workingReps: Int?): List<WarmUpSet> {
        if (exercise.bodyweight || BodyweightLoad.assisted(exercise)) return emptyList()
        val first = ((workingReps ?: MAX_WARM_UP_REPS) / 2).coerceIn(1, MAX_WARM_UP_REPS)
        val ramp = genericShares.mapIndexed { index, share ->
            WarmUpSet(Strength.round(workingKg * share, exercise.barbell), 1, "${maxOf(1, first - index / 2)}")
        }
        val last = WarmUpSet(Strength.round(workingKg * LAST_SHARE, exercise.barbell), 1, LAST_REPS)
        return (ramp + last).filter { it.weightKg < workingKg }.distinctBy { it.weightKg }
    }
}
