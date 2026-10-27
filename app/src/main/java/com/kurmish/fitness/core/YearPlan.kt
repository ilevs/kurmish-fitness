package com.kurmish.fitness.core

/**
 * Период годового плана: у каждого своя задача, число повторов, процент от ПМ, темп и отдых.
 *
 * @property title название для экрана.
 * @property weeks сколько недель длится.
 * @property task зачем нужен период, одной фразой.
 */
enum class Period(val title: String, val weeks: Int, val task: String) {
    Preparatory("Подготовительный", weeks = 3, task = "Втянуться и отработать технику на умеренных весах"),
    Hypertrophy("Гипертрофия", weeks = 8, task = "Рост мышц: от 8 повторов, медленное опускание"),
    Deload("Разгрузка", weeks = 1, task = "Восстановиться: вдвое меньше подходов и лёгкий вес"),
    Strength("Сила", weeks = 8, task = "Рост ПМ: тяжёлые многосуставные упражнения на 3–5 повторов"),
    Power("Мощность", weeks = 5, task = "Взрывной подъём средних весов"),
    Metabolic("Метаболический", weeks = 5, task = "Выносливость: больше 12 повторов, короткий отдых"),
    Recovery("Восстановительный", weeks = 3, task = "Лёгкая работа перед новым кругом"),
}

/**
 * Где атлет находится в годовом плане.
 *
 * @property period текущий период.
 * @property week неделя периода, с 1.
 * @property index номер периода в круге, с 0.
 */
data class YearPosition(val period: Period, val week: Int, val index: Int) {
    /** «период «Сила», неделя 2 из 8». */
    val label: String
        get() = "период «${period.title}», неделя $week из ${period.weeks}"
}

/**
 * Годовой план общей тренировки: периоды идут друг за другом, после восстановительного круг повторяется.
 *
 * Подготовка, гипертрофия, разгрузка, сила, мощность (перед ней разгрузки нет), разгрузка, метаболический,
 * восстановительный и разгрузка — около 35 недель. Пока план включён и программа не выбрана, схема общей
 * тренировки берётся из периода, а не из цели в профиле.
 */
object YearPlan {
    private const val WEEK = 7L

    /** Периоды круга по порядку. */
    val order: List<Period> = listOf(
        Period.Preparatory,
        Period.Hypertrophy,
        Period.Deload,
        Period.Strength,
        Period.Power,
        Period.Deload,
        Period.Metabolic,
        Period.Recovery,
        Period.Deload,
    )

    /** Недель в круге. */
    val totalWeeks: Int = order.sumOf { it.weeks }

    /** Положение на день [today] для плана, начатого в [start]; null, если план не начат. */
    fun position(start: Long?, today: Long): YearPosition? {
        if (start == null || today < start) return null
        val week = (((today - start) / WEEK) % totalWeeks).toInt()
        val starts = order.runningFold(0) { sum, period -> sum + period.weeks }
        val index = order.indices.last { starts[it] <= week }
        return YearPosition(order[index], week - starts[index] + 1, index)
    }
}
