package com.kurmish.fitness.core

/**
 * Собирает конкретный план на сегодня: разминка, упражнения с весом, подходами и повторами, заминка.
 *
 * Если в профиле выбрана программа, упражнения берутся из её следующей тренировки, иначе из общей
 * тренировки по цели ([BasicTemplate]). Записи за сегодня не сдвигают план: пока день не кончился,
 * на экране та тренировка, которую атлет сейчас делает.
 */
object Planner {
    private val schemePattern = Regex("""^(\d+)(?:-\d+)?×([^\s,]+)[,\s]*(.*)$""")
    private val wordPattern = Regex("[^а-яa-z]+")
    private const val SCHEME_REST = 3

    /** План на день [today] (дни с 1970-01-01). */
    fun today(data: AppData, today: Long): DayPlan {
        val before = data.sessions.filter { it.epochDay < today }
        val position = Macrocycle.position(data.profile, before, today)
        val period = YearPlan.position(data.profile.yearPlanStartEpochDay, today)?.period.takeIf { position == null }
        val program = when {
            FitnessTest.needed(data, today) -> FitnessTest.plan(data.profile)
            position != null -> programPlan(position, data, today)
            else -> BasicTemplate.plan(data, today, before.size)
        }
        val body = program.copy(items = program.items.map { withPace(it, period) })
        val work = body.items
        val planned = if (work.isEmpty()) {
            body
        } else {
            body.copy(
                items = listOf(warmUp(work.first())) + work + coolDown(),
                note = listOfNotNull(body.note, Pace.LEGEND).joinToString(" "),
            )
        }
        return planned
    }

    /** Добавляет темп и отдых, если программа их не задала. */
    private fun withPace(item: PlanItem, period: Period?): PlanItem {
        val exercise = item.exercise
        val given = item.notes.any { it.contains("отдых", ignoreCase = true) }
        return if (exercise == null || given) {
            item
        } else {
            item.copy(notes = item.notes + Pace.note(exercise, item.sets.firstOrNull()?.reps, period))
        }
    }

    private fun programPlan(position: MacroPosition, data: AppData, today: Long): DayPlan {
        val program = position.program
        val next = position.progress.next
        val block = if (position.count > 1) ", блок ${position.index + 1} из ${position.count}" else ""
        return if (next == null) {
            DayPlan(
                title = "Программа «${program.title}» пройдена",
                items = emptyList(),
                note = "Дальше разгрузочная неделя с небольшим объёмом и весами, после неё проверь максимум. " +
                    "Следующий макроцикл составь во вкладке «Циклы».",
            )
        } else {
            DayPlan(
                title = "${next.day}, микроцикл ${next.microcycle} из ${program.microcycles}",
                items = next.items.map { item -> withChoice(item, data.profile.pool[item.name], data, today) },
                note = "Программа «${program.title}»$block.",
            )
        }
    }

    /** Пункт программы; если атлет выбрал на цикл другое упражнение [chosen], веса считаются от его ПМ. */
    private fun withChoice(item: ProgramItem, chosen: Exercise?, data: AppData, today: Long): PlanItem {
        val used = chosen?.let { item.copy(name = it.title, maxOf = it.takeIf { item.maxOf != null }) } ?: item
        val exercise = chosen ?: item.maxOf ?: exerciseIn(item.name)
        val planned = fromProgram(
            item = used,
            exercise = exercise,
            max = exercise?.let { data.workingMax(it, today) },
            last = exercise?.let { data.lastSet(it, today) },
            previous = exercise?.let { data.previousSets(it, today) }.orEmpty(),
        )
        return if (chosen == null) planned else planned.copy(slot = item.name)
    }

    /**
     * Пункт программы для упражнения [exercise] с весами от ПМ [max].
     *
     * Если программа задаёт вес процентами, а ПМ неизвестен, проценты остаются в пояснении. Если процентов нет,
     * вес подбирается по числу повторов от своего ПМ, по последней записи в Дневнике [last] или по ПМ
     * близкого упражнения. Если повторы заданы диапазоном («6-8») и есть прошлая тренировка [previous],
     * вес идёт по [DoubleProgression].
     */
    internal fun fromProgram(
        item: ProgramItem,
        exercise: Exercise?,
        max: WorkingMax?,
        last: SetGroup? = null,
        previous: List<SetGroup> = emptyList(),
    ): PlanItem {
        val match = item.scheme?.let { schemePattern.find(it) }
        val sets = match?.groupValues?.get(1)?.toInt()
        val reps = match?.groupValues?.get(2)
        val guess = exercise?.takeIf { item.percents.isEmpty() && item.kg == null && sets != null }
            ?.let { freeWeight(it, PlannedSets(sets ?: 0, reps.orEmpty(), null), max, last, previous) }
        val planned = when {
            sets == null || reps == null -> emptyList()
            item.kg != null -> listOf(PlannedSets(sets, reps, item.kg))
            guess != null -> listOf(PlannedSets(sets, reps, guess.first))
            exercise != null && max?.bodyweightKg != null ->
                BodyweightLoad.byPercents(exercise, item.percents, sets, reps, max)
            else -> byPercents(item, sets, reps, max, exercise?.barbell != false)
        }
        val rest = if (match == null) item.scheme else match.groupValues[SCHEME_REST].ifBlank { null }
        return PlanItem(PlanPart.Work, item.name, exercise, planned, notes(item, rest, exercise, max, guess?.second))
    }

    /** Вес без процентов: по двойной прогрессии от прошлой тренировки, иначе по [WeightAdvice.byReps]. */
    private fun freeWeight(
        exercise: Exercise,
        scheme: PlannedSets,
        max: WorkingMax?,
        last: SetGroup?,
        previous: List<SetGroup>,
    ): Pair<Double?, String>? = DoubleProgression.next(exercise, scheme.reps, scheme.sets, previous)
        ?: WeightAdvice.byReps(exercise, scheme.reps, max, last)

    private fun notes(item: ProgramItem, rest: String?, exercise: Exercise?, max: WorkingMax?, guess: String?) =
        listOfNotNull(rest, guess) + item.percents.takeIf { it.isNotEmpty() }?.let { percents ->
            val note = WeightAdvice.percentNote(percents.joinToString(", ") { "$it %" }, max, exercise)
            val hint = max?.takeIf { it.bodyweightKg != null }?.let { found ->
                exercise?.let { BodyweightLoad.hint(it, found) }
            }
            listOfNotNull(note, hint)
        }.orEmpty()

    private fun byPercents(
        item: ProgramItem,
        sets: Int,
        reps: String,
        max: WorkingMax?,
        barbell: Boolean,
    ): List<PlannedSets> = when {
        item.percents.isEmpty() || max == null -> listOf(PlannedSets(sets, reps, null))
        item.percents.size == 1 -> listOf(PlannedSets(sets, reps, item.weights(max.kg, barbell).single()))
        else -> item.weights(max.kg, barbell).map { PlannedSets(1, reps, it) }
    }

    /** Упражнение из базы, которое узнаётся в названии [name]; null, если не узнаётся. */
    internal fun exerciseIn(name: String): Exercise? {
        val tokens = name.lowercase().replace('ё', 'е').split(wordPattern).filter { it.isNotEmpty() }
        return tokens.indices.firstNotNullOfOrNull { ExerciseMatcher.matchAt(tokens, it)?.first }
    }

    private fun warmUp(first: PlanItem): PlanItem {
        val lift = first.exercise
        val work = first.sets.firstOrNull()
        val weight = work?.weightKg
        val reps = work?.reps?.substringBefore('-')?.toIntOrNull()
        val special = if (lift != null && weight != null) WarmUp.sets(lift, weight, reps) else emptyList()
        val steps = WarmUp.routine(lift) + listOfNotNull(
            weight?.let { "Подводящие подходы к первому рабочему весу: ${first.title}, ${it.kg()}" }
                ?.takeIf { special.isNotEmpty() },
        )
        return PlanItem(
            part = PlanPart.WarmUp,
            title = "Разминка",
            exercise = null,
            sets = special.map { PlannedSets(it.sets, it.reps, it.weightKg) },
            notes = steps,
        )
    }

    private fun coolDown(): PlanItem = PlanItem(
        part = PlanPart.CoolDown,
        title = "Заминка",
        exercise = null,
        sets = emptyList(),
        notes = listOf(
            "1–2 лёгких подхода на 12–20 повторов без отказа",
            "Растяжка работавших мышц по 15–30 секунд",
            "5–10 минут спокойной ходьбы",
        ),
    )
}
