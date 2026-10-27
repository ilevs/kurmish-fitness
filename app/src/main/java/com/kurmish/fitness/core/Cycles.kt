package com.kurmish.fitness.core

/** Вид цикла. */
enum class CycleKind(val title: String) {
    SelfCalculating("Саморасчитывающийся цикл"),
    Block("Блочный цикл на 4 недели"),
    Embedded("Встраиваемый цикл"),
}

/**
 * Готовый цикл из базы и условия, на которые он рассчитан.
 *
 * @property id короткое обозначение (СРЦ15 и т. п.).
 * @property kind вид цикла.
 * @property discipline направление.
 * @property ranks от какого до какого уровня подходит.
 * @property goal задача периода.
 * @property minBodyweightKg минимальный собственный вес; null, если не важен.
 * @property days тренировок в неделю; null, если цикл допускает разные схемы.
 * @property strengthMode в основной работе и подводящих не больше 6 повторов; иначе не меньше 8.
 * @property note для кого цикл и что важно знать.
 */
data class TrainingCycle(
    val id: String,
    val kind: CycleKind,
    val discipline: Discipline,
    val ranks: ClosedRange<Rank>,
    val goal: Goal,
    val minBodyweightKg: Int?,
    val days: Int?,
    val strengthMode: Boolean = true,
    val note: String,
)

/**
 * Вывод подбора: цикл и чем он не подходит.
 *
 * @property cycle цикл.
 * @property issues несоответствия; пустой список, если подходит полностью.
 */
data class CycleMatch(val cycle: TrainingCycle, val issues: List<String>) {
    /** Подходит без оговорок. */
    val fits: Boolean
        get() = issues.isEmpty()
}

/** База циклов и подбор под атлета по правилам таблицы выбора. */
object CycleCatalog {
    private const val KG80 = 80
    private const val KG70 = 70
    private const val KG60 = 60
    private const val TWO = 2
    private const val THREE = 3
    private const val FOUR = 4

    /** Все циклы базы. */
    val cycles: List<TrainingCycle> = listOf(
        self(
            "СРЦ1",
            Discipline.Powerlifting,
            Rank.Second..Rank.Kms,
            Goal.Strength,
            KG80,
            THREE,
            "Базовый для начинающих троеборцев.",
        ),
        self(
            "СРЦ2",
            Discipline.Powerlifting,
            Rank.Kms..Rank.Ms,
            Goal.Strength,
            KG80,
            FOUR,
            "Базовый для троеборцев среднего уровня.",
        ),
        self(
            "СРЦ3",
            Discipline.Bench,
            Rank.Novice..Rank.Third,
            Goal.Endurance,
            KG80,
            null,
            "Для полных новичков и тех, кто возвращается после перерыва. Даёт большие прибавки.",
            strengthMode = false,
        ),
        self("СРЦ4", Discipline.Armwrestling, Rank.Second..Rank.Kms, Goal.Strength, KG80, null, "Для борьбы «верхом»."),
        self(
            "СРЦ5",
            Discipline.Bench,
            Rank.Second..Rank.Kms,
            Goal.Strength,
            KG80,
            null,
            "Базовый для начинающих жимовиков, много жима стоя.",
        ),
        self(
            "СРЦ6",
            Discipline.Bench,
            Rank.Kms..Rank.Ms,
            Goal.Strength,
            KG80,
            null,
            "Базовый для жимовиков среднего уровня.",
        ),
        self(
            "СРЦ7",
            Discipline.Powerlifting,
            Rank.Ms..Rank.Msmk,
            Goal.Peak,
            KG70,
            null,
            "Выход на пик для троеборцев высокого уровня.",
        ),
        self(
            "СРЦ8",
            Discipline.Bodybuilding,
            Rank.Second..Rank.Kms,
            Goal.Mass,
            KG80,
            null,
            "Рост мышечной массы. Результат во многом зависит от питания и режима.",
        ),
        self(
            "СРЦ9",
            Discipline.DeadliftBench,
            Rank.Ms..Rank.Msmk,
            Goal.Mixed,
            KG80,
            null,
            "Акцент на становую тягу, жиму тоже уделено внимание.",
        ),
        self(
            "СРЦ10",
            Discipline.Bench,
            Rank.Kms..Rank.Ms,
            Goal.Endurance,
            KG80,
            null,
            "Выносливость как база для интенсивных периодов. Нужен профицит калорий.",
            strengthMode = false,
        ),
        self(
            "СРЦ11",
            Discipline.Bench,
            Rank.Ms..Rank.Msmk,
            Goal.Endurance,
            KG80,
            null,
            "Базовый цикл на выносливость.",
            strengthMode = false,
        ),
        self(
            "СРЦ12",
            Discipline.DeadliftBench,
            Rank.Ms..Rank.Msmk,
            Goal.Endurance,
            KG80,
            null,
            "Выносливость, жим в приоритете.",
            strengthMode = false,
        ),
        self(
            "СРЦ13",
            Discipline.Bench,
            Rank.Kms..Rank.Msmk,
            Goal.Mixed,
            null,
            null,
            "Интенсивный, рост тоннажа. Нужен профицит калорий.",
        ),
        self(
            "СРЦ14",
            Discipline.Powerlifting,
            Rank.Second..Rank.Ms,
            Goal.Mixed,
            KG60,
            THREE,
            "Для тех, кто ходит в зал не больше трёх раз в неделю. Между тренировками минимум день отдыха.",
        ),
        self(
            "СРЦ15",
            Discipline.Bench,
            Rank.Second..Rank.Kms,
            Goal.Peak,
            KG60,
            TWO,
            "Две тренировки в неделю, лучше Пн-Чт или Вт-Пт, не подряд.",
        ),
        self(
            "СРЦ16",
            Discipline.Bench,
            Rank.Second..Rank.Kms,
            Goal.Endurance,
            KG60,
            TWO,
            "Две тренировки в неделю, многоповторный. После него можно сразу пройти СРЦ15.",
            strengthMode = false,
        ),
        block("Троеборье, начальный", Discipline.Powerlifting, Rank.Novice..Rank.Third),
        block("Троеборье, средний", Discipline.Powerlifting, Rank.First..Rank.Ms),
        block("Троеборье, опытный", Discipline.Powerlifting, Rank.Msmk..Rank.Msmk),
        embedded("Жим стоя, начальный", Discipline.OverheadPress, Rank.Novice..Rank.Second),
        embedded("Жим стоя, средний", Discipline.OverheadPress, Rank.First..Rank.Kms),
        embedded(
            "Жим стоя, опытный",
            Discipline.OverheadPress,
            Rank.Ms..Rank.Msmk,
            "Не брать, если жим стоя меньше 110 кг.",
        ),
        embedded("Строгий подъём на бицепс", Discipline.Biceps, Rank.Novice..Rank.Msmk),
    )

    /** Циклы нужного направления: сначала подходящие полностью, затем с наименьшим числом оговорок. */
    fun select(profile: Profile): List<CycleMatch> = cycles
        .filter { it.discipline == profile.discipline }
        .map { CycleMatch(it, issues(it, profile)) }
        .sortedBy { it.issues.size }

    /** Лучший цикл для атлета, если хоть один подходит полностью. */
    fun best(profile: Profile): TrainingCycle? = select(profile).firstOrNull { it.fits }?.cycle

    private fun issues(cycle: TrainingCycle, profile: Profile): List<String> = buildList {
        if (profile.rank !in cycle.ranks) {
            add("уровень: ${cycle.ranks.start.title} – ${cycle.ranks.endInclusive.title}")
        }
        if (cycle.kind != CycleKind.Embedded && cycle.goal != profile.goal) {
            add("задача: ${cycle.goal.title.lowercase()}")
        }
        val weight = profile.bodyweightKg
        if (cycle.minBodyweightKg != null && weight != null && weight < cycle.minBodyweightKg) {
            add("собственный вес от ${cycle.minBodyweightKg} кг")
        }
        if (cycle.days != null && cycle.days != profile.daysPerWeek) add("тренировок в неделю: ${cycle.days}")
    }

    @Suppress("LongParameterList")
    private fun self(
        id: String,
        discipline: Discipline,
        ranks: ClosedRange<Rank>,
        goal: Goal,
        minWeight: Int?,
        days: Int?,
        note: String,
        strengthMode: Boolean = true,
    ) = TrainingCycle(
        id = id,
        kind = CycleKind.SelfCalculating,
        discipline = discipline,
        ranks = ranks,
        goal = goal,
        minBodyweightKg = minWeight,
        days = days,
        strengthMode = strengthMode,
        note = note,
    )

    private fun block(id: String, discipline: Discipline, ranks: ClosedRange<Rank>) = TrainingCycle(
        id = id,
        kind = CycleKind.Block,
        discipline = discipline,
        ranks = ranks,
        goal = Goal.Peak,
        minBodyweightKg = KG60,
        days = null,
        note = "4 недели, 3–5 тренировок. Пройденный блок можно повторять, пока есть прибавка.",
    )

    private fun embedded(id: String, discipline: Discipline, ranks: ClosedRange<Rank>, extra: String = "") =
        TrainingCycle(
            id = id,
            kind = CycleKind.Embedded,
            discipline = discipline,
            ranks = ranks,
            goal = Goal.Strength,
            minBodyweightKg = null,
            days = null,
            note = "Добавляется в конец тренировки основной программы, лёгкая нагрузка. $extra".trim(),
        )
}
