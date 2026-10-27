package com.kurmish.fitness.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Всё, что приложение хранит на устройстве.
 *
 * @property profile данные атлета.
 * @property sessions записанные тренировки, новые в конце.
 * @property weights взвешивания по дням.
 */
@Serializable
data class AppData(
    val profile: Profile = Profile(),
    val sessions: List<WorkoutSession> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
) {
    /** Следующий свободный номер записи. */
    val nextId: Long
        get() = (sessions.maxOfOrNull { it.id } ?: 0L) + 1

    /**
     * Лучшая оценка разового максимума по дневнику за последние [days] дней.
     *
     * @return упражнение → оценка ПМ в кг и дата подхода, по которому она получена.
     */
    fun estimatedMaxes(today: Long, days: Int = RECENT_DAYS): Map<Exercise, MaxEstimate> = sessions
        .filter { today - it.epochDay in 0..days }
        .flatMap { session ->
            session.entries.flatMap { entry ->
                entry.sets.mapNotNull { set ->
                    val weight = set.weightKg ?: return@mapNotNull null
                    val estimate = Strength.estimateMax(weight, set.reps) ?: return@mapNotNull null
                    entry.exercise
                        ?.takeUnless(BodyweightLoad::assisted)
                        ?.let { it to MaxEstimate(estimate, session.epochDay) }
                }
            }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, estimates) -> estimates.maxBy { it.kg } }

    /** Действующий ПМ: введённый вручную, а если его нет, то оценка по дневнику. */
    fun currentMax(exercise: Exercise, today: Long): Double? =
        profile.manualMaxes[exercise] ?: estimatedMaxes(today)[exercise]?.kg

    /** ПМ для показателей нагрузки: у подтягиваний, брусьев и гравитрона — ПМ полной нагрузки. */
    fun loadMax(exercise: Exercise, today: Long): Double? =
        BodyweightLoad.base(exercise)?.let { bodyweightMax(it, today) } ?: currentMax(exercise, today)

    /**
     * ПМ для расчёта весов: свой, а если его нет, то оценка по ПМ близкого упражнения. У подтягиваний,
     * брусьев и гравитрона — ПМ полной нагрузки по Дневнику и собственному весу.
     */
    fun workingMax(exercise: Exercise, today: Long): WorkingMax? {
        val base = BodyweightLoad.base(exercise)
        if (base != null) return bodyweightMax(base, today)?.let { WorkingMax(it, bodyweightKg = profile.bodyweightKg) }
        val estimated = estimatedMaxes(today)
        val own = { lift: Exercise -> profile.manualMaxes[lift] ?: estimated[lift]?.kg }
        return own(exercise)?.let { WorkingMax(it) } ?: MaxRatios.estimate(exercise, own, strengthBodyweight())
    }

    /**
     * Вес для стартовых весов без статистики. Нормы новичка в долях веса рассчитаны на обычный состав тела,
     * а сила зависит от мышц, поэтому при введённом проценте жира вес приводится к [REFERENCE_FAT]
     * по сухой массе: при 80 кг и 30 % жира это 80 × 0,70 / 0,85 ≈ 66 кг. Тоннаж и нагрузка в подтягиваниях
     * и гравитроне считаются по полному весу: жир поднимается вместе с телом.
     */
    fun strengthBodyweight(): Double? = profile.bodyweightKg?.let { weight ->
        val fat = profile.diet.bodyFatPercent?.takeIf { it in 0.0..MAX_FAT }
        if (fat == null) weight else weight * (1 - fat / PERCENT) / (1 - REFERENCE_FAT)
    }

    /** ПМ полной нагрузки движения [base] за последние дни: подтягивания, с весом и в гравитроне вместе. */
    fun bodyweightMax(base: Exercise, today: Long): Double? {
        val body = profile.bodyweightKg ?: return null
        return sessions
            .filter { today - it.epochDay in 0..RECENT_DAYS }
            .flatMap { it.entries }
            .flatMap { entry ->
                val exercise = entry.exercise?.takeIf { BodyweightLoad.base(it) == base } ?: return@flatMap emptyList()
                entry.sets.mapNotNull { set ->
                    Strength.estimateMax(BodyweightLoad.effective(exercise, set.weightKg, body), set.reps)
                }
            }
            .maxOrNull()
    }

    /** Данные после переименований в программах, см. [Profile.migrated]. */
    fun migrated(): AppData = copy(profile = profile.migrated())

    /** Самый тяжёлый подход [exercise] с весом в последней тренировке до [today] включительно. */
    fun lastSet(exercise: Exercise, today: Long): SetGroup? = sessions
        .filter { it.epochDay <= today }
        .sortedWith(compareBy({ it.epochDay }, { it.id }))
        .asReversed()
        .firstNotNullOfOrNull { session ->
            session.entries
                .filter { it.exercise == exercise }
                .flatMap { it.sets }
                .filter { it.weightKg != null }
                .maxByOrNull { it.weightKg ?: 0.0 }
        }

    /** Значения по умолчанию. */
    companion object {
        /** За сколько дней дневник учитывается при оценке ПМ. */
        const val RECENT_DAYS = 60

        /** Доля жира, на которую рассчитаны нормы новичка в долях собственного веса. */
        const val REFERENCE_FAT = 0.15

        private const val PERCENT = 100.0
        private const val MAX_FAT = 60.0
    }
}

/**
 * Оценка максимума.
 *
 * @property kg вес.
 * @property epochDay дата подхода.
 */
data class MaxEstimate(val kg: Double, val epochDay: Long)

/** Хранит [AppData] одним JSON-файлом в памяти приложения, без сети. */
class JournalStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    /** Файл целиком, как он лежит на устройстве; null, если записей ещё не было. */
    fun raw(): ByteArray? = file.takeIf { it.exists() }?.readBytes()

    /** Разбирает копию файла, например из бэкапа; null, если это не Дневник. */
    fun decode(bytes: ByteArray): AppData? =
        runCatching { json.decodeFromString<AppData>(bytes.decodeToString()) }.getOrNull()?.migrated()

    /** Читает данные; при отсутствии или порче файла возвращает пустые. */
    fun load(): AppData =
        runCatching { json.decodeFromString<AppData>(file.readText()) }.getOrDefault(AppData()).migrated()

    /** Записывает атомарно: через временный файл, чтобы не потерять дневник при сбое. */
    fun save(data: AppData) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(AppData.serializer(), data))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }
}
