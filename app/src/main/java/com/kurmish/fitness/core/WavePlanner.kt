package com.kurmish.fitness.core

/** План дня саморегулирующейся программы ([Waves]): веса от ПМ на начало фазы и шага волны. */
internal object WavePlanner {
    /** План тренировки [next] блока [position]. */
    fun plan(position: MacroPosition, next: ProgramSession, data: AppData, today: Long): DayPlan {
        val program = position.program
        val pool = data.profile.pool
        val at = Waves.position(program.waves, program.daysPerWeek, position.history, Macrocycle.lead(data.profile))
        val phase = program.waves[at.phase]
        val baseDay = position.history.getOrNull(at.phaseStart)?.epochDay ?: today
        val items = next.items.map { item ->
            val exercise = pool[item.name] ?: item.maxOf
            if (item.wave == null) {
                Planner.withChoice(item, pool[item.name], data, today)
            } else {
                waved(item, exercise, exercise?.let { data.workingMax(it, baseDay) }, at.step, item == phase.lead)
            }
        }
        val block = if (position.count > 1) ", блок ${position.index + 1} из ${position.count}" else ""
        val record = at.record?.let { " Рекорд прошлой волны: ${it.kg()} — побей его в этой." }.orEmpty()
        return DayPlan(
            title = "${next.day} · фаза ${at.phase + 1} из ${program.waves.size} (${phase.title}), " +
                "волна ${at.wave + 1} из ${Waves.PER_PHASE}, микроцикл ${at.step + 1}",
            items = items,
            note = "Программа «${program.title}»$block. Вес растёт каждый микроцикл, повторов — сколько выйдет. " +
                "Записывай каждый подход в Дневник: по нему видно, когда волна кончилась.$record",
        )
    }

    /** Пункт волны [item] с весами от ПМ [max] на шаге [step]; [lead] — по нему решается конец волны. */
    internal fun waved(item: ProgramItem, exercise: Exercise?, max: WorkingMax?, step: Int, lead: Boolean): PlanItem {
        val rule = requireNotNull(item.wave)
        val end = if (rule.worstSet) {
            "хоть в одном рабочем подходе не выйдут все ${rule.reps} повторов"
        } else {
            "в самом тяжёлом подходе выйдет ${plural(rule.endReps, "повтор", "повтора", "повторов")} или меньше"
        }
        val notes = listOfNotNull(
            rule.percents(step).joinToString(", ") { it.percent() } + " ПМ; план — ${rule.reps} повторов, " +
                "но делай сколько выйдет",
            "Волна кончится, когда $end. После этого все веса откатятся ко второму микроциклу".takeIf { lead },
            "ПМ неизвестен: введи его в профиле или запиши подход в Дневник".takeIf { max == null },
        )
        return PlanItem(
            part = PlanPart.Work,
            title = exercise?.title ?: item.name,
            exercise = exercise,
            sets = Waves.sets(rule, step, max?.kg, exercise?.barbell != false),
            notes = notes,
            slot = item.name,
        )
    }
}
