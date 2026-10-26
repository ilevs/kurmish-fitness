package com.kurmish.fitness.core

import kotlin.math.abs

/**
 * Совет тренера на сегодня.
 *
 * @property headline главное в одной фразе.
 * @property plan что делать на следующей тренировке программы; пусто без программы.
 * @property warmUp подводящие подходы к первому рабочему весу тренировки.
 * @property points подробности по пунктам.
 */
data class Advice(
    val headline: String,
    val plan: List<String>,
    val warmUp: List<WarmUpSet>,
    val points: List<String>,
) {
    /** Текст для озвучки целиком. */
    val speech: String
        get() {
            val warm = if (warmUp.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    "Разминка: " + warmUp.joinToString { it.label } + "."
                )
            }
            return (listOf(headline) + plan + warm + points).joinToString(" ")
        }
}

/**
 * Даёт совет «что и как тренировать» по профилю и дневнику.
 *
 * Правила взяты из инструкций к циклам: отдых между тренировками, процент корректировки по самочувствию,
 * разминка от пустого грифа, ограничения по повторам, лёгкая заминка. Все правила перечислены
 * в docs/explanation/methodology.md.
 */
object Coach {
    private const val EASY_STEP = 2.0
    private const val NATURAL_CEILING = 1.0
    private const val MIN_DIFFERENCE_KG = 2.5
    private const val COOL_DOWN_FROM = 40
    private const val COOL_DOWN_TO = 70
    private const val FAST_TEMPO_FROM = 50
    private const val STRENGTH_REPS = 6
    private const val ENDURANCE_REPS = 8
    private const val MAX_DAYS_WITH_REST = 3
    private const val EPSILON = 0.001

    /** Совет на день [today] (дни с 1970-01-01). */
    fun advise(data: AppData, today: Long): Advice {
        val profile = data.profile
        val cycle = CycleCatalog.best(profile)
        val last = data.sessions.maxByOrNull { it.epochDay }
        val program = Programs.byId(profile.programId)
        val progress = program?.let { Programs.progress(it, profile.programStartEpochDay ?: today, data.sessions) }
        val next = progress?.next
        val points = buildList {
            if (program == null) add(cycleLine(cycle))
            addAll(maxLines(data, today))
            if (program == null) correctionLine(last, profile, cycle)?.let(::add)
            addAll(if (program == null) techniqueLines(cycle?.strengthMode != false) else programTechniqueLines())
        }
        val plan = when {
            program == null || progress == null -> emptyList()
            next == null -> listOf(
                "Программа «${program.title}» пройдена. Дальше разгрузочная неделя с ограниченной " +
                    "интенсивностью и объёмом, после неё проверь максимум.",
            )
            else -> planLines(program, next, data, today)
        }
        return Advice(headline(last, profile, today), plan, warmUpFor(next, data, today), points)
    }

    private fun planLines(program: TrainingProgram, next: ProgramSession, data: AppData, today: Long): List<String> =
        listOf("Следующая тренировка: ${next.day}, микроцикл ${next.microcycle} из ${program.microcycles}.") +
            next.items.map { item -> item.describe(item.maxOf?.let { data.currentMax(it, today) }) + "." }

    private fun warmUpFor(next: ProgramSession?, data: AppData, today: Long): List<WarmUpSet> {
        val main = next?.items?.firstOrNull { it.maxOf != null && it.percents.isNotEmpty() }
        val lift = main?.maxOf
        val max = lift?.let { data.currentMax(it, today) }
        return if (main == null || lift == null || max == null) {
            emptyList()
        } else {
            WarmUp.sets(
                lift,
                main.weights(max).first()
            )
        }
    }

    private fun headline(last: WorkoutSession?, profile: Profile, today: Long): String {
        val gap = last?.let { today - it.epochDay }
        return when {
            gap == 0L -> "Сегодня тренировка уже записана. Восстанавливайся: сон, еда, растяжка."
            gap == 1L && profile.daysPerWeek <= MAX_DAYS_WITH_REST ->
                "Вчера была тренировка. Сегодня лучше отдохнуть: подряд тренироваться нежелательно, " +
                    "подойдёт лёгкая зарядка или растяжка."
            else -> "Сегодня можно тренироваться."
        }
    }

    private fun cycleLine(cycle: TrainingCycle?): String = if (cycle == null) {
        "Подходящий цикл не найден: проверь профиль и выбери цикл с наименьшими оговорками на вкладке «Профиль»."
    } else {
        "Твой цикл: ${cycle.id}. ${cycle.note}"
    }

    private fun maxLines(data: AppData, today: Long): List<String> {
        val estimates = data.estimatedMaxes(today)
        return Exercise.entries.filter { it.isMainLift }.mapNotNull { exercise ->
            val manual = data.profile.manualMaxes[exercise]
            val estimate = estimates[exercise]?.kg?.let(Strength::roundToPlates)
            when {
                manual != null && estimate != null && estimate - manual >= MIN_DIFFERENCE_KG ->
                    "${exercise.title}: по дневнику максимум около ${estimate.kg()}, это выше введённого " +
                        "${manual.kg()}. Не завышай ПМ: ставь реальный или близкий к нему."
                manual != null -> "${exercise.title}: ПМ ${manual.kg()}."
                estimate != null -> "${exercise.title}: оценка ПМ по дневнику ${estimate.kg()}."
                else -> null
            }
        }
    }

    private fun correctionLine(last: WorkoutSession?, profile: Profile, cycle: TrainingCycle?): String? {
        val ceiling = cycle?.maxCorrectionPercent
        val current = profile.correctionPercent
        return when (last?.feeling) {
            Feeling.Hard -> "Прошлая тренировка далась тяжело: поставь процент корректировки 0 %."
            Feeling.Easy -> {
                val raised = (current + EASY_STEP).let { if (ceiling != null) minOf(it, ceiling) else it }
                if (abs(raised - current) < EPSILON) {
                    "Было легко, но процент корректировки уже на максимуме для цикла: замедли темп."
                } else {
                    "Было легко: процент корректировки можно поднять до ${raised.percent()}."
                }
            }
            else -> if (profile.natural && current > NATURAL_CEILING) {
                "Для натурального атлета процент корректировки обычно 0,5–1 %, сейчас ${current.percent()}."
            } else {
                null
            }
        }
    }

    private fun programTechniqueLines(): List<String> = listOf(
        "Перед базовым движением сделай подводящие подходы по таблице разминки.",
        "Отдых между подходами не ограничивай: размеренный, неспешный темп силовой тренировки только на пользу.",
        "В подсобных упражнениях вес подбирай так, чтобы схема выполнялась чисто, без отказа.",
    )

    private fun techniqueLines(strengthMode: Boolean): List<String> = listOf(
        "Начни с общей разминки и подводящих подходов от пустого грифа.",
        if (strengthMode) {
            "В подводящих подходах и заминке не больше $STRENGTH_REPS повторов."
        } else {
            "Цикл многоповторный: в подводящих подходах и заминке не меньше $ENDURANCE_REPS повторов."
        },
        "Темп: до $FAST_TEMPO_FROM % от ПМ средний, от $FAST_TEMPO_FROM % быстрый или комфортный.",
        "Заминка лёгкая или средняя, $COOL_DOWN_FROM–$COOL_DOWN_TO % от ПМ, без отказа, темп медленный. " +
            "Затем растяжка плеч и грудных.",
    )
}

/** «102,5 кг» с русской запятой. */
fun Double.kg(): String = "${number()} кг"

/** «0,5 %». */
fun Double.percent(): String = "${number()} %"

private fun Double.number(): String =
    if (this == Math.floor(this)) toLong().toString() else toString().replace('.', ',')
