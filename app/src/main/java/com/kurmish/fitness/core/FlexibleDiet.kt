package com.kurmish.fitness.core

/**
 * Гибкий подход к питанию: дефицит от поддерживающих калорий, белок по сухой массе, рефиды
 * и свободные приёмы пищи по категории, перерывы в диете, поправка калорий по весу.
 *
 * Все правила и числа перечислены в docs/reference/flexible-diet.md.
 */
object FlexibleDiet {
    private const val LB_PER_KG = 2.2046
    private const val KCAL_PROTEIN = 4
    private const val KCAL_CARB = 4
    private const val KCAL_FAT = 9
    private const val MIN_CARBS_OFF_DEFICIT = 100
    private const val MIN_FAT = 30
    private const val REFEED_MAX_FAT = 50
    private const val PERCENT = 100.0
    private const val CATEGORY_ONE = 1
    private const val CATEGORY_TWO = 2
    private const val CATEGORY_THREE = 3

    // Границы категорий по проценту жира: до первой — категория 1, до второй — 2, выше — 3.
    private val categoryLimits = mapOf(Sex.Male to (15.0 to 25.0), Sex.Female to (24.0 to 34.0))

    // Поддерживающие калории на фунт веса: женщинам нижняя граница, мужчинам верхняя.
    private val kcalPerLb = mapOf(
        Activity.Sedentary to (10.0 to 11.0),
        Activity.Light to (11.0 to 12.0),
        Activity.Moderate to (12.0 to 13.0),
        Activity.High to (14.0 to 15.0),
        Activity.Extreme to (18.0 to 19.0),
    )

    // Углеводы сверх обязательных 100 г: граммы на фунт сухой массы.
    private val carbsPerLeanLb = mapOf(
        Activity.Sedentary to 0.0,
        Activity.Light to 0.5,
        Activity.Moderate to 1.0,
        Activity.High to 1.25,
        Activity.Extreme to 1.5,
    )

    private const val PROTEIN_PER_LEAN_LB_STRENGTH = 1.0
    private const val PROTEIN_PER_LEAN_LB_LOW = 0.75

    // Недель сброса жира до перерыва в диете по категориям.
    private val weeksBeforeBreak = mapOf(CATEGORY_ONE to 4..6, CATEGORY_TWO to 6..12, CATEGORY_THREE to 12..16)

    // Оценка процента жира по индексу массы тела и возрасту (для взрослых).
    private const val BMI_FACTOR = 1.2
    private const val AGE_FACTOR = 0.23
    private const val MALE_FACTOR = 10.8
    private const val BASE_SHIFT = 5.4
    private const val CM_PER_M = 100.0

    /** Чего не хватает в профиле для расчёта; null, если хватает всего или питание не отслеживается. */
    fun missing(data: AppData): String? = when {
        data.profile.diet.phase == DietPhase.Off -> null
        data.currentWeight() == null -> "Введи собственный вес в профиле или взвесься на этом экране."
        bodyFat(data) == null -> "Введи процент жира или рост и возраст для оценки: Профиль, раздел «Питание»."
        else -> null
    }

    /** Питание на день [today]; null, если фаза не выбрана или данных не хватает. */
    fun day(data: AppData, today: Long): DietDay? {
        val settings = data.profile.diet
        val weight = data.currentWeight()
        val fat = bodyFat(data)
        if (settings.phase == DietPhase.Off || weight == null || fat == null) return null
        val context = DietContext(
            settings = settings,
            category = category(settings.sex, fat.first),
            intense = data.profile.activityLevel() >= Activity.Moderate,
            leanKg = weight * (1 - fat.first / PERCENT),
            start = settings.phaseStartEpochDay ?: today,
            today = today,
        )
        val maintenance = maintenanceKcal(data.profile, weight)
        val base = maintenanceMacros(data.profile.activityLevel(), context.leanKg, maintenance)
        val deficit = settings.phase == DietPhase.Deficit
        val target = if (deficit) deficitMacros(settings, base) else base
        val refeed = if (deficit) Refeeds.next(context) else null
        val rate = DietProgress.weeklyLoss(data, today)
        val dropping = DietProgress.strengthDropping(data.sessions, today)
        val day = DietDay(
            phase = settings.phase,
            category = context.category,
            intenseTraining = context.intense,
            bodyFatPercent = fat.first,
            bodyFatEstimated = fat.second,
            leanKg = context.leanKg,
            maintenanceKcal = maintenance,
            target = target,
            refeedTarget = refeed?.takeIf { it.length == RefeedLength.OneDay }?.let { refeedMacros(target, it) },
            refeed = refeed,
            daysInPhase = today - context.start,
            rateKgPerWeek = rate,
            strengthDropping = dropping,
            suggestion = DietProgress.suggestion(context, rate, dropping),
            points = emptyList(),
            trainingNotes = emptyList(),
        )
        return day.copy(points = DietAdvice.points(day), trainingNotes = DietAdvice.trainingNotes(day))
    }

    /** Диетическая категория по проценту жира [fat]: 1 — стройный, 3 — много лишнего жира. */
    fun category(sex: Sex, fat: Double): Int {
        val (lean, middle) = categoryLimits.getValue(sex)
        return when {
            fat <= lean -> CATEGORY_ONE
            fat <= middle -> CATEGORY_TWO
            else -> CATEGORY_THREE
        }
    }

    /** Поддерживающие калории при весе [weightKg]. */
    fun maintenanceKcal(profile: Profile, weightKg: Double): Int {
        val (low, high) = kcalPerLb.getValue(profile.activityLevel())
        val perLb = if (profile.diet.sex == Sex.Female) low else high
        return (weightKg * LB_PER_KG * perLb).toInt()
    }

    /** Сколько недель сбрасывать жир до перерыва в диете. */
    fun weeksBeforeBreak(category: Int): IntRange = weeksBeforeBreak.getValue(category)

    /** Процент жира и признак, что он оценён по росту и возрасту, а не введён. */
    private fun bodyFat(data: AppData): Pair<Double, Boolean>? {
        val settings = data.profile.diet
        val entered = settings.bodyFatPercent
        val weight = data.currentWeight()
        val height = settings.heightCm?.takeIf { it > 0 }
        val age = settings.age
        val estimate = if (weight != null && height != null && age != null) {
            val bmi = weight / (height / CM_PER_M).let { it * it }
            val male = if (settings.sex == Sex.Male) MALE_FACTOR else 0.0
            BMI_FACTOR * bmi + AGE_FACTOR * age - male - BASE_SHIFT
        } else {
            null
        }
        return entered?.let { it to false } ?: estimate?.let { it to true }
    }

    private fun maintenanceMacros(activity: Activity, leanKg: Double, kcal: Int): Macros {
        val leanLb = leanKg * LB_PER_KG
        val proteinPerLb = if (activity >= Activity.Moderate) {
            PROTEIN_PER_LEAN_LB_STRENGTH
        } else {
            PROTEIN_PER_LEAN_LB_LOW
        }
        val protein = (leanLb * proteinPerLb).toInt()
        val carbs = MIN_CARBS_OFF_DEFICIT + (leanLb * carbsPerLeanLb.getValue(activity)).toInt()
        return split(kcal, protein, carbs, MIN_CARBS_OFF_DEFICIT)
    }

    /** Дефицит снимается с углеводов, белок не трогаем; жиры не ниже минимума. */
    private fun deficitMacros(settings: DietSettings, base: Macros): Macros {
        val percent = (PERCENT - settings.deficitPercent) * (PERCENT + settings.calorieCorrectionPercent) / PERCENT
        val kcal = (base.kcal * percent / PERCENT).toInt()
        val carbs = (kcal - base.proteinG * KCAL_PROTEIN - base.fatG * KCAL_FAT) / KCAL_CARB
        return if (carbs >= 0) Macros(kcal, base.proteinG, carbs, base.fatG) else split(kcal, base.proteinG, 0, 0)
    }

    /** Добирает калории жирами; если жиров выходит меньше минимума, урезает углеводы до [carbFloor]. */
    private fun split(kcal: Int, protein: Int, carbs: Int, carbFloor: Int): Macros {
        val fat = (kcal - protein * KCAL_PROTEIN - carbs * KCAL_CARB) / KCAL_FAT
        if (fat >= MIN_FAT) return Macros(kcal, protein, carbs, fat)
        val fewerCarbs = maxOf(carbFloor, (kcal - protein * KCAL_PROTEIN - MIN_FAT * KCAL_FAT) / KCAL_CARB)
        val total = protein * KCAL_PROTEIN + fewerCarbs * KCAL_CARB + MIN_FAT * KCAL_FAT
        return Macros(maxOf(kcal, total), protein, fewerCarbs, MIN_FAT)
    }

    /** День рефида: белок как обычно, углеводы по нижней границе, жиров не больше 50 г. */
    private fun refeedMacros(target: Macros, refeed: Refeed): Macros {
        val carbs = refeed.carbsG.first
        val fat = minOf(target.fatG, REFEED_MAX_FAT)
        val kcal = target.proteinG * KCAL_PROTEIN + carbs * KCAL_CARB + fat * KCAL_FAT
        return Macros(kcal, target.proteinG, carbs, fat)
    }
}

/**
 * Исходные данные расчёта на день.
 *
 * @property settings настройки питания.
 * @property category диетическая категория.
 * @property intense тренируется интенсивно.
 * @property leanKg сухая масса.
 * @property start начало фазы.
 * @property today сегодняшний день.
 */
internal data class DietContext(
    val settings: DietSettings,
    val category: Int,
    val intense: Boolean,
    val leanKg: Double,
    val start: Long,
    val today: Long,
)
