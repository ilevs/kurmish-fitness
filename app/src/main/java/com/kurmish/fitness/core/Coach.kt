package com.kurmish.fitness.core

/**
 * Совет тренера на сегодня.
 *
 * Что делать на тренировке по пунктам, считает [Planner]; совет — про отдых, максимумы и технику.
 *
 * @property headline главное в одной фразе.
 * @property points подробности по пунктам.
 */
data class Advice(val headline: String, val points: List<String>)

/**
 * Даёт совет «что и как тренировать» по профилю и дневнику.
 *
 * Правила взяты из инструкций к циклам: отдых между тренировками, нагрузка по самочувствию,
 * разминка от пустого грифа, ограничения по повторам, лёгкая заминка. Все правила перечислены
 * в docs/explanation/methodology.md.
 */
object Coach {
    private const val MIN_DIFFERENCE_KG = 2.5
    private const val COOL_DOWN_FROM = 40
    private const val COOL_DOWN_TO = 70
    private const val FAST_TEMPO_FROM = 50
    private const val STRENGTH_REPS = 6
    private const val ENDURANCE_REPS = 8
    private const val MAX_DAYS_WITH_REST = 3
    private const val HARD_FEELING =
        "Прошлая тренировка далась тяжело: сегодня сними с рабочих весов 5–10 %, а если самочувствие плохое, отдохни."

    /** Совет на день [today] (дни с 1970-01-01). */
    fun advise(data: AppData, today: Long): Advice {
        val profile = data.profile
        val cycle = CycleCatalog.best(profile)
        val last = data.sessions.maxByOrNull { it.epochDay }
        val program = profile.program(profile.programId)
        val points = buildList {
            if (program == null) cycle?.let { add("Цикл методики под твой профиль: ${it.id}. ${it.note}") }
            addAll(maxLines(data, today))
            addAll(Plateau.lines(data, today))
            feelingLine(last)?.let(::add)
            addAll(if (program == null) techniqueLines(cycle?.strengthMode != false) else programTechniqueLines())
        }
        return Advice(headline(last, profile, today), points)
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

    private fun feelingLine(last: WorkoutSession?): String? = HARD_FEELING.takeIf { last?.feeling == Feeling.Hard }

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
