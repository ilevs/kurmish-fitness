package com.kurmish.fitness.core

/**
 * Блок макроцикла с примерными датами: по неделе на микроцикл.
 *
 * @property program программа блока.
 * @property startEpochDay примерная дата начала.
 */
data class MacroBlock(val program: TrainingProgram, val startEpochDay: Long) {
    /** Примерная дата конца. */
    val endEpochDay: Long
        get() = startEpochDay + program.microcycles * Macrocycle.DAYS_IN_WEEK - 1
}

/**
 * Где атлет находится в макроцикле.
 *
 * @property index номер текущего блока, с 0.
 * @property count сколько блоков в макроцикле.
 * @property program программа текущего блока.
 * @property progress сделанные тренировки блока и следующая; next = null, если пройден весь макроцикл.
 * @property history записи Дневника текущего блока по порядку.
 */
data class MacroPosition(
    val index: Int,
    val count: Int,
    val program: TrainingProgram,
    val progress: ProgramProgress,
    val history: List<WorkoutSession> = emptyList(),
)

/**
 * Макроцикл: программы-блоки одна за другой. Записи Дневника с даты старта по порядку считаются
 * тренировками блоков: когда тренировки одного блока кончились, следующая запись идёт в следующий блок.
 * Саморегулирующаяся программа ([Waves]) занимает столько записей, сколько понадобилось на все её волны.
 */
object Macrocycle {
    /** Дней в микроцикле для примерных дат. */
    const val DAYS_IN_WEEK = 7

    /** Блоки профиля [profile] с примерными датами; старт без даты считается с [today]. */
    fun blocks(profile: Profile, today: Long): List<MacroBlock> {
        val programs = profile.blockPrograms()
        val starts = programs.runningFold(profile.programStartEpochDay ?: today) { start, program ->
            start + program.microcycles * DAYS_IN_WEEK
        }
        return programs.mapIndexed { index, program -> MacroBlock(program, starts[index]) }
    }

    /** Текущий блок и следующая тренировка по записям [sessions]; null, если макроцикл не составлен. */
    fun position(profile: Profile, sessions: List<WorkoutSession>, today: Long): MacroPosition? {
        val programs = profile.blockPrograms()
        val start = profile.programStartEpochDay ?: today
        var rest = sessions.filter { it.epochDay >= start }
        val index = programs.indices.firstOrNull { index ->
            val length = length(programs[index], rest, profile)
            val current = rest.size < length || index == programs.lastIndex
            if (!current) rest = rest.drop(length)
            current
        } ?: return null
        val program = programs[index]
        val length = length(program, rest, profile)
        val history = rest.take(length)
        val next = if (history.size < length) next(program, history, profile) else null
        return MacroPosition(index, programs.size, program, ProgramProgress(history.size, next), history)
    }

    /** Ведущее упражнение фазы: то, что атлет выбрал вместо пункта программы, или пункт как есть. */
    fun lead(profile: Profile): (ProgramItem) -> Exercise? = { item -> profile.pool[item.name] ?: item.maxOf }

    /** Сколько записей занимает [program], если после начала блока записаны [rest]. */
    private fun length(program: TrainingProgram, rest: List<WorkoutSession>, profile: Profile): Int =
        if (program.selfRegulating) {
            Waves.position(program.waves, program.daysPerWeek, rest, lead(profile)).length ?: Int.MAX_VALUE
        } else {
            program.sessions.size
        }

    /** Следующая тренировка программы после записей блока [history]. */
    private fun next(program: TrainingProgram, history: List<WorkoutSession>, profile: Profile): ProgramSession? {
        if (!program.selfRegulating) return program.sessions.getOrNull(history.size)
        val phase = program.waves[Waves.position(program.waves, program.daysPerWeek, history, lead(profile)).phase]
        val (day, items) = phase.days[history.size % phase.days.size]
        return ProgramSession(day, history.size / phase.days.size + 1, items)
    }
}
