package com.kurmish.fitness.core

/**
 * Блок макроцикла с примерными датами.
 *
 * @property program программа блока.
 * @property startEpochDay примерная дата начала.
 * @property weeks сколько недель займёт блок при числе тренировок в неделю из профиля.
 */
data class MacroBlock(val program: TrainingProgram, val startEpochDay: Long, val weeks: Int) {
    /** Примерная дата конца. */
    val endEpochDay: Long
        get() = startEpochDay + weeks * Macrocycle.DAYS_IN_WEEK - 1
}

/**
 * Где атлет находится в макроцикле.
 *
 * @property index номер текущего блока, с 0.
 * @property count сколько блоков в макроцикле.
 * @property program программа текущего блока.
 * @property progress сделанные тренировки блока и следующая; next = null, если пройден весь макроцикл.
 */
data class MacroPosition(val index: Int, val count: Int, val program: TrainingProgram, val progress: ProgramProgress)

/**
 * Макроцикл: программы-блоки одна за другой. Записи Дневника с даты старта по порядку считаются
 * тренировками блоков: когда тренировки одного блока кончились, следующая запись идёт в следующий блок.
 */
object Macrocycle {
    /** Дней в микроцикле для примерных дат. */
    const val DAYS_IN_WEEK = 7

    /**
     * Блоки профиля [profile] с примерными датами; старт без даты считается с [today].
     *
     * Тренировки блока идут по порядку записей, поэтому длина блока считается по тренировкам в неделю
     * из профиля: программа на 4 дня при двух тренировках в неделю займёт вдвое больше недель.
     */
    fun blocks(profile: Profile, today: Long): List<MacroBlock> {
        val programs = profile.blockPrograms()
        val perWeek = profile.daysPerWeek.coerceIn(WeekSplit.days)
        val weeks = programs.map { (it.sessions.size + perWeek - 1) / perWeek }
        val starts = weeks.runningFold(profile.programStartEpochDay ?: today) { start, count ->
            start + count * DAYS_IN_WEEK
        }
        return programs.mapIndexed { index, program -> MacroBlock(program, starts[index], weeks[index]) }
    }

    /** Текущий блок и следующая тренировка по записям [sessions]; null, если макроцикл не составлен. */
    fun position(profile: Profile, sessions: List<WorkoutSession>, today: Long): MacroPosition? {
        val programs = profile.blockPrograms().ifEmpty { return null }
        val start = profile.programStartEpochDay ?: today
        val done = sessions.count { it.epochDay >= start }
        val offsets = programs.runningFold(0) { total, program -> total + program.sessions.size }
        val index = offsets.drop(1).indexOfFirst { done < it }.takeIf { it >= 0 } ?: programs.lastIndex
        val program = programs[index]
        val inBlock = (done - offsets[index]).coerceAtMost(program.sessions.size)
        val progress = ProgramProgress(inBlock, program.sessions.getOrNull(inBlock))
        return MacroPosition(index, programs.size, program, progress)
    }
}
