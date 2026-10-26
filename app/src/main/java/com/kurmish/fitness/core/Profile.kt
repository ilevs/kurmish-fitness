package com.kurmish.fitness.core

import kotlinx.serialization.Serializable

/** Спортивный разряд или уровень подготовки, по возрастанию. */
@Serializable
enum class Rank(val title: String) {
    Novice("Без разряда"),
    Third("III разряд"),
    Second("II разряд"),
    First("I разряд"),
    Kms("КМС"),
    Ms("МС"),
    Msmk("МСМК"),
}

/** Направление подготовки. */
@Serializable
enum class Discipline(val title: String) {
    Powerlifting("Троеборье"),
    Bench("Жим лёжа"),
    DeadliftBench("Тяга и жим"),
    OverheadPress("Жим стоя"),
    Biceps("Подъём на бицепс"),
    Bodybuilding("Бодибилдинг"),
    Armwrestling("Армрестлинг"),
}

/** Задача периода. */
@Serializable
enum class Goal(val title: String) {
    Strength("Сила"),
    Endurance("Силовая выносливость"),
    Peak("Выход на пик"),
    Mass("Масса"),
    Mixed("Смешанный"),
}

/**
 * Данные атлета, от которых зависят советы.
 *
 * @property bodyweightKg собственный вес; null, если не указан.
 * @property discipline направление.
 * @property rank уровень подготовки.
 * @property goal задача периода.
 * @property daysPerWeek сколько тренировок в неделю атлет может себе позволить.
 * @property correctionPercent процент корректировки: на сколько растёт расчётный ПМ от недели к неделе.
 * @property natural тренируется без фармакологии.
 * @property manualMaxes ПМ, введённые вручную, в кг.
 * @property programId первый блок макроцикла: встроенная программа из [Programs] или свой цикл; null — без программы.
 * @property programStartEpochDay с какого дня записи дневника считаются тренировками макроцикла.
 * @property nextProgramIds следующие блоки макроцикла по порядку.
 * @property designs свои циклы.
 * @property pool упражнения, выбранные на цикл вместо предложенных: название пункта плана → упражнение.
 * @property diet питание: фаза, процент жира, активность, рефиды.
 */
@Serializable
data class Profile(
    val bodyweightKg: Double? = null,
    val discipline: Discipline = Discipline.Powerlifting,
    val rank: Rank = Rank.Second,
    val goal: Goal = Goal.Strength,
    val daysPerWeek: Int = DEFAULT_DAYS,
    val correctionPercent: Double = DEFAULT_CORRECTION,
    val natural: Boolean = true,
    val manualMaxes: Map<Exercise, Double> = emptyMap(),
    val programId: String? = null,
    val programStartEpochDay: Long? = null,
    val pool: Map<String, Exercise> = emptyMap(),
    val diet: DietSettings = DietSettings(),
    val nextProgramIds: List<String> = emptyList(),
    val designs: List<CycleDesign> = emptyList(),
) {
    /** Встроенные программы и свои циклы. */
    val programs: List<TrainingProgram>
        get() = Programs.all + designs.map { it.toProgram() }

    /** Идентификаторы блоков макроцикла по порядку. */
    val blockIds: List<String>
        get() = listOfNotNull(programId) + nextProgramIds

    /** Программа или свой цикл по идентификатору; null, если такой нет. */
    fun program(id: String?): TrainingProgram? =
        designs.firstOrNull { it.id == id }?.toProgram() ?: Programs.byId(id)

    /** Программы блоков макроцикла по порядку. */
    fun blockPrograms(): List<TrainingProgram> = blockIds.mapNotNull(::program)

    /** Новый состав макроцикла [ids]; если сменился первый блок, макроцикл начинается с [today]. */
    fun withBlocks(ids: List<String>, today: Long): Profile = copy(
        programId = ids.firstOrNull(),
        nextProgramIds = ids.drop(1),
        programStartEpochDay = when {
            ids.isEmpty() -> null
            ids.first() == programId -> programStartEpochDay ?: today
            else -> today
        },
    )

    /** Сохраняет свой цикл [design]: заменяет прежнюю версию или добавляет новый. */
    fun withDesign(design: CycleDesign): Profile = copy(
        designs = if (designs.any { it.id == design.id }) {
            designs.map { if (it.id == design.id) design else it }
        } else {
            designs + design
        },
    )

    /** Удаляет свой цикл [id] вместе с его блоками в макроцикле. */
    fun withoutDesign(id: String, today: Long): Profile =
        copy(designs = designs.filter { it.id != id }).withBlocks(blockIds.filter { it != id }, today)

    /** Профиль после переименований в программах: выбор на цикл переносится на новые названия пунктов. */
    fun migrated(): Profile = copy(pool = pool.mapKeys { (slot, _) -> Programs.renamed[slot] ?: slot })

    /** Запоминает на цикл [exercise] вместо пункта [slot]; null возвращает исходное упражнение. */
    fun choose(slot: String, exercise: Exercise?): Profile =
        copy(pool = if (exercise == null || exercise.title == slot) pool - slot else pool + (slot to exercise))

    /** Значения по умолчанию. */
    companion object {
        /** Три тренировки в неделю. */
        const val DEFAULT_DAYS = 3

        /** Рекомендованный процент корректировки для натурального атлета. */
        const val DEFAULT_CORRECTION = 0.5
    }
}
