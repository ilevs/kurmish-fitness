package com.kurmish.fitness.core

/**
 * Упражнение тренировки программы.
 *
 * @property name название как в программе.
 * @property scheme подходы × повторы, например «10×10» или «4×мах»; null, если схема не задана.
 * @property maxOf от ПМ какого движения считаются веса; null, если вес подбирается по самочувствию.
 * @property percents проценты от ПМ: один на все подходы или по одному на каждый подход.
 * @property kg вес в килограммах, если он задан не процентами; так бывает в своих циклах.
 */
data class ProgramItem(
    val name: String,
    val scheme: String?,
    val maxOf: Exercise?,
    val percents: List<Int>,
    val kg: Double? = null,
) {
    /** Рабочие веса по ПМ, округлённые под блины; пустой список, если веса не задаются процентами. */
    fun weights(maxKg: Double, barbell: Boolean = true): List<Double> =
        percents.map { Strength.percentOf(maxKg, it, barbell) }

    /** «Жим лёжа 10×10 по 50 кг (50 %)» или «Присед 4×6: 135, 140, 147,5, 155 кг». */
    fun describe(maxKg: Double?): String {
        val base = listOfNotNull(name, scheme).joinToString(" ")
        return when {
            kg != null -> "$base по ${kg.kg()}"
            percents.isEmpty() -> base
            maxKg == null -> "$base, ${percents.joinToString(", ") { "$it %" }} от ПМ"
            percents.size == 1 -> "$base по ${weights(maxKg).single().kg()} (${percents.single()} %)"
            else -> "$base: ${weights(maxKg).joinToString(", ") { it.kg().removeSuffix(" кг") }} кг"
        }
    }
}

/**
 * Одна тренировка программы.
 *
 * @property day название дня («Д1», «Тренировка А»).
 * @property microcycle номер микроцикла (недели), с 1.
 * @property items упражнения по порядку.
 */
data class ProgramSession(val day: String, val microcycle: Int, val items: List<ProgramItem>)

/**
 * Программа с расписанными по микроциклам тренировками.
 *
 * @property id постоянный идентификатор для профиля.
 * @property title название.
 * @property note для чего программа и что важно знать.
 * @property daysPerWeek тренировок в микроцикле.
 * @property goal на что программа работает.
 * @property sessions тренировки по порядку выполнения.
 */
data class TrainingProgram(
    val id: String,
    val title: String,
    val note: String,
    val daysPerWeek: Int,
    val goal: Goal,
    val sessions: List<ProgramSession>,
) {
    /** Сколько микроциклов в программе. */
    val microcycles: Int
        get() = sessions.maxOf { it.microcycle }

    /** «4 тренировки в неделю · 5 недель · цель: сила». */
    val summary: String
        get() = "${trainings(daysPerWeek)} в неделю · ${plural(microcycles, "неделя", "недели", "недель")} · " +
            "цель: ${goal.title.lowercase()}"

    /** Чем программа расходится с профилем; пустой список, если совпадает. */
    fun mismatches(profile: Profile): List<String> = listOfNotNull(
        "в профиле ${trainings(profile.daysPerWeek)} в неделю".takeIf { profile.daysPerWeek != daysPerWeek },
        "в профиле цель «${profile.goal.title.lowercase()}»".takeIf { profile.goal != goal },
    )

    private fun trainings(count: Int): String = plural(count, "тренировка", "тренировки", "тренировок")
}

private const val DECIMAL = 10
private const val HUNDRED = 100
private val teens = 11..14
private val fewEndings = 2..4

/** «1 неделя», «3 недели», «5 недель». */
internal fun plural(count: Int, one: String, few: String, many: String): String {
    val last = count % DECIMAL
    val word = when {
        count % HUNDRED in teens -> many
        last == 1 -> one
        last in fewEndings -> few
        else -> many
    }
    return "$count $word"
}

/**
 * Где атлет находится в программе.
 *
 * @property done сколько тренировок программы уже записано.
 * @property next следующая тренировка; null, если программа пройдена.
 */
data class ProgramProgress(val done: Int, val next: ProgramSession?)

/** Встроенные программы и расчёт следующей тренировки. */
object Programs {
    /** Линия упражнения сквозь микроциклы: схема и проценты на каждый микроцикл, null — упражнения нет. */
    private class Track(
        val name: String,
        val schemes: List<String?>,
        val maxOf: Exercise? = null,
        val percents: List<List<Int>> = emptyList(),
    ) {
        fun at(microcycle: Int): ProgramItem? = schemes.getOrNull(microcycle)?.let { scheme ->
            ProgramItem(name, scheme.ifEmpty { null }, maxOf, percents.getOrNull(microcycle).orEmpty())
        }
    }

    private class Day(val title: String, val tracks: List<Track>)

    private fun build(days: List<Day>, microcycles: Int): List<ProgramSession> = (0 until microcycles).flatMap { mc ->
        days.map { day -> ProgramSession(day.title, mc + 1, day.tracks.mapNotNull { it.at(mc) }) }
    }

    private val volumeMain = listOf("10×10", "10×8", "10×6", "10×4", "10×2")
    private val volumePercents = listOf(listOf(50), listOf(60), listOf(70), listOf(80), listOf(90))
    private val pause = listOf("5×5", "5×4", "5×3", "5×2", null)
    private val fiveSets = listOf("5×12", "5×10", "5×8", "5×6", null)
    private val forearms = listOf("3×20-15", "3×15-12", "3×12-10", "3×10-8", null)
    private val bends = listOf("4×5", "4×4", "4×3", "4×2", null)
    private val hanging = listOf("3-4×мах", "3-4×мах", "3-4×мах", "3-4×мах", null)
    private val finalPump = listOf(null, null, null, null, "2×20")

    /** Объёмная программа на 4 дня и 5 микроциклов: базовое движение 10 подходов от 50 до 90 % ПМ. */
    val maximum = TrainingProgram(
        id = "maximum",
        title = "Максимум",
        note = "Базовое движение дня — 10 подходов, вес растёт " +
            "с 50 до 90 % ПМ, повторы снижаются с 10 до 2. Пятый микроцикл — подводка: мало объёма в подсобке.",
        daysPerWeek = 4,
        goal = Goal.Strength,
        sessions = build(
            days = listOf(
                Day(
                    "Д1",
                    listOf(
                        Track("Жим штанги лёжа", volumeMain, Exercise.Bench, volumePercents),
                        Track("Жим с упоров до середины с удержанием 2 сек.", pause),
                        Track("Подтягивания широким хватом", fiveSets),
                        Track("Отведения с гантелями стоя", fiveSets),
                        Track("Подъём EZ-грифа на бицепс у стены", fiveSets),
                        Track("Разгибания кисти с гантелей сидя одной рукой", forearms),
                        Track("Вертикальная тяга произвольным хватом", finalPump),
                        Track("Отведения на блоке одной рукой", finalPump),
                        Track("Подъём на блоке на бицепс одной рукой", finalPump),
                    ),
                ),
                Day(
                    "Д2",
                    listOf(
                        Track("Присед с низкой штангой", volumeMain, Exercise.Squat, volumePercents),
                        Track("Присед с упоров до середины с удержанием 2 сек.", pause),
                        Track("Наклоны со штангой стоя", bends),
                        Track("Разгибания голени в тренажёре одной ногой", forearms),
                        Track("Подъёмы прямых ног к перекладине в висе", hanging),
                        Track("Жим ногами", listOf(null, null, null, null, "2×20-30")),
                    ),
                ),
                Day(
                    "Д3",
                    listOf(
                        Track("Жим штанги лёжа средним хватом", volumeMain, Exercise.Bench, volumePercents),
                        Track("Жим сидя в Смите до середины с удержанием 2 сек.", pause),
                        Track("Подтягивания прямым средним хватом", fiveSets),
                        Track("Лицевая тяга на блоке", listOf("5×20-15", "5×15-12", "5×12-10", "5×10-8", null)),
                        Track("Французский жим с EZ-грифом лёжа на полу", fiveSets),
                        Track("Сгибания кистей со штангой за спиной", forearms),
                        Track("Вертикальная тяга произвольным хватом", finalPump),
                        Track("Обратные разводки на блоке одной рукой стоя", finalPump),
                        Track(
                            "Разгибания предплечья на блоке обратным хватом одной рукой",
                            listOf(null, null, null, null, "")
                        ),
                    ),
                ),
                Day(
                    "Д4",
                    listOf(
                        Track("Становая тяга с пола", volumeMain, Exercise.Deadlift, volumePercents),
                        Track("Тяга из ямы до коленей с удержанием 2 сек.", pause),
                        Track("Тяга штанги в наклоне обратным хватом", bends),
                        Track("Сгибания голени в тренажёре стоя одной ногой", forearms),
                        Track("Подъёмы прямых ног к перекладине в висе", hanging),
                        Track("Фронтальная тяга блока сидя", finalPump),
                        Track("Сгибания голени в любом тренажёре", finalPump),
                    ),
                ),
            ),
            microcycles = 5,
        ),
    )

    private val strengthMain = listOf("4×6", "4×5", "4×4", "3×3")
    private val hyperTen = listOf("4×10", "4×8", "4×6", "4×4")
    private val hyperTwelve = listOf("4×12", "4×10", "4×8", "4×6")
    private val maxReps = listOf("4×мах", "4×мах", "4×мах", "4×мах")

    /** Силово-гипертрофийный блок на 4 дня и 4 микроцикла: базовые движения 4×6 → 3×3 с ростом веса. */
    val hypertrophy = TrainingProgram(
        id = "hypertrophy",
        title = "Гипертрофия",
        note = "В базовых движениях подходы с разным весом, " +
            "от 4×6 до 3×3, вес растёт к последней неделе, к концу до 90 % ПМ. " +
            "В подсобке вес подбирай так, чтобы схема выполнялась чисто.",
        daysPerWeek = 4,
        goal = Goal.Mass,
        sessions = build(
            days = listOf(
                Day(
                    "Тренировка А",
                    listOf(
                        Track(
                            "Присед с низкой штангой",
                            listOf("4×6", "4×5", "4×4", "3×3, последний подход ×2"),
                            Exercise.Squat,
                            listOf(
                                listOf(60, 63, 66, 69),
                                listOf(66, 69, 72, 75),
                                listOf(63, 69, 75, 81),
                                listOf(78, 84, 90)
                            ),
                        ),
                        Track("Приседания в гакк-тренажёре", listOf("4×12", "4×10", "4×8", "4×6")),
                        Track("Приседания с закреплёнными ногами", maxReps),
                        Track(
                            "Разгибания голени в тренажёре",
                            listOf("3×12 (1,5)", "3×10 (1,5)", "3×8 (1,5)", "3×6 (1,5)")
                        ),
                        Track("Икры сидя", listOf("4×20", "4×20", "4×15", "4×15")),
                        Track("Икры стоя в тренажёре", listOf("2×20", "2×20", "2×15", "2×15")),
                    ),
                ),
                Day(
                    "Тренировка Б",
                    listOf(
                        Track(
                            "Жим штанги лёжа",
                            listOf("4×6", "4×5", "4×4, третий подход ×3", "3×3, последний подход ×2"),
                            Exercise.Bench,
                            listOf(
                                listOf(54, 63, 72, 68),
                                listOf(59, 68, 77, 72),
                                listOf(63, 72, 81, 77),
                                listOf(72, 81, 86)
                            ),
                        ),
                        Track("Подтягивания обратным хватом с отягощением", strengthMain),
                        Track("Жим штанги сидя", strengthMain),
                        Track("Жим гантелей на горизонтальной скамье", hyperTen),
                        Track("Жим в тренажёре на грудь горизонтальный", listOf("4×12", "4×10", "4×8", "4×мах")),
                        Track("Вертикальная тяга широким хватом к груди", hyperTen),
                        Track("Вертикальная тяга широким хватом за голову", hyperTwelve),
                        Track("Высокая тяга штанги с виса", hyperTen),
                        Track("Отведения с гантелями сидя", hyperTwelve),
                    ),
                ),
                Day(
                    "Тренировка В",
                    listOf(
                        Track(
                            "Становая тяга",
                            listOf("4×6", "4×5", "4×4", "3×3, последний подход — попытка"),
                            Exercise.Deadlift,
                            listOf(
                                listOf(62, 68, 73, 79),
                                listOf(68, 73, 79, 85),
                                listOf(73, 79, 85, 90),
                                listOf(85, 90, 95)
                            ),
                        ),
                        Track(
                            "Тяга штанги в наклоне с пола",
                            listOf("4×10", "4×8", "4×6", "4×4"),
                            Exercise.BentOverRow,
                            listOf(
                                listOf(50, 55, 60, 65),
                                listOf(55, 60, 65, 70),
                                listOf(65, 70, 75, 80),
                                listOf(80, 90, 100, 90)
                            ),
                        ),
                        Track("Фронтальная тяга блока сидя узким хватом", hyperTwelve),
                        Track("Шраги со штангой", hyperTwelve),
                        Track("GHR", maxReps),
                        Track("Сгибания голени в тренажёре лёжа", hyperTwelve),
                        Track("Икры стоя в тренажёре", listOf("3×20-30", "3×20-30", "3×15-25", "3×15-25")),
                    ),
                ),
                Day(
                    "Тренировка Г",
                    listOf(
                        Track(
                            "Жим штанги лёжа, согнутые ноги на лавке",
                            strengthMain,
                            Exercise.Bench,
                            listOf(
                                listOf(63, 69, 74, 69),
                                listOf(69, 74, 77, 74),
                                listOf(71, 77, 83, 77),
                                listOf(74, 80, 86)
                            ),
                        ),
                        Track("Подъём EZ-штанги на бицепс у стены", hyperTen),
                        Track("Подъём гантели на скамье Скотта одной рукой", hyperTwelve),
                        Track("Жим лёжа узким хватом", hyperTen),
                        Track("Французский жим с EZ-штангой сидя", hyperTwelve),
                        Track("Лицевая тяга на блоке", listOf("4×15", "4×12", "4×10", "4×мах")),
                        Track("Обратные разводки в тренажёре", listOf("4×10", "4×8", "4×6", "4×мах")),
                        Track("Хват и предплечья", listOf("3×мах", "3×мах", "3×мах", "3×мах")),
                    ),
                ),
            ),
            microcycles = 4,
        ),
    )

    /** Все встроенные программы. */
    val all: List<TrainingProgram> = listOf(
        maximum,
        hypertrophy,
        IntegralProgram.program,
        ShortPrograms.maintenance,
        ShortPrograms.fullBodyTwice,
        ShortPrograms.upperLowerThree,
    )

    /** Прежние названия упражнений программ → новые: выбор на цикл в профиле переносится на новое название. */
    val renamed: Map<String, String> = mapOf(
        "Присед с низкой штангой в бинтах" to "Присед с низкой штангой",
        "Жим лёжа в слинге" to "Жим штанги лёжа",
        "Становая тяга с обратной резиной" to "Становая тяга",
    )

    /** Программа по идентификатору из профиля. */
    fun byId(id: String?): TrainingProgram? = all.firstOrNull { it.id == id }

    /**
     * Прогресс по программе: каждая запись в дневнике начиная с [startEpochDay] считается
     * выполненной тренировкой программы, по порядку.
     */
    fun progress(program: TrainingProgram, startEpochDay: Long, sessions: List<WorkoutSession>): ProgramProgress {
        val done = sessions.count { it.epochDay >= startEpochDay }
        return ProgramProgress(done, program.sessions.getOrNull(done))
    }
}
