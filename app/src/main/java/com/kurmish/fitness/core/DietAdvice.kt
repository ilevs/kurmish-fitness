package com.kurmish.fitness.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** Тексты советов по питанию и по тому, как фаза питания меняет тренировки. */
internal object DietAdvice {
    private const val WEEK = 7L
    private const val BREAK_DAYS = 14L
    private const val CATEGORY_THREE = 3
    private const val NO_REFEED_WEEKS = 6
    private const val TENTH = 10
    private const val ROUND = 10.0

    /** Советы по питанию на сегодня. */
    fun points(day: DietDay): List<String> = when (day.phase) {
        DietPhase.Deficit -> deficitPoints(day)
        DietPhase.Break -> breakPoints(day)
        DietPhase.Maintenance -> maintenancePoints(day)
        DietPhase.Off -> emptyList()
    }

    /** Как фаза меняет тренировки: эти пункты попадают в советы тренера. */
    fun trainingNotes(day: DietDay): List<String> = when (day.phase) {
        DietPhase.Deficit -> buildList {
            add(
                "Идёт сброс жира: держи рабочие веса и технику, за новыми максимумами не гонись, " +
                    "процент корректировки 0–0,5 %.",
            )
            if (day.strengthDropping) {
                add(
                    "Максимумы за две недели ниже прежних: на дефиците это признак недоедания. Веса не повышай, " +
                        "сделай рефид по плану и проверь калории.",
                )
            }
            if (day.suggestion?.percent?.let { it < 0 } == true) {
                add(
                    "Вместо урезания еды можно добавить активности примерно на ${day.target.kcal / TENTH} ккал: " +
                        "спокойное кардио после тренировки или в другие дни.",
                )
            }
        }
        DietPhase.Break -> listOf(
            "Перерыв в диете: калорий хватает на восстановление, тренируйся в обычном режиме и поднимай веса по плану.",
        )
        DietPhase.Maintenance, DietPhase.Off -> emptyList()
    }

    /** Пояснение к плану на сегодня, если сегодня рефид. */
    fun planNote(day: DietDay): String? {
        val refeed = day.refeed?.takeIf { it.due || it.doneToday } ?: return null
        return when (refeed.length) {
            RefeedLength.OneDay ->
                "Сегодня рефид на весь день: это лучший день для самой тяжёлой тренировки, удобнее утром."
            RefeedLength.FiveHours ->
                "Сегодня вечером рефид: поставь тренировку внутри пятичасового окна. Углеводный приём пищи " +
                    "за час до тренировки, второй сразу после, третий через два часа."
        }
    }

    private fun deficitPoints(day: DietDay): List<String> = buildList {
        val week = day.daysInPhase / WEEK + 1
        add("Сброс жира, неделя $week. Поддерживающие калории около ${day.maintenanceKcal} ккал.")
        add("Белок не урезай: 20–40 г в каждый приём пищи. Овощи без ограничений, Омега-3 каждый день.")
        day.refeed?.let { addAll(refeedPoints(day, it)) }
        add(
            "Свободных приёмов пищи: ${Refeeds.freeMealsPerWeek(day.refeed)} в неделю. Это обычная " +
                "вкусная еда без подсчёта, а не повод объесться.",
        )
        breakPoint(day)?.let(::add)
        rateLine(day)?.let(::add)
    }

    private fun refeedPoints(day: DietDay, refeed: Refeed): List<String> {
        val carbs = "углеводы ${refeed.carbsG.first}–${refeed.carbsG.last} г (начни с ${refeed.carbsG.first})"
        val window = when (refeed.length) {
            RefeedLength.FiveHours -> "пять часов вечером, три приёма пищи"
            RefeedLength.OneDay -> "с завтрака до ужина, еда каждые 2–3 часа"
        }
        val schedule = "Рефид ${refeed.length.title} раз в ${days(refeed.everyDays)}" +
            refeed.alternative?.let { " ($it)" }.orEmpty() + "."
        val now = when {
            refeed.doneToday -> "Рефид сегодня отмечен. Пару дней вес будет выше: это вода и гликоген, а не жир."
            refeed.due ->
                "Сегодня рефид: $window; $carbs, жиров не больше 50 г, белок как обычно. " +
                    "Основа: хлеб, рис, макароны, картофель, пара фруктов; сладкого совсем немного."
            day.category == CATEGORY_THREE && day.daysInPhase < NO_REFEED_WEEKS * WEEK ->
                "Первые $NO_REFEED_WEEKS недель без рефидов: пусть закрепятся новые привычки."
            else -> null
        }
        return listOfNotNull(schedule, now)
    }

    private fun breakPoint(day: DietDay): String? {
        val weeks = FlexibleDiet.weeksBeforeBreak(day.category)
        val done = day.daysInPhase / WEEK
        return when {
            done >= weeks.last -> "Сброс жира идёт уже $done нед.: пора сделать перерыв в диете на две недели."
            done >= weeks.first ->
                "Можно сделать перерыв в диете на две недели: для твоей категории он нужен " +
                    "каждые ${weeks.first}–${weeks.last} недель."
            else -> "Перерыв в диете понадобится через ${weeks.first - done}–${weeks.last - done} нед."
        }
    }

    private fun breakPoints(day: DietDay): List<String> = buildList {
        val left = BREAK_DAYS - day.daysInPhase
        if (left > 0) {
            add("Перерыв в диете, осталось $left дн. Калории на уровне поддержки, углеводов не меньше 100 г в день.")
        } else {
            add("Две недели перерыва прошли: можно возвращаться к сбросу жира.")
        }
        add("Вес в начале перерыва поднимется на 1–1,5 кг: это гликоген и вода. Следи, чтобы не росло дальше.")
        rateLine(day)?.let(::add)
    }

    private fun maintenancePoints(day: DietDay): List<String> = listOfNotNull(
        "Поддержание: вес держи в коридоре ±1,5–2 кг и взвешивайся регулярно.",
        "Если вес ползёт вверх, убери 100–200 ккал: 25–50 г углеводов или 10–20 г жиров. Белок и овощи не трогай.",
        "Один-два свободных приёма пищи в неделю без подсчёта.",
        rateLine(day),
    )

    private fun rateLine(day: DietDay): String? {
        val rate = day.rateKgPerWeek ?: return null
        val kg = abs(rate).let { (it * ROUND).roundToInt() / ROUND }.toString().replace('.', ',')
        return if (rate >= 0) "За неделю вес снизился на $kg кг." else "За неделю вес вырос на $kg кг."
    }

    private fun days(range: IntRange): String =
        if (range.first == range.last) "${range.first} дн." else "${range.first}–${range.last} дн."
}
