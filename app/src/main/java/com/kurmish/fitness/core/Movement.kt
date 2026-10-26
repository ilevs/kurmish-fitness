package com.kurmish.fitness.core

/**
 * Тип движения (плоскость): по нему упражнения взаимозаменяемы, а нагрузка уравновешивается.
 *
 * @property title название для экрана.
 */
enum class Movement(val title: String) {
    /** Присед и другие движения с упором на колени. */
    Squat("Присед"),

    /** Тяги с пола и наклоны: упор на таз и спину. */
    Hinge("Тяги с пола, наклоны"),

    /** Жимы от груди и отжимания. */
    HorizontalPush("Жим от груди"),

    /** Жимы вверх и брусья. */
    VerticalPush("Жим вверх, брусья"),

    /** Тяги к поясу. */
    HorizontalPull("Тяга к поясу"),

    /** Подтягивания и тяги сверху. */
    VerticalPull("Подтягивания"),

    /** Вращение плеча наружу и сведение лопаток. */
    Rotation("Вращатели плеча"),

    /** Бицепс и трицепс отдельно. */
    Arms("Руки"),

    /** Пресс и мышцы кора. */
    Core("Пресс"),
    ;

    /** Жимовое движение. */
    val isPush: Boolean
        get() = this == HorizontalPush || this == VerticalPush

    /** Тяговое движение верхом тела. */
    val isPull: Boolean
        get() = this == HorizontalPull || this == VerticalPull
}
