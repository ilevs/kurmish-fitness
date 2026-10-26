package com.kurmish.fitness.core

import kotlinx.serialization.Serializable

/** Пол: от него зависят границы диетических категорий и оценка калорий. */
@Serializable
enum class Sex(val title: String) {
    Male("Мужчина"),
    Female("Женщина"),
}

/**
 * Уровень активности за неделю: от него зависят поддерживающие калории, белок и углеводы.
 *
 * @property title название.
 * @property hint что под ним понимается.
 */
@Serializable
enum class Activity(val title: String, val hint: String) {
    Sedentary("Сидячий", "работа за столом, тренировок нет"),
    Light("Лёгкий", "спокойное кардио"),
    Moderate("Умеренный", "силовые или интенсивное кардио"),
    High("Высокий", "силовые 3 раза в неделю и кардио"),
    Extreme("Очень высокий", "больше двух часов тренировок в день"),
}

/** Фаза питания: под неё подстраиваются калории и советы по тренировкам. */
@Serializable
enum class DietPhase(val title: String) {
    /** Питание не отслеживается. */
    Off("Не слежу"),

    /** Сброс жира: дефицит калорий, рефиды, свободные приёмы пищи. */
    Deficit("Сброс жира"),

    /** Полный перерыв в диете: две недели на поддерживающих калориях. */
    Break("Перерыв в диете"),

    /** Поддержание веса без срока. */
    Maintenance("Поддержание"),
}

/**
 * Настройки питания в профиле.
 *
 * @property phase текущая фаза.
 * @property phaseStartEpochDay с какого дня идёт фаза.
 * @property sex пол.
 * @property bodyFatPercent процент жира; null — оценить по росту и возрасту.
 * @property heightCm рост для оценки процента жира.
 * @property age возраст для оценки процента жира.
 * @property activity уровень активности.
 * @property deficitPercent на сколько процентов меньше поддерживающих калорий есть при сбросе жира.
 * @property calorieCorrectionPercent поправка калорий на дефиците по скорости снижения веса.
 * @property correctedEpochDay когда поправку меняли последний раз.
 * @property refeedDays дни, когда был рефид.
 */
@Serializable
data class DietSettings(
    val phase: DietPhase = DietPhase.Off,
    val phaseStartEpochDay: Long? = null,
    val sex: Sex = Sex.Male,
    val bodyFatPercent: Double? = null,
    val heightCm: Double? = null,
    val age: Int? = null,
    val activity: Activity = Activity.Moderate,
    val deficitPercent: Int = DEFAULT_DEFICIT,
    val calorieCorrectionPercent: Int = 0,
    val correctedEpochDay: Long? = null,
    val refeedDays: List<Long> = emptyList(),
) {
    /** Значения по умолчанию. */
    companion object {
        /** Середина рекомендованного дефицита 10–20 %. */
        const val DEFAULT_DEFICIT = 15

        /** Допустимые варианты дефицита. */
        val DEFICIT_OPTIONS = listOf(10, 15, 20)
    }
}

/**
 * Вес тела за день.
 *
 * @property epochDay дата.
 * @property kg вес.
 */
@Serializable
data class WeightEntry(val epochDay: Long, val kg: Double)

/**
 * Калории и БЖУ на день.
 *
 * @property kcal калории.
 * @property proteinG белок, г.
 * @property carbsG углеводы, г.
 * @property fatG жиры, г.
 */
data class Macros(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int) {
    /** «2100 ккал · Б 150 · Ж 60 · У 240». */
    val label: String
        get() = "$kcal ккал · Б $proteinG · Ж $fatG · У $carbsG"
}

/** Длительность рефида. */
enum class RefeedLength(val title: String) {
    /** Пять часов вечером, на три приёма пищи. */
    FiveHours("5 часов"),

    /** День с завтрака до ужина. */
    OneDay("1 день"),
}

/**
 * Рефид: запланированный подъём углеводов.
 *
 * @property length длительность.
 * @property everyDays раз во сколько дней.
 * @property nextEpochDay когда следующий.
 * @property due пора делать сегодня.
 * @property doneToday сегодня уже отмечен.
 * @property carbsG сколько углеводов за рефид: начинать с нижней границы.
 * @property alternative другой вариант из той же категории.
 */
data class Refeed(
    val length: RefeedLength,
    val everyDays: IntRange,
    val nextEpochDay: Long,
    val due: Boolean,
    val doneToday: Boolean,
    val carbsG: IntRange,
    val alternative: String?,
)

/**
 * Предложение поправить калории по скорости снижения веса.
 *
 * @property percent на сколько процентов изменить калории.
 * @property reason почему.
 */
data class CalorieSuggestion(val percent: Int, val reason: String)

/**
 * Питание на сегодня по гибкому подходу.
 *
 * @property phase фаза.
 * @property category диетическая категория по проценту жира: 1 — стройный, 3 — лишний жир.
 * @property intenseTraining тренируется интенсивно (силовые 2+ раза в неделю).
 * @property bodyFatPercent процент жира.
 * @property bodyFatEstimated процент жира оценён по росту и возрасту, а не введён.
 * @property leanKg сухая масса.
 * @property maintenanceKcal поддерживающие калории.
 * @property target калории и БЖУ на обычный день фазы.
 * @property refeedTarget калории и БЖУ, если сегодня рефид на целый день.
 * @property refeed рефид; null вне сброса жира.
 * @property daysInPhase сколько дней идёт фаза.
 * @property rateKgPerWeek скорость снижения веса за последнюю неделю; null, если взвешиваний мало.
 * @property strengthDropping рабочие максимумы за две недели заметно ниже прежних.
 * @property suggestion поправка калорий; null, если менять ничего не нужно.
 * @property points советы по питанию.
 * @property trainingNotes как фаза меняет тренировки.
 */
data class DietDay(
    val phase: DietPhase,
    val category: Int,
    val intenseTraining: Boolean,
    val bodyFatPercent: Double,
    val bodyFatEstimated: Boolean,
    val leanKg: Double,
    val maintenanceKcal: Int,
    val target: Macros,
    val refeedTarget: Macros?,
    val refeed: Refeed?,
    val daysInPhase: Long,
    val rateKgPerWeek: Double?,
    val strengthDropping: Boolean,
    val suggestion: CalorieSuggestion?,
    val points: List<String>,
    val trainingNotes: List<String>,
)

/** Последний известный вес тела: взвешивание или вес из профиля. */
fun AppData.currentWeight(): Double? = weights.maxByOrNull { it.epochDay }?.kg ?: profile.bodyweightKg

/** Записывает вес за день [epochDay], заменяя прежнее взвешивание за этот день. */
fun AppData.withWeight(epochDay: Long, kg: Double): AppData = copy(
    weights = (weights.filter { it.epochDay != epochDay } + WeightEntry(epochDay, kg)).sortedBy { it.epochDay },
    profile = profile.copy(bodyweightKg = kg),
)

/** Отмечает рефид в день [epochDay]. */
fun AppData.withRefeed(epochDay: Long): AppData {
    val diet = profile.diet
    return if (epochDay in diet.refeedDays) {
        this
    } else {
        copy(profile = profile.copy(diet = diet.copy(refeedDays = diet.refeedDays + epochDay)))
    }
}

/** Принимает поправку калорий [percent] в день [epochDay]. */
fun AppData.withCalorieCorrection(percent: Int, epochDay: Long): AppData {
    val diet = profile.diet
    val corrected = diet.copy(
        calorieCorrectionPercent = diet.calorieCorrectionPercent + percent,
        correctedEpochDay = epochDay,
    )
    return copy(profile = profile.copy(diet = corrected))
}

/** Меняет фазу с дня [epochDay]: поправка калорий начинается заново. */
fun DietSettings.withPhase(phase: DietPhase, epochDay: Long): DietSettings = if (phase == this.phase) {
    this
} else {
    copy(phase = phase, phaseStartEpochDay = epochDay, calorieCorrectionPercent = 0, correctedEpochDay = null)
}
