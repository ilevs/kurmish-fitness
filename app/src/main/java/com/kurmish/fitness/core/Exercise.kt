package com.kurmish.fitness.core

import kotlinx.serialization.Serializable

/**
 * Упражнение из каталога.
 *
 * @property title название для экрана и озвучки.
 * @property bodyweight упражнение с собственным весом: «3x10» читается как подходы × повторы.
 * @property synonyms как упражнение называют в свободной речи, без «ё» и в нижнем регистре.
 */
@Serializable
enum class Exercise(val title: String, val bodyweight: Boolean, val synonyms: List<String>) {
    Squat("Присед", bodyweight = false, synonyms = listOf("присед", "приседания", "приседание", "присяд")),
    Bench("Жим лёжа", bodyweight = false, synonyms = listOf("жим лежа", "жим штанги лежа", "жим")),
    Deadlift(
        "Становая тяга",
        bodyweight = false,
        synonyms = listOf("становая тяга", "тяга становая", "становая", "тяга классика", "тяга сумо", "тяга"),
    ),
    OverheadPress(
        "Жим стоя",
        bodyweight = false,
        synonyms = listOf("жим стоя", "армейский жим", "армейский", "жим над головой"),
    ),
    MidGripBench("Жим средним хватом", bodyweight = false, synonyms = listOf("жим средним хватом", "средний хват")),
    CloseGripBench("Жим узким хватом", bodyweight = false, synonyms = listOf("жим узким хватом", "узкий жим", "узким")),
    DumbbellPress("Жим гантелей", bodyweight = false, synonyms = listOf("жим гантелей", "жим гантелями", "гантели")),
    FrenchPress("Французский жим", bodyweight = false, synonyms = listOf("французский жим", "французский")),
    BicepsCurl(
        "Подъём на бицепс",
        bodyweight = false,
        synonyms = listOf("подъем на бицепс", "подъем штанги на бицепс", "бицепс"),
    ),
    StiffLegDeadlift(
        "Тяга на прямых ногах",
        bodyweight = false,
        synonyms = listOf("тяга на прямых ногах", "румынская тяга", "румынка", "мертвая тяга"),
    ),
    BentOverRow("Тяга в наклоне", bodyweight = false, synonyms = listOf("тяга в наклоне", "тяга штанги в наклоне")),
    PullUp("Подтягивания", bodyweight = true, synonyms = listOf("подтягивания", "подтягивание", "подтягивался")),
    Dips("Брусья", bodyweight = true, synonyms = listOf("отжимания на брусьях", "брусья", "брусьях")),
    PushUp("Отжимания", bodyweight = true, synonyms = listOf("отжимания", "отжимание", "отжимался")),
    Abs("Пресс", bodyweight = true, synonyms = listOf("пресс", "скручивания")),
    Hyperextension("Гиперэкстензия", bodyweight = true, synonyms = listOf("гиперэкстензия", "гиперы")),
    ;

    /** Соревновательное движение, по которому ведётся предельный максимум (ПМ). */
    val isMainLift: Boolean
        get() = this == Squat || this == Bench || this == Deadlift
}
