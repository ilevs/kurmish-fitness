package com.kurmish.fitness.core

/**
 * Встроенные саморегулирующиеся программы ([Waves]): длина волны подстраивается под атлета сама.
 *
 * Проценты — от разового ПМ на начало блока.
 */
internal object WavePrograms {
    private const val LADDER_STEP = 7.0
    private const val LADDER_INCREMENT = 3.5
    private const val LADDER_PHASE = 13
    private const val LINEAR_PHASE = 19

    private fun ladder(sets: Int, reps: Int, start: Double, end: Int) =
        WaveRule(sets, reps, start, LADDER_STEP, LADDER_INCREMENT, end)

    private fun main(exercise: Exercise, rule: WaveRule) =
        ProgramItem(exercise.title, rule.scheme, exercise, emptyList(), wave = rule)

    private fun extra(name: String, scheme: String) = ProgramItem(name, scheme, null, emptyList())

    private val pullDay = listOf(extra("Тяга в наклоне", "3×10"), extra("Подъём на бицепс", "2×15"))
    private val pressDay =
        listOf(extra("Подтягивания", "3×мах"), extra("Махи в стороны", "3×12"), extra("Пресс", "3×15"))
    private val benchDay = listOf(extra("Французский жим", "3×12"), extra("Лицевая тяга", "2×20-25"))

    private fun ladderPhase(rule: WaveRule, lifts: List<Pair<Exercise, Exercise>>) = WavePhase(
        title = rule.scheme,
        days = listOf(
            "Д1 · жим и тяга" to listOf(main(lifts[0].first, rule), main(lifts[0].second, rule)) + pullDay,
            "Д2 · жим стоя и присед" to listOf(main(lifts[1].first, rule), main(lifts[1].second, rule)) + pressDay,
            "Д3 · жим и присед" to listOf(main(lifts[2].first, rule), main(lifts[2].second, rule)) + benchDay,
        ),
        estimate = LADDER_PHASE,
    )

    /** Лестница 4×10 → 4×8 → 3×6: подходы с шагом 7 % ПМ, прибавка 3,5 % за микроцикл. */
    val ladder = Waves.program(
        id = "waves-ladder",
        title = "Волны: лестница",
        note = "Саморегулирующаяся: в каждом движении подходы лесенкой с шагом 7 % ПМ, каждый микроцикл " +
            "вес +3,5 % ПМ, повторов — сколько выйдет. Когда в последнем подходе жима в Д1 остаётся 2 повтора " +
            "(в фазе 3×6 — 1), волна кончена: все веса откатываются ко второму микроциклу, задача — побить " +
            "рекорд. Две волны на фазу: 4×10 в изменённых движениях → 4×8 → 3×6 в соревновательных. " +
            "Записывай каждый подход в Дневник: длина волн считается по нему. Подход до отказа — " +
            "разминайся тщательнее и держи технику.",
        goal = Goal.Strength,
        phases = listOf(
            ladderPhase(
                ladder(sets = 4, reps = 10, start = 40.0, end = 2),
                listOf(
                    Exercise.CloseGripBench to Exercise.DeficitDeadlift,
                    Exercise.OverheadPress to Exercise.FrontSquat,
                    Exercise.PauseBench to Exercise.BoxSquat,
                ),
            ),
            ladderPhase(
                ladder(sets = 4, reps = 8, start = 45.0, end = 2),
                listOf(
                    Exercise.MidGripBench to Exercise.BlockPull,
                    Exercise.SeatedBarbellPress to Exercise.PauseSquat,
                    Exercise.PauseBench to Exercise.Squat,
                ),
            ),
            ladderPhase(
                ladder(sets = 3, reps = 6, start = 55.0, end = 1),
                listOf(
                    Exercise.Bench to Exercise.Deadlift,
                    Exercise.PushPress to Exercise.FrontSquat,
                    Exercise.MidGripBench to Exercise.Squat,
                ),
            ),
        ),
    )

    private fun linearPhase(rule: WaveRule, light: String) = WavePhase(
        title = rule.scheme,
        days = listOf(
            "Д1 · жим и присед" to listOf(main(Exercise.Bench, rule), main(Exercise.Squat, rule)) +
                listOf(extra("Тяга в наклоне", "4×8"), extra("Лицевая тяга", "3×15-20")),
            "Д2 · тяга" to listOf(main(Exercise.Deadlift, rule)) +
                listOf(extra("Жим стоя", "3×8"), extra("Подтягивания", "4×мах"), extra("Пресс", "3×15")),
            "Д3 · лёгкий жим" to listOf(extra(Exercise.CloseGripBench.title, light), extra("Гиперэкстензия", "3×12")) +
                listOf(extra("Подъём на бицепс", "3×12"), extra("Обратные разводки", "3×15")),
        ),
        estimate = LINEAR_PHASE,
    )

    /** Линейная 5×5 → 5×3 → 3×2: все подходы одним весом, прибавка каждый микроцикл. */
    val linear = Waves.program(
        id = "waves-linear",
        title = "Волны: 5×5 → 5×3 → 3×2",
        note = "Саморегулирующаяся: все рабочие подходы одним весом, старт далеко от максимума, каждый " +
            "микроцикл вес растёт. Когда в жиме лёжа не выходят все повторы схемы, волна кончена: веса " +
            "откатываются ко второму микроциклу, задача — пройти дальше. Две волны на схему, потом схема короче. " +
            "В Д3 лёгкий жим узким хватом без нажима — он восстановительный.",
        goal = Goal.Strength,
        phases = listOf(
            linearPhase(WaveRule(5, 5, 47.0, 0.0, 4.0, endReps = 4, worstSet = true), light = "4×8"),
            linearPhase(WaveRule(5, 3, 55.0, 0.0, 4.0, endReps = 2, worstSet = true), light = "4×6"),
            linearPhase(WaveRule(3, 2, 70.0, 0.0, 3.0, endReps = 1, worstSet = true), light = "4×5"),
        ),
    )

    private val highReps = WaveRule(3, 15, 55.0, 0.0, 4.0, endReps = 2)

    /** 3×15 с прибавкой каждый микроцикл: повторы падают сами, конец — 2 повтора в первом подходе. */
    val highRep = Waves.program(
        id = "waves-15",
        title = "Волны: 3×15",
        note = "Саморегулирующаяся, на массу и силу: жим лёжа 3×15 с 55 % ПМ, каждый микроцикл +4 % ПМ, " +
            "повторы падают сами (15 → 12 → 8 → 4…). Когда в лучшем подходе жима остаётся 2 повтора, это " +
            "новый максимум на 2: веса откатываются ко второму микроциклу. После пятого микроцикла до отказа " +
            "только первый подход, остальные с запасом.",
        goal = Goal.Mass,
        phases = listOf(
            WavePhase(
                title = highReps.scheme,
                days = listOf(
                    "Д1 · жим и присед" to listOf(main(Exercise.Bench, highReps), main(Exercise.Squat, highReps)) +
                        listOf(extra("Тяга в наклоне", "3×12"), extra("Лицевая тяга", "3×15-20")),
                    "Д2 · узкий жим и тяга" to listOf(extra(Exercise.CloseGripBench.title, "3×12")) +
                        listOf(main(Exercise.Deadlift, highReps.copy(sets = 2, reps = 12, endReps = 2))) +
                        listOf(extra("Подтягивания", "3×мах"), extra("Пресс", "3×15")),
                    "Д3 · жим" to listOf(main(Exercise.Bench, highReps)) +
                        listOf(extra("Махи в стороны", "3×15"), extra("Французский жим", "3×12")),
                ),
                estimate = LINEAR_PHASE,
            ),
        ),
    )

    /** Все саморегулирующиеся программы. */
    val all: List<TrainingProgram> = listOf(ladder, linear, highRep)
}
