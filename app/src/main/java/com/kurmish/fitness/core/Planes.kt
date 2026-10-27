package com.kurmish.fitness.core

/**
 * Часть кора: кор тренируют спереди и сбоку, а сзади его нагружают тазовые движения.
 *
 * @property title название для экрана.
 */
enum class CoreZone(val title: String) {
    /** Скручивания, планка, подъёмы ног. */
    Front("передний"),

    /** Боковая планка, наклоны, вращения. */
    Side("боковой"),
}

/**
 * Ступень лестницы внутри плоскости.
 *
 * @property level номер ступени, от 1 до 6.
 * @property exercises упражнения ступени в порядке базы.
 */
data class LadderStep(val level: Int, val exercises: List<Exercise>)

/**
 * Плоскостная классификация упражнений: лестница из шести ступеней в каждом типе движения. Первая
 * ступень самая простая, пятая самая весовая, шестая односторонняя и самая сложная технически; у
 * вращения плеча наружу самая весовая четвёртая.
 */
object Planes {
    private val side = setOf(Exercise.SideCrunch, Exercise.SidePlank, Exercise.Woodchopper, Exercise.SideBend)

    /**
     * Упражнения, которые по методике лучше не делать, и чем их заменить. В базе остаются, чтобы
     * узнавать их в Дневнике.
     */
    val discouraged: Map<Exercise, String> = mapOf(
        Exercise.FrenchPress to "нагружает локти, трицепсу хватит жима гантелей на полу",
        Exercise.HackSquat to "нагружает колени и поясницу, лучше гоблет или присед",
        Exercise.LegPress to "тяжёлый вес давит на поясницу, если нужен, то одной ногой",
    )

    /** Часть кора для упражнения на пресс, для остальных — null. */
    fun coreZone(exercise: Exercise): CoreZone? = when {
        exercise.movement != Movement.Core -> null
        exercise in side -> CoreZone.Side
        else -> CoreZone.Front
    }

    /** Лестница плоскости: ступени по возрастанию, пустые ступени пропущены. */
    fun ladder(movement: Movement): List<LadderStep> = Exercise.entries
        .filter { it.movement == movement }
        .groupBy { it.level }
        .toSortedMap()
        .map { (level, exercises) -> LadderStep(level, exercises) }
}
