package com.kurmish.fitness.core

import kotlinx.serialization.Serializable

/** Оценка теста подвижности, от лучшей к худшей. */
@Serializable
enum class Grade(val title: String) {
    Excellent("Отличная"),
    Good("Хорошая"),
    Average("Средняя"),
    BelowAverage("Ниже среднего"),
    Poor("Плохая"),
    ;

    /** Результат неудовлетворительный: сложные варианты движения пока рано делать. */
    val weak: Boolean
        get() = this >= BelowAverage
}

/**
 * Тесты подвижности плечевого пояса перед первой программой; null — тест не проходили.
 *
 * @property chest тест А: сидя развести прямые руки назад. Определяет вариант горизонтального жима.
 * @property overhead тест Б: лёжа с прижатой поясницей завести прямые руки за голову. Определяет, можно ли
 *   вертикальные тяги.
 */
@Serializable
data class ShoulderTests(val chest: Grade? = null, val overhead: Grade? = null)

/**
 * Первичное тестирование: по подвижности плеч подбирает упражнения на первый цикл, по весам оценивает ПМ
 * и силовой баланс.
 */
object FitnessTest {
    /** Упражнения, которые просят вес и повторы в анкете. */
    val lifts: List<Exercise> = listOf(
        Exercise.Squat,
        Exercise.Bench,
        Exercise.Deadlift,
        Exercise.OverheadPress,
        Exercise.ChinUp,
        Exercise.PowerClean,
    )

    /** Растяжка между подходами тяг, пока плечи закрыты. */
    const val STRETCH = "Между подходами тяги растягивай грудные мышцы по 15–20 секунд"

    private const val PRESS_SLOT = "Жим лёжа"
    private const val PULL_SLOT = "Подтягивания"
    private const val PERCENT = 100
    private const val TOLERANCE = 0.9
    private const val DEADLIFT_TO_SQUAT = 1.5

    /** Горизонтальный жим по тесту А: null — подойдёт любой, включая жим штанги лёжа. */
    fun pressFor(grade: Grade?): Exercise? = when (grade) {
        null, Grade.Excellent, Grade.Good -> null
        Grade.Average -> Exercise.DumbbellPress
        Grade.BelowAverage, Grade.Poor -> Exercise.MachineChestPress
    }

    /** Вертикальная тяга по тесту Б: null — подойдут подтягивания; при плохом результате тяга к поясу. */
    fun pullFor(grade: Grade?): Exercise? = when (grade) {
        null, Grade.Excellent, Grade.Good -> null
        Grade.Average -> Exercise.LatPulldown
        Grade.BelowAverage, Grade.Poor -> Exercise.SeatedCableRow
    }

    /** Профиль с результатами тестов [tests]: жим и подтягивания на цикл заменяются более простыми. */
    fun applyTo(profile: Profile, tests: ShoulderTests): Profile = profile
        .copy(shoulders = tests)
        .choose(PRESS_SLOT, pressFor(tests.chest))
        .choose(PULL_SLOT, pullFor(tests.overhead))

    /**
     * Запись тестирования в Дневник за день [today]: по ней приложение оценит ПМ. [results] — упражнение →
     * вес и повторы лучшего подхода; у подтягиваний вес — отягощение на поясе.
     */
    fun session(results: Map<Exercise, Pair<Double?, Int>>, today: Long): WorkoutSession? {
        val entries = lifts.mapNotNull { lift ->
            results[lift]?.takeIf { (_, reps) -> reps > 0 }?.let { (kg, reps) ->
                ExerciseEntry(lift, sets = listOf(SetGroup(kg, reps)))
            }
        }
        return if (entries.isEmpty()) {
            null
        } else {
            WorkoutSession(id = 0, epochDay = today, rawText = "Первичное тестирование", entries = entries)
        }
    }

    /**
     * Силовой баланс: ПМ жима лёжа ≈ ПМ подтягиваний обратным хватом с весом (вместе с собственным) ≈ ПМ
     * подъёма штанги на грудь, становая ≥ 1,5 приседа. Первые два равенства берегут плечи, последнее —
     * колени. Возвращает советы; пустой список, если перекосов нет или данных мало.
     */
    fun balance(data: AppData, today: Long): List<String> {
        val bench = data.currentMax(Exercise.Bench, today)
        val chinUp = data.bodyweightMax(Exercise.ChinUp, today)
        val clean = data.currentMax(Exercise.PowerClean, today)
        val squat = data.currentMax(Exercise.Squat, today)
        val deadlift = data.currentMax(Exercise.Deadlift, today)
        return listOfNotNull(
            lag(bench, chinUp, "Подтягивания обратным хватом с весом", "вертикальных тяг"),
            lag(bench, clean, "Подъём на грудь", "подъёмов на грудь и вращения плеча наружу"),
            if (squat != null && deadlift != null && deadlift < squat * DEADLIFT_TO_SQUAT) {
                "Становая слабее полутора приседов (${percent(deadlift, squat * DEADLIFT_TO_SQUAT)} %): " +
                    "добавь тазовых упражнений — колени держит задняя поверхность бедра"
            } else {
                null
            },
        )
    }

    private fun lag(bench: Double?, other: Double?, title: String, more: String): String? =
        if (bench != null && other != null && other < bench * TOLERANCE) {
            "$title отстаёт от жима лёжа (${percent(other, bench)} %): больше $more, чтобы беречь плечи"
        } else {
            null
        }

    private fun percent(value: Double, of: Double): Int = (value / of * PERCENT).toInt()
}
