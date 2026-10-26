package com.kurmish.fitness.core

/**
 * Строка подходов в редакторе: поля в том виде, как их ввели, пока не проверены.
 *
 * @property weight вес в кг, допускается запятая; пусто — собственный вес.
 * @property reps повторы в подходе.
 * @property sets сколько подходов; пусто — один.
 */
data class SetDraft(val weight: String = "", val reps: String = "", val sets: String = "1") {
    /** Ничего не введено: такая строка при сохранении пропускается. */
    val blank: Boolean
        get() = weight.isBlank() && reps.isBlank()

    /** Вес введён, но это не число от 0 до [MAX_WEIGHT_KG]. */
    val weightInvalid: Boolean
        get() = weight.isNotBlank() && parsedWeight == null

    /** Повторы не введены или вне 1..[MAX_REPS]. */
    val repsInvalid: Boolean
        get() = reps.trim().toIntOrNull()?.takeIf { it in 1..MAX_REPS } == null

    /** Подходы вне 1..[MAX_SETS]. */
    val setsInvalid: Boolean
        get() = parsedSets == null

    private val parsedWeight: Double?
        get() = weight.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..MAX_WEIGHT_KG }

    private val parsedSets: Int?
        get() = sets.trim().ifEmpty { "1" }.toIntOrNull()?.takeIf { it in 1..MAX_SETS }

    /** Подходы для Дневника; null, если что-то введено неправильно. */
    fun toSetGroup(): SetGroup? {
        val count = parsedSets
        return if (weightInvalid || repsInvalid || count == null) {
            null
        } else {
            SetGroup(weightKg = parsedWeight, reps = reps.trim().toInt(), sets = count)
        }
    }

    /** Пределы полей. */
    companion object {
        /** Больше этого веса не бывает даже в рекордах. */
        const val MAX_WEIGHT_KG = 1000.0

        /** Повторов в одном подходе. */
        const val MAX_REPS = 999

        /** Подходов с одним весом. */
        const val MAX_SETS = 99

        /** Черновик из записанных подходов. */
        fun of(group: SetGroup): SetDraft = SetDraft(
            weight = group.weightKg?.let(::formatWeight).orEmpty(),
            reps = group.reps.toString(),
            sets = group.sets.toString(),
        )

        /** Черновик из подходов плана: «мах» и диапазон повторов оставляют повторы пустыми или берут нижнюю границу. */
        fun planned(group: PlannedSets): SetDraft = SetDraft(
            weight = group.weightKg?.let(::formatWeight).orEmpty(),
            reps = group.reps.substringBefore('-').toIntOrNull()?.toString().orEmpty(),
            sets = group.sets.toString(),
        )

        private fun formatWeight(kg: Double): String =
            if (kg == Math.floor(kg)) kg.toLong().toString() else kg.toString().replace('.', ',')
    }
}

/**
 * Упражнение в редакторе.
 *
 * @property exercise упражнение из каталога; null — своё название.
 * @property customName название, как его ввели; пусто — название упражнения из каталога.
 * @property sets строки подходов.
 */
data class EntryDraft(val exercise: Exercise?, val customName: String = "", val sets: List<SetDraft>) {
    /** Название в поле ввода. */
    val name: String
        get() = customName.ifEmpty { exercise?.title.orEmpty() }

    /** Новое название: совпадающее с упражнением базы становится этим упражнением. */
    fun named(text: String): EntryDraft = copy(exercise = ExerciseNames.resolve(text), customName = text)

    /** Не выбрано упражнение и не введено название. */
    val nameMissing: Boolean
        get() = exercise == null && customName.isBlank()

    /** Упражнение для Дневника; null, если нет названия, подходов или какая-то строка с ошибкой. */
    fun toEntry(): ExerciseEntry? {
        val filled = sets.filterNot { it.blank }
        val groups = filled.mapNotNull { it.toSetGroup() }
        if (nameMissing || groups.isEmpty() || groups.size != filled.size) return null
        val custom = customName.trim().takeIf { exercise == null }
        return ExerciseEntry(exercise = exercise, customName = custom, sets = groups)
    }

    /** Значения по умолчанию. */
    companion object {
        /** Пустое упражнение с одной строкой подходов. */
        fun empty(): EntryDraft = EntryDraft(exercise = null, sets = listOf(SetDraft()))

        /** Черновик из рабочего пункта плана: упражнение, подходы, повторы и вес как в плане. */
        fun planned(item: PlanItem): EntryDraft = EntryDraft(
            exercise = item.exercise,
            customName = if (item.exercise == null) item.title else "",
            sets = item.sets.map(SetDraft::planned).ifEmpty { listOf(SetDraft()) },
        )

        /** Черновик из записанного упражнения. */
        fun of(entry: ExerciseEntry): EntryDraft = EntryDraft(
            exercise = entry.exercise,
            customName = entry.customName.orEmpty(),
            sets = entry.sets.map(SetDraft::of).ifEmpty { listOf(SetDraft()) },
        )
    }
}

/**
 * Запись Дневника в редакторе.
 *
 * @property id номер записи; 0 — новая запись.
 * @property epochDay дата тренировки.
 * @property rawText исходный текст: по нему можно разобрать запись заново.
 * @property entries упражнения.
 * @property feeling самочувствие.
 */
data class SessionDraft(
    val id: Long,
    val epochDay: Long,
    val rawText: String,
    val entries: List<EntryDraft>,
    val feeling: Feeling,
) {
    /** Запись можно сохранить: есть упражнения и всё введено правильно. */
    val valid: Boolean
        get() = entries.isNotEmpty() && entries.all { it.toEntry() != null }

    /** Заменяет упражнения и самочувствие разбором [rawText], как при записи текстом. */
    fun reparsed(): SessionDraft {
        val parsed = WorkoutParser.parse(rawText)
        return copy(entries = parsed.entries.map(EntryDraft::of), feeling = parsed.feeling)
    }

    /** Запись для Дневника; null, если [valid] ложно. */
    fun toSession(): WorkoutSession? {
        val parsed = entries.mapNotNull { it.toEntry() }
        if (!valid || parsed.size != entries.size) return null
        return WorkoutSession(
            id = id,
            epochDay = epochDay,
            rawText = rawText.trim(),
            entries = parsed,
            feeling = feeling,
        )
    }

    /** Создание черновиков. */
    companion object {
        /** Пустая новая запись на день [epochDay]. */
        fun new(epochDay: Long): SessionDraft = SessionDraft(
            id = 0,
            epochDay = epochDay,
            rawText = "",
            entries = listOf(EntryDraft.empty()),
            feeling = Feeling.Normal,
        )

        /**
         * Черновик записи по плану [plan] на день [epochDay]. Если за этот день уже есть запись [existing],
         * открывается она, а в конец добавляются ещё не записанные упражнения плана.
         */
        fun fromPlan(plan: DayPlan, epochDay: Long, existing: WorkoutSession?): SessionDraft {
            val base = existing?.let(::of) ?: new(epochDay).copy(entries = emptyList())
            val recorded = existing?.entries.orEmpty()
            val planned = plan.items
                .filter { it.part == PlanPart.Work && it.recordedIn(recorded).isEmpty() }
                .map(EntryDraft::planned)
            return base.copy(entries = (base.entries + planned).ifEmpty { listOf(EntryDraft.empty()) })
        }

        /** Черновик из записи Дневника. */
        fun of(session: WorkoutSession): SessionDraft = SessionDraft(
            id = session.id,
            epochDay = session.epochDay,
            rawText = session.rawText,
            entries = session.entries.map(EntryDraft::of),
            feeling = session.feeling,
        )
    }
}

/** Сохраняет запись из редактора: новую добавляет, существующую заменяет. */
fun AppData.withSaved(edited: WorkoutSession): AppData {
    val old = sessions.firstOrNull { it.id == edited.id }
    return when {
        old == null -> copy(sessions = sessions + edited.copy(id = nextId))
        old.sameContent(edited) -> this
        else -> copy(sessions = sessions.map { if (it.id == old.id) edited else it })
    }
}

/** Удаляет запись. */
fun AppData.withoutSession(id: Long): AppData = copy(sessions = sessions.filterNot { it.id == id })

/** Новая запись из разобранного текста: дата — [today] со сдвигом «вчера» и «позавчера». */
fun ParsedWorkout.toSession(rawText: String, today: Long): WorkoutSession = WorkoutSession(
    id = 0,
    epochDay = today + dayOffset,
    rawText = rawText.trim(),
    entries = entries,
    feeling = feeling,
)

/** Одинаковые дата, текст, упражнения и самочувствие. */
fun WorkoutSession.sameContent(other: WorkoutSession): Boolean =
    epochDay == other.epochDay && rawText == other.rawText && entries == other.entries && feeling == other.feeling
