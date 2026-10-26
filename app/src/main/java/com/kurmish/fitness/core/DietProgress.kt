package com.kurmish.fitness.core

/** Как идёт фаза: скорость снижения веса, сила на дефиците и поправка калорий. */
internal object DietProgress {
    private const val WEEK = 7L
    private const val ADJUST_EVERY_DAYS = 14L
    private const val WATER_DAYS_AFTER_REFEED = 2L
    private const val SLOW_LOSS = 0.5
    private const val FAST_LOSS = 1.0
    private const val ADJUST_STEP = 10
    private const val STRENGTH_RECENT_DAYS = 14L
    private const val STRENGTH_BEFORE_DAYS = 42L
    private const val STRENGTH_DROP = 0.95
    private const val CATEGORY_ONE = 1

    /**
     * Сколько кг в неделю уходит: среднее за прошлую неделю минус среднее за эту.
     * Взвешивания в первые два дня после рефида не считаются: это вода.
     */
    fun weeklyLoss(data: AppData, today: Long): Double? {
        val refeeds = data.profile.diet.refeedDays
        val clean = data.weights.filter { entry -> refeeds.none { entry.epochDay - it in 1..WATER_DAYS_AFTER_REFEED } }
        val thisWeek = clean.filter { today - it.epochDay in 0 until WEEK }.map { it.kg }
        val lastWeek = clean.filter { today - it.epochDay in WEEK until WEEK * 2 }.map { it.kg }
        return if (thisWeek.isEmpty() || lastWeek.isEmpty()) null else lastWeek.average() - thisWeek.average()
    }

    /** Лучшая оценка ПМ в базовом движении за две недели ниже, чем за четыре недели до них. */
    fun strengthDropping(sessions: List<WorkoutSession>, today: Long): Boolean {
        val recent = best(sessions, today, 0 until STRENGTH_RECENT_DAYS)
        val before = best(sessions, today, STRENGTH_RECENT_DAYS until STRENGTH_BEFORE_DAYS)
        return recent.any { (lift, kg) -> before[lift]?.let { kg < it * STRENGTH_DROP } == true }
    }

    /** Поправка калорий на дефиците; не чаще раза в две недели, чтобы успел проявиться результат. */
    fun suggestion(context: DietContext, rate: Double?, dropping: Boolean): CalorieSuggestion? {
        val since = maxOf(context.start, context.settings.correctedEpochDay ?: context.start)
        val settled = context.today - since >= ADJUST_EVERY_DAYS
        if (context.settings.phase != DietPhase.Deficit || rate == null || !settled) return null
        val leanAndTraining = context.category == CATEGORY_ONE && context.intense
        return when {
            rate < SLOW_LOSS -> CalorieSuggestion(
                -ADJUST_STEP,
                "Вес уходит медленнее 0,5 кг в неделю: убавь калории на 10 % или добавь столько же активности.",
            )
            rate > FAST_LOSS && leanAndTraining && dropping -> CalorieSuggestion(
                ADJUST_STEP,
                "Вес уходит быстрее 1 кг в неделю и падает сила: прибавь калории на 10 %, чтобы не терять мышцы.",
            )
            else -> null
        }
    }

    private fun best(sessions: List<WorkoutSession>, today: Long, days: LongRange): Map<Exercise, Double> = sessions
        .filter { today - it.epochDay in days }
        .flatMap { session -> session.entries }
        .flatMap { entry ->
            val lift = entry.exercise?.takeIf { it.isMainLift } ?: return@flatMap emptyList()
            entry.sets.mapNotNull { set ->
                set.weightKg?.let { Strength.estimateMax(it, set.reps) }?.let { lift to it }
            }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.max() }
}
