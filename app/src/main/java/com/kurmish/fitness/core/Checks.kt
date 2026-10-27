package com.kurmish.fitness.core

import kotlin.math.roundToInt

/**
 * Числовые проверки Дневника для Статистики: каждая находит перекос, который весы пар и силовой баланс
 * не видят, и говорит, что поправить. Все пороги перечислены в docs/reference/stats.md.
 */
object Checks {
    /** Вращения плеча внутрь больше, чем наружу, в столько раз: дальше — перекос. */
    const val ROTATION_EXCESS = 1.5

    /** Рост рабочего веса за неделю больше этой доли — слишком быстро. */
    const val FAST_GROWTH = 0.05

    /** Прибавки меньше этого не считаются: у гантелей один шаг уже больше 5 %. */
    const val FAST_GROWTH_MIN_KG = 5.0

    /** Сколько недель подряд без лёгкой можно тренироваться. */
    const val DELOAD_WEEKS = 8

    /** Лёгкая неделя: тоннаж не больше этой доли от обычного. */
    const val DELOAD_SHARE = 0.7

    /** Сколько раз за [FREQUENCY_DAYS] дней нужно сделать каждую плоскость. */
    const val MIN_FREQUENCY = 2

    /** Окно для проверки частоты. */
    const val FREQUENCY_DAYS = 14

    /** Частоту проверяем, только если за окно было хотя бы столько тренировок. */
    const val FREQUENCY_MIN_SESSIONS = 4

    /** Упражнений в тренировке больше этого — тренировка слишком длинная. */
    const val MAX_EXERCISES = 7

    private const val WEEK = 7
    private const val HALF = 2
    private const val PERCENT = 100
    private const val TENTHS = 10.0

    private val inward = setOf(Movement.HorizontalPush, Movement.HorizontalPull, Movement.VerticalPull)
    private val outward = setOf(Movement.VerticalPush, Movement.Rotation)
    private val planes = listOf(
        Movement.Squat,
        Movement.Hinge,
        Movement.HorizontalPush,
        Movement.HorizontalPull,
        Movement.VerticalPush,
        Movement.VerticalPull,
    )

    /** Все предупреждения на день [today]; пусто, если всё в порядке. */
    fun lines(data: AppData, today: Long): List<String> {
        val recent = data.sessions.filter { today - it.epochDay in 0 until Stats.RECENT_DAYS }
        return listOfNotNull(rotation(recent), deload(data, today), longSession(recent)) +
            baseVolume(recent) + fastGrowth(data, today) + frequency(data, today, recent) + Plateau.lines(data, today)
    }

    /** Жимы от груди, тяги к поясу и подтягивания вращают плечо внутрь; жимы вверх и вращатели — наружу. */
    private fun rotation(recent: List<WorkoutSession>): String? {
        val entries = recent.flatMap { it.entries }
        fun reps(group: Set<Movement>) = entries.filter { it.exercise?.movement in group }.sumOf { it.reps() }
        val inside = reps(inward)
        val outside = reps(outward)
        return if (outside > 0 && inside > outside * ROTATION_EXCESS) {
            "Плечо вращается внутрь в ${ratio(inside, outside)} раза чаще, чем наружу: жимов от груди, тяг " +
                "и подтягиваний много, а жимов вверх, лицевых тяг и подъёмов на грудь мало. Добавь их, чтобы " +
                "беречь плечи."
        } else {
            null
        }
    }

    /** Объём базы: жима больше всего, становой меньше всего — она восстанавливается дольше. */
    private fun baseVolume(recent: List<WorkoutSession>): List<String> {
        val entries = recent.flatMap { it.entries }
        fun reps(exercise: Exercise) = entries.filter { it.exercise == exercise }.sumOf { it.reps() }
        val bench = reps(Exercise.Bench)
        val squat = reps(Exercise.Squat)
        val deadlift = reps(Exercise.Deadlift)
        return listOfNotNull(
            if (squat > 0 && deadlift > squat) {
                "Становой за 4 недели больше, чем приседа ($deadlift и $squat повторов): тяга " +
                    "восстанавливается дольше всех, её объём должен быть самым маленьким."
            } else {
                null
            },
            if (bench > 0 && squat > bench) {
                "Приседа за 4 недели больше, чем жима лёжа ($squat и $bench повторов): обычно жим делают " +
                    "чаще всех, присед реже, становую ещё реже."
            } else {
                null
            },
        )
    }

    /** Рабочий вес вырос больше чем на 5 % за неделю: прибавлять стоит по 2,5–5 %. */
    private fun fastGrowth(data: AppData, today: Long): List<String> {
        val thisWeek = topWeights(data, today - WEEK + 1..today)
        val lastWeek = topWeights(data, today - WEEK * HALF + 1..today - WEEK)
        return thisWeek.mapNotNull { (exercise, now) ->
            val before = lastWeek[exercise] ?: return@mapNotNull null
            if (now - before >= FAST_GROWTH_MIN_KG && now > before * (1 + FAST_GROWTH)) {
                "«${exercise.title}»: рабочий вес вырос за неделю с ${before.kg()} до ${now.kg()} " +
                    "(+${((now / before - 1) * PERCENT).roundToInt()} %). Прибавляй по 2,5–5 % в неделю, " +
                    "иначе цикл быстро упрётся в потолок."
            } else {
                null
            }
        }
    }

    /** 8 недель подряд с тренировками, и ни одна не была лёгкой. */
    private fun deload(data: AppData, today: Long): String? {
        val weeks = Stats.weeks(data, today, DELOAD_WEEKS + 1).dropLast(1)
        if (weeks.any { it.sessions == 0 }) return null
        val usual = weeks.map { it.tonnageKg }.sorted().let { (it[it.size / HALF - 1] + it[it.size / HALF]) / HALF }
        return if (usual > 0 && weeks.none { it.tonnageKg <= usual * DELOAD_SHARE }) {
            "$DELOAD_WEEKS недель подряд без лёгкой недели. Пора разгрузиться: 5–10 дней с вдвое меньшим " +
                "числом подходов и лёгким весом."
        } else {
            null
        }
    }

    /** Каждую плоскость нужно делать хотя бы 2 раза за 2 недели, чтобы она успевала расти. */
    private fun frequency(data: AppData, today: Long, recent: List<WorkoutSession>): List<String> {
        val window = data.sessions.filter { today - it.epochDay in 0 until FREQUENCY_DAYS }
        if (window.size < FREQUENCY_MIN_SESSIONS) return emptyList()
        val done = recent.flatMap { it.entries }.mapNotNull { it.exercise?.movement }.toSet()
        return planes.filter { it in done }.mapNotNull { plane ->
            val times = window.count { session -> session.entries.any { it.exercise?.movement == plane } }
            if (times < MIN_FREQUENCY) {
                "«${plane.title}» за 2 недели ${timesText(times)}: каждую плоскость стоит делать хотя бы " +
                    "раз в неделю, лучше каждые 2–3 дня."
            } else {
                null
            }
        }
    }

    /** В основной части не больше 6 упражнений и кор; иначе последние делаются уставшим. */
    private fun longSession(recent: List<WorkoutSession>): String? {
        val count = recent.maxByOrNull { it.epochDay }?.entries?.distinctBy { it.displayName }?.size ?: 0
        return if (count > MAX_EXERCISES) {
            "В последней тренировке $count упражнений. Хватит шести, по одному на плоскость, и кора: " +
                "тренировка уложится в час, а последние упражнения не будут сделаны на усталости."
        } else {
            null
        }
    }

    /** Самый большой вес каждого упражнения за дни [days]; гравитрон не в счёт, там вес помогает. */
    private fun topWeights(data: AppData, days: LongRange): Map<Exercise, Double> = data.sessions
        .filter { it.epochDay in days }
        .flatMap { it.entries }
        .mapNotNull { entry ->
            val exercise = entry.exercise?.takeUnless(BodyweightLoad::assisted) ?: return@mapNotNull null
            entry.sets.mapNotNull { it.weightKg }.maxOrNull()?.let { exercise to it }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, weights) -> weights.max() }

    /** «1,8» или «4» без лишнего нуля. */
    private fun ratio(a: Int, b: Int): String {
        val tenths = (a * TENTHS / b).roundToInt()
        val whole = tenths / TENTHS.toInt()
        val rest = tenths % TENTHS.toInt()
        return if (rest == 0) "$whole" else "$whole,$rest"
    }

    private fun timesText(count: Int): String = if (count == 0) "ни разу" else "только $count раз"
}
