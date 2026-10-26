package com.kurmish.fitness.core

import kotlinx.serialization.Serializable

/**
 * Упражнение из базы.
 *
 * Новые упражнения добавляются в конец: Дневник хранит упражнение по имени константы.
 *
 * @property title название для экрана и озвучки.
 * @property bodyweight упражнение с собственным весом: «3x10» читается как подходы × повторы.
 * @property movement тип движения: упражнения одного типа взаимозаменяемы.
 * @property level сложность внутри типа движения, от 1 (тренажёр, стабильная опора) до 6
 *   (одна нога или рука, свободный вес, большая амплитуда).
 * @property synonyms как упражнение называют в свободной речи, без «ё» и в нижнем регистре.
 */
@Serializable
enum class Exercise(
    val title: String,
    val bodyweight: Boolean,
    val movement: Movement,
    val level: Int,
    val synonyms: List<String>,
) {
    Squat(
        "Присед",
        bodyweight = false,
        movement = Movement.Squat,
        level = 4,
        synonyms = listOf("присед", "приседания", "приседание", "присяд"),
    ),
    Bench(
        "Жим лёжа",
        bodyweight = false,
        movement = Movement.HorizontalPush,
        level = 4,
        synonyms = listOf("жим лежа", "жим штанги лежа", "жим"),
    ),
    Deadlift(
        "Становая тяга",
        bodyweight = false,
        movement = Movement.Hinge,
        level = 5,
        synonyms = listOf("становая тяга", "тяга становая", "становая", "тяга классика", "тяга сумо", "тяга"),
    ),
    OverheadPress(
        "Жим стоя",
        bodyweight = false,
        movement = Movement.VerticalPush,
        level = 4,
        synonyms = listOf("жим стоя", "армейский жим", "армейский", "жим над головой"),
    ),
    MidGripBench(
        "Жим средним хватом",
        bodyweight = false,
        movement = Movement.HorizontalPush,
        level = 4,
        synonyms = listOf("жим средним хватом", "средний хват"),
    ),
    CloseGripBench(
        "Жим узким хватом",
        bodyweight = false,
        movement = Movement.HorizontalPush,
        level = 4,
        synonyms = listOf("жим узким хватом", "узкий жим", "узким"),
    ),
    DumbbellPress(
        "Жим гантелей",
        bodyweight = false,
        movement = Movement.HorizontalPush,
        level = 3,
        synonyms = listOf("жим гантелей", "жим гантелями", "гантели"),
    ),
    FrenchPress(
        "Французский жим",
        bodyweight = false,
        movement = Movement.Arms,
        level = 2,
        synonyms = listOf("французский жим", "французский"),
    ),
    BicepsCurl(
        "Подъём на бицепс",
        bodyweight = false,
        movement = Movement.Arms,
        level = 2,
        synonyms = listOf("подъем на бицепс", "подъем штанги на бицепс", "бицепс"),
    ),
    StiffLegDeadlift(
        "Тяга на прямых ногах",
        bodyweight = false,
        movement = Movement.Hinge,
        level = 4,
        synonyms = listOf("тяга на прямых ногах", "румынская тяга", "румынка", "мертвая тяга"),
    ),
    BentOverRow(
        "Тяга в наклоне",
        bodyweight = false,
        movement = Movement.HorizontalPull,
        level = 4,
        synonyms = listOf("тяга в наклоне", "тяга штанги в наклоне"),
    ),
    PullUp(
        "Подтягивания",
        bodyweight = true,
        movement = Movement.VerticalPull,
        level = 4,
        synonyms = listOf("подтягивания", "подтягивание", "подтягивался"),
    ),
    Dips(
        "Брусья",
        bodyweight = true,
        movement = Movement.VerticalPush,
        level = 4,
        synonyms = listOf("отжимания на брусьях", "брусья", "брусьях"),
    ),
    PushUp(
        "Отжимания",
        bodyweight = true,
        movement = Movement.HorizontalPush,
        level = 2,
        synonyms = listOf("отжимания", "отжимание", "отжимался"),
    ),
    Abs(
        "Пресс",
        bodyweight = true,
        movement = Movement.Core,
        level = 1,
        synonyms = listOf("пресс", "скручивания", "приседания с закрепленными ногами"),
    ),
    Hyperextension(
        "Гиперэкстензия",
        bodyweight = true,
        movement = Movement.Hinge,
        level = 2,
        synonyms = listOf("гиперэкстензия", "гиперы"),
    ),
    FrontSquat(
        "Фронтальный присед",
        bodyweight = false,
        movement = Movement.Squat,
        level = 5,
        synonyms = listOf("фронтальный присед", "фронтальные приседания", "фронтальник"),
    ),
    GobletSquat(
        "Гоблет-присед",
        bodyweight = false,
        movement = Movement.Squat,
        level = 2,
        synonyms = listOf("гоблет присед", "гоблет", "кубковый присед"),
    ),
    LegPress(
        "Жим ногами",
        bodyweight = false,
        movement = Movement.Squat,
        level = 1,
        synonyms = listOf("жим ногами"),
    ),
    LegExtension(
        "Разгибания ног",
        bodyweight = false,
        movement = Movement.Squat,
        level = 1,
        synonyms = listOf("разгибания ног", "разгибание ног", "разгибания голени"),
    ),
    Lunge(
        "Выпады",
        bodyweight = false,
        movement = Movement.Squat,
        level = 3,
        synonyms = listOf("выпады", "выпад"),
    ),
    SplitSquat(
        "Болгарский сплит-присед",
        bodyweight = false,
        movement = Movement.Squat,
        level = 3,
        synonyms = listOf("сплит присед", "сплит приседания", "болгарский присед", "болгарские выпады"),
    ),
    PistolSquat(
        "Присед на одной ноге",
        bodyweight = true,
        movement = Movement.Squat,
        level = 6,
        synonyms = listOf("присед на одной ноге", "приседания на одной ноге", "пистолетик", "пистолет"),
    ),
    GluteBridge(
        "Ягодичный мостик",
        bodyweight = true,
        movement = Movement.Hinge,
        level = 1,
        synonyms = listOf("ягодичный мостик", "ягодичный мост", "мостик"),
    ),
    LegCurl(
        "Сгибания ног",
        bodyweight = false,
        movement = Movement.Hinge,
        level = 1,
        synonyms = listOf("сгибания ног", "сгибание ног", "сгибания голени"),
    ),
    GoodMorning(
        "Наклоны со штангой",
        bodyweight = false,
        movement = Movement.Hinge,
        level = 4,
        synonyms = listOf("наклоны со штангой", "наклон со штангой", "гуд морнинг"),
    ),
    SingleLegDeadlift(
        "Румынская тяга на одной ноге",
        bodyweight = false,
        movement = Movement.Hinge,
        level = 5,
        synonyms = listOf("румынская тяга на одной ноге", "тяга на одной ноге"),
    ),
    KneePushUp(
        "Отжимания с колен",
        bodyweight = true,
        movement = Movement.HorizontalPush,
        level = 1,
        synonyms = listOf("отжимания с колен", "отжимания от скамьи"),
    ),
    MachineChestPress(
        "Жим в тренажёре",
        bodyweight = false,
        movement = Movement.HorizontalPush,
        level = 1,
        synonyms = listOf("жим в тренажере", "жим от груди в тренажере"),
    ),
    InclineBench(
        "Жим на наклонной скамье",
        bodyweight = false,
        movement = Movement.HorizontalPush,
        level = 4,
        synonyms = listOf("жим на наклонной", "жим под углом", "наклонный жим"),
    ),
    SeatedCableRow(
        "Тяга блока к поясу",
        bodyweight = false,
        movement = Movement.HorizontalPull,
        level = 1,
        synonyms = listOf("тяга блока к поясу", "тяга блока к животу", "горизонтальная тяга", "тяга сидя"),
    ),
    InvertedRow(
        "Подтягивания на низкой перекладине",
        bodyweight = true,
        movement = Movement.HorizontalPull,
        level = 2,
        synonyms = listOf(
            "подтягивания на низкой перекладине",
            "австралийские подтягивания",
            "горизонтальные подтягивания",
        ),
    ),
    DumbbellRow(
        "Тяга гантели в наклоне",
        bodyweight = false,
        movement = Movement.HorizontalPull,
        level = 3,
        synonyms = listOf("тяга гантели в наклоне", "тяга гантели", "тяга одной рукой"),
    ),
    LateralRaise(
        "Махи в стороны",
        bodyweight = false,
        movement = Movement.VerticalPush,
        level = 1,
        synonyms = listOf("махи в стороны", "махи гантелями", "махи"),
    ),
    PikePushUp(
        "Отжимания уголком",
        bodyweight = true,
        movement = Movement.VerticalPush,
        level = 3,
        synonyms = listOf("отжимания уголком"),
    ),
    DumbbellShoulderPress(
        "Жим гантелей сидя",
        bodyweight = false,
        movement = Movement.VerticalPush,
        level = 3,
        synonyms = listOf("жим гантелей сидя", "жим гантелей над головой"),
    ),
    PushPress(
        "Швунг жимовой",
        bodyweight = false,
        movement = Movement.VerticalPush,
        level = 5,
        synonyms = listOf("швунг жимовой", "жимовой швунг", "швунг"),
    ),
    LatPulldown(
        "Вертикальная тяга",
        bodyweight = false,
        movement = Movement.VerticalPull,
        level = 1,
        synonyms = listOf("вертикальная тяга", "тяга верхнего блока", "тяга блока к груди"),
    ),
    AssistedPullUp(
        "Подтягивания в гравитроне",
        bodyweight = false,
        movement = Movement.VerticalPull,
        level = 2,
        synonyms = listOf("подтягивания в гравитроне", "гравитрон", "подтягивания с резиной"),
    ),
    ChinUp(
        "Подтягивания обратным хватом",
        bodyweight = true,
        movement = Movement.VerticalPull,
        level = 3,
        synonyms = listOf("подтягивания обратным хватом"),
    ),
    FacePull(
        "Лицевая тяга",
        bodyweight = false,
        movement = Movement.Rotation,
        level = 1,
        synonyms = listOf("лицевая тяга", "тяга к лицу"),
    ),
    ExternalRotation(
        "Вращения плеча наружу",
        bodyweight = false,
        movement = Movement.Rotation,
        level = 1,
        synonyms = listOf("вращения плеча наружу", "вращения наружу", "ротация плеча"),
    ),
    ReverseFly(
        "Обратные разводки",
        bodyweight = false,
        movement = Movement.Rotation,
        level = 2,
        synonyms = listOf("обратные разводки", "разводки в наклоне"),
    ),
    PowerClean(
        "Подъём штанги на грудь",
        bodyweight = false,
        movement = Movement.Rotation,
        level = 5,
        synonyms = listOf("подъем штанги на грудь", "подъем на грудь", "взятие на грудь"),
    ),
    Plank(
        "Планка",
        bodyweight = true,
        movement = Movement.Core,
        level = 2,
        synonyms = listOf("планка", "планку"),
    ),
    ReverseCrunch(
        "Обратные скручивания",
        bodyweight = true,
        movement = Movement.Core,
        level = 2,
        synonyms = listOf("обратные скручивания"),
    ),
    SidePlank(
        "Боковая планка",
        bodyweight = true,
        movement = Movement.Core,
        level = 2,
        synonyms = listOf("боковая планка", "боковую планку"),
    ),
    LegRaise(
        "Подъём ног в висе",
        bodyweight = true,
        movement = Movement.Core,
        level = 4,
        synonyms = listOf("подъем ног в висе", "подъемы ног", "подъем ног"),
    ),
    HackSquat(
        "Гакк-присед",
        bodyweight = false,
        movement = Movement.Squat,
        level = 1,
        synonyms = listOf("приседания в гакк тренажере", "гакк присед", "гакк"),
    ),
    Pullover(
        "Пуловер",
        bodyweight = false,
        movement = Movement.VerticalPull,
        level = 2,
        synonyms = listOf("пуловер", "пулловер"),
    ),
    StandingCalfRaise(
        "Подъём на носки стоя",
        bodyweight = false,
        movement = Movement.Squat,
        level = 1,
        synonyms = listOf("подъем на носки стоя", "подъемы на носки", "икры стоя", "икры"),
    ),
    SeatedCalfRaise(
        "Подъём на носки сидя",
        bodyweight = false,
        movement = Movement.Squat,
        level = 1,
        synonyms = listOf("подъем на носки сидя", "икры сидя"),
    ),
    AssistedDips(
        "Брусья в гравитроне",
        bodyweight = false,
        movement = Movement.VerticalPush,
        level = 2,
        synonyms = listOf("брусья в гравитроне", "отжимания на брусьях в гравитроне", "гравитрон брусья"),
    ),
    PauseSquat(
        "Присед с паузой",
        bodyweight = false,
        movement = Movement.Squat,
        level = 5,
        synonyms = listOf("присед с паузой", "приседания с паузой"),
    ),
    BoxSquat(
        "Присед на ящик",
        bodyweight = false,
        movement = Movement.Squat,
        level = 4,
        synonyms = listOf("присед на ящик", "приседания на ящик", "присед на бокс"),
    ),
    PauseBench(
        "Жим лёжа с паузой",
        bodyweight = false,
        movement = Movement.HorizontalPush,
        level = 4,
        synonyms = listOf("жим лежа с паузой", "жим с паузой"),
    ),
    BoardPress(
        "Жим на брусок",
        bodyweight = false,
        movement = Movement.HorizontalPush,
        level = 4,
        synonyms = listOf("жим на брусок", "жим с бруском", "жим на доски"),
    ),
    DeficitDeadlift(
        "Становая с дефицита",
        bodyweight = false,
        movement = Movement.Hinge,
        level = 5,
        synonyms = listOf("становая с дефицита", "тяга с дефицита"),
    ),
    BlockPull(
        "Тяга с плинтов",
        bodyweight = false,
        movement = Movement.Hinge,
        level = 4,
        synonyms = listOf("тяга с плинтов", "тяга с блоков", "тяга от колен"),
    ),
    PauseDeadlift(
        "Становая с паузой",
        bodyweight = false,
        movement = Movement.Hinge,
        level = 5,
        synonyms = listOf("становая с паузой", "тяга с паузой"),
    ),
    ;

    /**
     * Коэффициент воздействия на организм: 1 — тяжёлое базовое движение, 0,6 — другое многосуставное,
     * 0,3 — односуставное или с собственным весом. Влияет на интенсивность и время тренировки.
     */
    val loadFactor: Double
        get() = when (this) {
            Squat, Bench, Deadlift -> HEAVY
            in light -> ISOLATION
            else -> COMPOUND
        }

    /** Упражнение со штангой: вес округляется под блины и не бывает легче пустого грифа. */
    val barbell: Boolean
        get() = this in barbells

    /** Упражнение с двумя гантелями: в плане указывается вес одной. */
    val dumbbell: Boolean
        get() = this in dumbbells

    /** Соревновательное движение, по которому ведётся предельный максимум (ПМ). */
    val isMainLift: Boolean
        get() = this == Squat || this == Bench || this == Deadlift

    /** Значения коэффициента воздействия. */
    companion object {
        /** Тяжёлое базовое движение. */
        const val HEAVY = 1.0

        /** Многосуставное вспомогательное. */
        const val COMPOUND = 0.6

        /** Односуставное или с собственным весом. */
        const val ISOLATION = 0.3

        private val light = setOf(
            FrenchPress, BicepsCurl, PushUp, Abs, Hyperextension,
            LegExtension, LegCurl, GluteBridge, KneePushUp, LateralRaise,
            FacePull, ExternalRotation, ReverseFly, Plank, ReverseCrunch,
            SidePlank, Pullover, StandingCalfRaise, SeatedCalfRaise,
        )

        private val barbells = setOf(
            Squat, Bench, Deadlift, OverheadPress, MidGripBench, CloseGripBench,
            FrenchPress, BicepsCurl, StiffLegDeadlift, BentOverRow, FrontSquat, GoodMorning,
            InclineBench, PushPress, PowerClean, PauseSquat, BoxSquat, PauseBench, BoardPress,
            DeficitDeadlift, BlockPull, PauseDeadlift,
        )

        private val dumbbells = setOf(DumbbellPress, DumbbellRow, LateralRaise, DumbbellShoulderPress, ReverseFly)
    }
}
