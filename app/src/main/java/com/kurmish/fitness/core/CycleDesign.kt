package com.kurmish.fitness.core

import kotlinx.serialization.Serializable

/**
 * Нагрузка упражнения в одном микроцикле своего цикла.
 *
 * @property scheme подходы × повторы, например «4×6».
 * @property percents проценты от ПМ: один на все подходы или по одному на подход.
 * @property kg вес в килограммах, если он задан не процентами.
 */
@Serializable
data class DesignWeek(val scheme: String = "", val percents: List<Int> = emptyList(), val kg: Double? = null) {
    /** «67, 70, 73 %», «62,5 кг» или пустая строка, если вес не задан. */
    val load: String
        get() = when {
            kg != null -> kg.kg()
            percents.isNotEmpty() -> percents.joinToString(", ") + " %"
            else -> ""
        }

    /** Разбор того, что ввёл атлет. */
    companion object {
        private val numbers = Regex("""\d+""")

        /** Схема [scheme] и нагрузка [load]: проценты через запятую при [inPercents], иначе вес в кг. */
        fun parse(scheme: String, load: String, inPercents: Boolean): DesignWeek = if (inPercents) {
            DesignWeek(scheme.trim(), numbers.findAll(load).map { it.value.toInt() }.toList())
        } else {
            DesignWeek(scheme.trim(), kg = load.replace(',', '.').filter { it.isDigit() || it == '.' }.toDoubleOrNull())
        }
    }
}

/**
 * Упражнение своего цикла сквозь микроциклы.
 *
 * @property name название; по нему узнаётся упражнение из базы.
 * @property weeks нагрузка на каждый микроцикл; null — в этом микроцикле упражнения нет.
 * @property maxOf от ПМ какого движения считать проценты; null — от ПМ самого упражнения.
 */
@Serializable
data class DesignItem(val name: String, val weeks: List<DesignWeek?>, val maxOf: Exercise? = null) {
    /** Меняет нагрузку микроцикла [week] (с 0), а при [everywhere] — всех микроциклов. */
    fun withWeek(week: Int, value: DesignWeek?, everywhere: Boolean = false): DesignItem =
        copy(weeks = weeks.mapIndexed { index, old -> if (everywhere || index == week) value else old })
}

/**
 * День микроцикла своего цикла.
 *
 * @property title название дня.
 * @property items упражнения по порядку.
 */
@Serializable
data class DesignDay(val title: String, val items: List<DesignItem> = emptyList()) {
    /** Заменяет упражнение [index]; null удаляет его, индекс за концом списка добавляет новое. */
    fun withItem(index: Int, item: DesignItem?): DesignDay = copy(items = items.replaced(index, item))

    /** Сдвигает упражнение [index] на [delta] позиций, не выходя за края списка. */
    fun moved(index: Int, delta: Int): DesignDay {
        val target = (index + delta).coerceIn(items.indices)
        return copy(items = items.toMutableList().apply { add(target, removeAt(index)) })
    }
}

/**
 * Свой цикл: дни микроцикла и нагрузка упражнений по микроциклам. Из него получается обычная
 * [TrainingProgram], поэтому план на сегодня и расписание считаются так же, как для встроенных программ.
 *
 * @property id постоянный идентификатор, начинается с «my-».
 * @property title название.
 * @property note пояснение.
 * @property goal на что работает цикл.
 * @property microcycles сколько микроциклов (недель).
 * @property days дни микроцикла.
 */
@Serializable
data class CycleDesign(
    val id: String,
    val title: String,
    val note: String = "",
    val goal: Goal = Goal.Strength,
    val microcycles: Int = 1,
    val days: List<DesignDay> = emptyList(),
) {
    /** Программа для плана и расписания. */
    fun toProgram(): TrainingProgram = TrainingProgram(
        id = id,
        title = title,
        note = note,
        daysPerWeek = days.size,
        goal = goal,
        sessions = (1..microcycles).flatMap { microcycle ->
            days.map { day ->
                ProgramSession(
                    day = day.title,
                    microcycle = microcycle,
                    items = day.items.mapNotNull { item ->
                        item.weeks.getOrNull(microcycle - 1)?.let { week ->
                            ProgramItem(item.name, week.scheme.ifBlank { null }, item.maxOf, week.percents, week.kg)
                        }
                    },
                )
            }
        },
    )

    /** Меняет число микроциклов: новые повторяют последний, лишние отбрасываются. */
    fun withMicrocycles(count: Int): CycleDesign {
        val size = count.coerceIn(1, MAX_MICROCYCLES)
        val days = days.map { day ->
            day.copy(items = day.items.map { item -> item.copy(weeks = item.weeks.resized(size)) })
        }
        return copy(microcycles = size, days = days)
    }

    /** Заменяет день [index]; null удаляет его, индекс за концом списка добавляет новый. */
    fun withDay(index: Int, day: DesignDay?): CycleDesign = copy(days = days.replaced(index, day))

    /** Пустое упражнение на все микроциклы этого цикла. */
    fun newItem(name: String): DesignItem = DesignItem(name, List(microcycles) { DesignWeek() })

    /** Создание своих циклов. */
    companion object {
        /** Больше микроциклов в одном блоке не бывает: длинный цикл лучше разбить на блоки. */
        const val MAX_MICROCYCLES = 12

        private const val PREFIX = "my-"

        /** Свободный идентификатор среди [designs]. */
        fun nextId(designs: List<CycleDesign>): String =
            PREFIX + ((designs.mapNotNull { it.id.removePrefix(PREFIX).toIntOrNull() }.maxOrNull() ?: 0) + 1)

        /** Копия программы [program] для правки. */
        fun copyOf(program: TrainingProgram, id: String): CycleDesign {
            val days = program.sessions.groupBy { it.day }.map { (title, sessions) ->
                val names = sessions.flatMap { session -> session.items.map { it.name } }.distinct()
                DesignDay(
                    title = title,
                    items = names.map { name ->
                        val found = (1..program.microcycles).map { microcycle ->
                            sessions.firstOrNull { it.microcycle == microcycle }?.items?.firstOrNull { it.name == name }
                        }
                        DesignItem(
                            name = name,
                            weeks = found.map { item ->
                                item?.let { DesignWeek(it.scheme.orEmpty(), it.percents, it.kg) }
                            },
                            maxOf = found.firstNotNullOfOrNull { it?.maxOf },
                        )
                    },
                )
            }
            return CycleDesign(id, "Мой «${program.title}»", program.note, program.goal, program.microcycles, days)
        }

        /** Новый цикл с одним днём. */
        fun empty(id: String, goal: Goal): CycleDesign =
            CycleDesign(id = id, title = "Мой цикл", goal = goal, microcycles = 1, days = listOf(DesignDay("День 1")))
    }
}

/** Заменяет элемент [index]; null удаляет его, индекс за концом списка добавляет [value] в конец. */
internal fun <T> List<T>.replaced(index: Int, value: T?): List<T> = when {
    index >= size -> if (value == null) this else this + value
    value == null -> filterIndexed { i, _ -> i != index }
    else -> mapIndexed { i, old -> if (i == index) value else old }
}

/** Список длины [size]: новые места повторяют последний элемент. */
internal fun List<DesignWeek?>.resized(size: Int): List<DesignWeek?> = List(size) { getOrElse(it) { lastOrNull() } }
