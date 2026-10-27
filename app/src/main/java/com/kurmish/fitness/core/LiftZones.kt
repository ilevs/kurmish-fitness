package com.kurmish.fitness.core

import kotlin.math.roundToInt

/**
 * Зона интенсивности и сколько подъёмов в ней за тренировку одного движения стоит делать.
 *
 * @property fromPercent нижняя граница зоны, % ПМ включительно.
 * @property toPercent верхняя граница, % ПМ не включительно; null — до максимума.
 * @property lifts коридор КПШ за тренировку.
 * @property optimum оптимальное КПШ.
 */
data class LiftZone(val fromPercent: Int, val toPercent: Int?, val lifts: IntRange, val optimum: Int) {
    /** «70–80 %» или «от 90 %». */
    val title: String
        get() = toPercent?.let { "$fromPercent–$it %" } ?: "от $fromPercent %"
}

/** Где КПШ относительно коридора зоны. */
enum class ZoneVerdict {
    /** Меньше коридора. */
    Below,

    /** В коридоре. */
    Inside,

    /** Больше коридора. */
    Above,
}

/**
 * Подъёмы одного движения в одной зоне за тренировку.
 *
 * @property exercise упражнение.
 * @property zone зона интенсивности.
 * @property lifts сколько подъёмов.
 */
data class ZoneCount(val exercise: Exercise, val zone: LiftZone, val lifts: Int) {
    /** Где КПШ относительно коридора. */
    val verdict: ZoneVerdict
        get() = when {
            lifts < zone.lifts.first -> ZoneVerdict.Below
            lifts > zone.lifts.last -> ZoneVerdict.Above
            else -> ZoneVerdict.Inside
        }

    /** «Присед, 70–80 %: 30 подъёмов — больше коридора 12–24 (оптимум 18)». */
    val line: String
        get() {
            val corridor = "${zone.lifts.first}–${zone.lifts.last}"
            val where = when (verdict) {
                ZoneVerdict.Below -> "меньше коридора $corridor (оптимум ${zone.optimum})"
                ZoneVerdict.Inside -> "в коридоре $corridor"
                ZoneVerdict.Above -> "больше коридора $corridor (оптимум ${zone.optimum})"
            }
            return "${exercise.title}, ${zone.title}: ${plural(lifts, "подъём", "подъёма", "подъёмов")} — $where"
        }
}

/**
 * Коридоры КПШ по зонам интенсивности для подходов без отказа: чем тяжелее вес, тем меньше подъёмов за тренировку
 * даёт результат. Подходы легче 55 % ПМ не считаются.
 */
object LiftZones {
    private const val PERCENT = 100.0

    /** Зоны от лёгкой к тяжёлой. */
    val zones: List<LiftZone> = listOf(
        LiftZone(fromPercent = 55, toPercent = 70, lifts = 18..30, optimum = 24),
        LiftZone(fromPercent = 70, toPercent = 80, lifts = 12..24, optimum = 18),
        LiftZone(fromPercent = 80, toPercent = 90, lifts = 10..20, optimum = 15),
        LiftZone(fromPercent = 90, toPercent = null, lifts = 4..10, optimum = 7),
    )

    /** Зона веса [weightKg] при ПМ [maxKg]; null, если легче 55 %. */
    fun zoneOf(weightKg: Double, maxKg: Double): LiftZone? {
        val percent = (weightKg / maxKg * PERCENT).roundToInt()
        return zones.lastOrNull { percent >= it.fromPercent }
    }

    /** КПШ по зонам в записанных упражнениях [entries]; [maxOf] даёт ПМ упражнения или null. */
    fun count(entries: List<ExerciseEntry>, maxOf: (Exercise) -> Double?): List<ZoneCount> = entries
        .flatMap { entry ->
            val exercise = entry.exercise?.takeUnless(BodyweightLoad::assisted) ?: return@flatMap emptyList()
            val max = maxOf(exercise) ?: return@flatMap emptyList()
            entry.sets.mapNotNull { set ->
                set.weightKg?.let { zoneOf(it, max) }?.let { Triple(exercise, it, set.reps * set.sets) }
            }
        }
        .groupBy({ it.first to it.second }, { it.third })
        .map { (key, lifts) -> ZoneCount(key.first, key.second, lifts.sum()) }

    /** КПШ по зонам в пункте плана [item] при ПМ [maxKg]; диапазон повторов считается по нижней границе. */
    fun planned(item: PlanItem, maxKg: Double): List<ZoneCount> {
        val exercise = item.exercise ?: return emptyList()
        val sets = item.sets.mapNotNull { planned ->
            val reps = planned.reps.substringBefore('-').toIntOrNull() ?: return@mapNotNull null
            planned.weightKg?.let { SetGroup(it, reps, planned.sets) }
        }
        return count(listOf(ExerciseEntry(exercise, sets = sets)), { maxKg })
    }

    /** Пункт [item] с предупреждением, если его КПШ в какой-то зоне выходит из коридора; [max] — ПМ упражнения. */
    fun withNotes(item: PlanItem, max: WorkingMax?): PlanItem {
        val kg = max?.takeIf { it.bodyweightKg == null }?.kg ?: return item
        val off = planned(item, kg).filter { it.verdict != ZoneVerdict.Inside }
        return item.copy(notes = item.notes + off.map { "КПШ ${it.line.substringAfter(", ")}" })
    }

    /** Дата последней тренировки до [today] включительно и КПШ по зонам в ней; null, если считать не по чему. */
    fun lastSession(data: AppData, today: Long): Pair<Long, List<ZoneCount>>? {
        val last = data.sessions.filter { it.epochDay <= today }.maxByOrNull { it.epochDay } ?: return null
        val counts = count(last.entries) { data.currentMax(it, today) }
        return counts.takeIf { it.isNotEmpty() }?.let { last.epochDay to it }
    }
}
