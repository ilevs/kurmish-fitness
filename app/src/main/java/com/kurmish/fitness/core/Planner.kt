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
    private val legsFirst = setOf(Exercise.Squat, Exercise.Deadlift, Exercise.StiffLegDeadlift)
    private const val SCHEME_REST = 3

    /** План на день [today] (дни с 1970-01-01). */
    fun today(data: AppData, today: Long): DayPlan {
        val before = data.sessions.filter { it.epochDay < today }
        val program = Programs.byId(data.profile.programId)
        val body = if (program == null) {
            BasicTemplate.plan(data, today, before.size)
        } else {
            val progress = Programs.progress(program, data.profile.programStartEpochDay ?: today, before)
            programPlan(program, progress.next) { lift -> data.currentMax(lift, today) }
        }
        val work = body.items
        return if (work.isEmpty()) body else body.copy(items = listOf(warmUp(work.first())) + work + coolDown())
    }

    private fun programPlan(program: TrainingProgram, next: ProgramSession?, maxOf: (Exercise) -> Double?): DayPlan =
        if (next == null) {
            DayPlan(
                title = "Программа «${program.title}» пройдена",
                items = emptyList(),
                note = "Дальше разгрузочная неделя с небольшим объёмом и весами, после неё проверь максимум.",
            )
        } else {
            DayPlan(
                title = "${next.day}, микроцикл ${next.microcycle} из ${program.microcycles}",
                items = next.items.map { item -> fromProgram(item, item.maxOf?.let(maxOf)) },
                note = "Программа «${program.title}».",
            )
        }

    /** Пункт программы с весами от ПМ [max]; без ПМ веса остаются процентами в пояснении. */
    internal fun fromProgram(item: ProgramItem, max: Double?): PlanItem {
        val match = item.scheme?.let { schemePattern.find(it) }
        val sets = match?.groupValues?.get(1)?.toInt()
        val reps = match?.groupValues?.get(2)
        val planned = when {
            sets == null || reps == null -> emptyList()
            item.percents.isEmpty() || max == null -> listOf(PlannedSets(sets, reps, null))
            item.percents.size == 1 -> listOf(PlannedSets(sets, reps, item.weights(max).single()))
            else -> item.weights(max).map { PlannedSets(1, reps, it) }
        }
        val percents = item.percents.takeIf { it.isNotEmpty() }?.joinToString(", ") { "$it %" }
        val notes = listOfNotNull(
            if (match == null) item.scheme else match.groupValues[SCHEME_REST].ifBlank { null },
            percents?.let { if (max == null) "$it от ПМ: введи ПМ в профиле" else "$it от ПМ" },
        )
        return PlanItem(PlanPart.Work, item.name, item.maxOf ?: exerciseIn(item.name), planned, notes)
    }

    private fun exerciseIn(name: String): Exercise? {
        val tokens = name.lowercase().replace('ё', 'е').split(wordPattern).filter { it.isNotEmpty() }
        return tokens.indices.firstNotNullOfOrNull { ExerciseMatcher.matchAt(tokens, it)?.first }
    }

    private fun warmUp(first: PlanItem): PlanItem {
        val lift = first.exercise
        val weight = first.sets.firstOrNull()?.weightKg
        val special = if (lift != null && weight != null) WarmUp.sets(lift, weight) else emptyList()
        val steps = listOfNotNull(
            "3–5 минут лёгкого кардио".takeIf { lift in legsFirst },
            "Суставная разминка: от мелких суставов к крупным",
            "Круг без веса по 8–10 повторов: приседания, отжимания, гиперэкстензия, скручивания",
            "Растяжка и мобильность под первое упражнение",
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
