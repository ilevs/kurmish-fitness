package com.kurmish.fitness.core

/**
 * Один подход в редакторе: поля в том виде, как их ввели, пока не проверены.
 *
 * @property weight вес в кг, допускается запятая; пусто — собственный вес.
 * @property reps повторы.
 * @property done подход сделан: в Дневник попадают только отмеченные подходы.
 */
data class SetDraft(val weight: String = "", val reps: String = "", val done: Boolean = false) {
    /** Ничего не введено: такая строка при сохранении пропускается. */
    val blank: Boolean
        get() = weight.isBlank() && reps.isBlank()

    /** Вес введён, но это не число от 0 до [MAX_WEIGHT_KG]. */
    val weightInvalid: Boolean
        get() = weight.isNotBlank() && parsedWeight == null

    /** Повторы не введены или вне 1..[MAX_REPS]. */
    val repsInvalid: Boolean
        get() = reps.trim().toIntOrNull()?.takeIf { it in 1..MAX_REPS } == null

    /**
     * Новые вес и повторы. Вписал повторы в пустую строку — подход сделан и отмечается сам; поправка веса
     * или повторов, подставленных из плана, отметку не меняет.
     */
    fun edited(weight: String = this.weight, reps: String = this.reps): SetDraft {
        val changed = copy(weight = weight, reps = reps)
        return changed.copy(done = done || repsInvalid && !changed.repsInvalid)
    }

    /** Подход отмечен, но записать его нельзя. */
    val broken: Boolean
        get() = done && !blank && (weightInvalid || repsInvalid)

    private val parsedWeight: Double?
        get() = weight.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..MAX_WEIGHT_KG }

    /** Подход для Дневника; null, если что-то введено неправильно. */
    fun toSetGroup(): SetGroup? =
        if (weightInvalid || repsInvalid) null else SetGroup(weightKg = parsedWeight, reps = reps.trim().toInt())

    /** Пределы полей. */
    companion object {
        /** Больше этого веса не бывает даже в рекордах. */
        const val MAX_WEIGHT_KG = 1000.0

        /** Повторов в одном подходе. */
        const val MAX_REPS = 999

        /** Подходов с одним весом в одной строке Дневника. */
        private const val MAX_SETS = 99

        /** Сделанные подходы из записанной строки: каждый подход отдельно. */
        fun of(group: SetGroup): List<SetDraft> = List(group.sets.coerceIn(1, MAX_SETS)) {
            SetDraft(weight = group.weightKg?.let(::formatWeight).orEmpty(), reps = group.reps.toString(), done = true)
        }

        /** Подходы плана, ещё не сделанные: «мах» оставляет повторы пустыми, диапазон берёт нижнюю границу. */
        fun planned(group: PlannedSets): List<SetDraft> = List(group.sets.coerceIn(1, MAX_SETS)) {
            SetDraft(
                weight = group.weightKg?.let(::formatWeight).orEmpty(),
                reps = group.reps.substringBefore('-').toIntOrNull()?.toString().orEmpty(),
            )
        }

        /** Вес для поля ввода: без «,0», с запятой. */
        fun formatWeight(kg: Double): String =
            if (kg == Math.floor(kg)) kg.toLong().toString() else kg.toString().replace('.', ',')
    }
}

/**
 * Упражнение в редакторе: название и подходы одной карточкой.
 *
 * @property exercise упражнение из каталога; null — своё название.
 * @property customName название, как его ввели; пусто — название упражнения из каталога.
 * @property sets подходы по одному.
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

    /** Сделанные подходы, которые попадут в Дневник. */
    private val doneSets: List<SetDraft>
        get() = sets.filter { it.done && !it.blank }

    /** Нет ни одного отмеченного подхода: упражнение не записывается. */
    val skipped: Boolean
        get() = doneSets.isEmpty()

    /** Упражнение записать нельзя: есть отмеченные подходы, но нет названия или подход с ошибкой. */
    val broken: Boolean
        get() = !skipped && (nameMissing || sets.any { it.broken })

    /** Подход с весом и повторами прошлой строки: следующий подход обычно такой же. */
    fun withSet(): EntryDraft = copy(sets = sets + (sets.lastOrNull()?.copy(done = false) ?: SetDraft()))

    /**
     * Упражнение для Дневника: подходы подряд с одинаковым весом и повторами сливаются в одну строку.
     * Null, если отмеченных подходов нет или что-то введено неправильно.
     */
    fun toEntry(): ExerciseEntry? {
        val groups = doneSets.map { it.toSetGroup() }
        if (nameMissing || groups.isEmpty() || null in groups) return null
        val merged = groups.filterNotNull().fold(emptyList<SetGroup>()) { acc, group ->
            val last = acc.lastOrNull()
            if (last != null && last.weightKg == group.weightKg && last.reps == group.reps) {
                acc.dropLast(1) + last.copy(sets = last.sets + group.sets)
            } else {
                acc + group
            }
        }
        val custom = customName.trim().takeIf { exercise == null }
        return ExerciseEntry(exercise = exercise, customName = custom, sets = merged)
    }

    /** Значения по умолчанию. */
    companion object {
        /** Пустое упражнение с одним подходом. */
        fun empty(): EntryDraft = EntryDraft(exercise = null, sets = listOf(SetDraft()))

        /** Черновик из рабочего пункта плана: подходы, повторы и вес как в плане, пока не отмечены. */
        fun planned(item: PlanItem): EntryDraft = EntryDraft(
            exercise = item.exercise,
            customName = if (item.exercise == null) item.title else "",
            sets = item.sets.flatMap(SetDraft::planned).ifEmpty { listOf(SetDraft()) },
        )

        /** Черновик из записанного упражнения: все подходы отмечены. */
        fun of(entry: ExerciseEntry): EntryDraft = EntryDraft(
            exercise = entry.exercise,
            customName = entry.customName.orEmpty(),
            sets = entry.sets.flatMap(SetDraft::of).ifEmpty { listOf(SetDraft()) },
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
    /** Запись можно сохранить: есть отмеченные подходы и всё отмеченное введено правильно. */
    val valid: Boolean
        get() = entries.any { !it.skipped } && entries.none { it.broken }

    /** Заменяет упражнения и самочувствие разбором [rawText], как при записи текстом. */
    fun reparsed(): SessionDraft {
        val parsed = WorkoutParser.parse(rawText)
        return copy(entries = parsed.entries.map(EntryDraft::of), feeling = parsed.feeling)
    }

    /** Запись для Дневника без упражнений, где ничего не отмечено; null, если [valid] ложно. */
    fun toSession(): WorkoutSession? {
        if (!valid) return null
        val parsed = entries.filterNot { it.skipped }.mapNotNull { it.toEntry() }
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
         * открывается она: к записанным упражнениям плана добавляются ещё не сделанные подходы, а в конец —
         * ещё не записанные упражнения.
         */
        fun fromPlan(plan: DayPlan, epochDay: Long, existing: WorkoutSession?): SessionDraft {
            val base = existing?.let(::of) ?: new(epochDay).copy(entries = emptyList())
            val work = plan.items.filter { it.part == PlanPart.Work }
            val recorded = base.entries.map { entry ->
                val item = work.firstOrNull { it.exercise != null && it.exercise == entry.exercise }
                val rest = item?.let { EntryDraft.planned(it).sets.drop(entry.sets.size) }.orEmpty()
                entry.copy(sets = entry.sets + rest)
            }
            val planned = work
                .filter { it.recordedIn(existing?.entries.orEmpty()).isEmpty() }
                .map(EntryDraft::planned)
            return base.copy(entries = (recorded + planned).ifEmpty { listOf(EntryDraft.empty()) })
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

/**
 * Подходы упражнения [entry] в последней тренировке до дня [epochDay], по одному, для подсказки «прошлый раз».
 * Запись [excludeId] — та, что сейчас редактируется, — не считается.
 */
fun AppData.previousSets(entry: EntryDraft, epochDay: Long, excludeId: Long): List<SetGroup> {
    val name = entry.customName.trim()
    fun same(other: ExerciseEntry): Boolean = when {
        entry.exercise != null -> other.exercise == entry.exercise
        name.isEmpty() -> false
        else -> other.exercise == null && other.customName.orEmpty().trim().equals(name, ignoreCase = true)
    }
    return sessions
        .filter { it.id != excludeId && it.epochDay <= epochDay }
        .sortedWith(compareBy({ it.epochDay }, { it.id }))
        .asReversed()
        .firstNotNullOfOrNull { session -> session.entries.firstOrNull(::same) }
        ?.sets
        ?.flatMap { group -> List(group.sets) { group.copy(sets = 1) } }
        .orEmpty()
}

/**
 * Какое упражнение черновика относится к какому рабочему пункту плана [plan]: номер пункта → номер
 * упражнения. Упражнения без пары — сделанные сверх плана.
 */
fun SessionDraft.matchPlan(plan: DayPlan): Map<Int, Int> {
    val taken = mutableSetOf<Int>()
    return plan.items.withIndex()
        .filter { it.value.part == PlanPart.Work }
        .mapNotNull { (itemIndex, item) ->
            entries.indices.firstOrNull { it !in taken && entries[it].matches(item) }?.let { entryIndex ->
                taken += entryIndex
                itemIndex to entryIndex
            }
        }
        .toMap()
}

private fun EntryDraft.matches(item: PlanItem): Boolean = if (item.exercise != null) {
    exercise == item.exercise
} else {
    exercise == null && customName.trim().equals(item.title, ignoreCase = true)
}

/** Удаляет запись. */
fun AppData.withoutSession(id: Long): AppData = copy(sessions = sessions.filterNot { it.id == id })

/**
 * Записывает разобранный текст [rawText] за день [today] со сдвигом «вчера» и «позавчера». Если за этот день
 * уже есть тренировка, текст дописывается в неё: подходы того же упражнения добавляются к нему, новые
 * упражнения — в конец. Так сказанное голосом ложится на сегодняшний план и уже отмеченные подходы.
 */
fun AppData.withRecorded(parsed: ParsedWorkout, rawText: String, today: Long): AppData {
    val fresh = parsed.toSession(rawText, today)
    val existing = sessions.filter { it.epochDay == fresh.epochDay }.maxByOrNull { it.id } ?: return withSaved(fresh)
    val entries = fresh.entries.fold(existing.entries) { acc, entry ->
        val index = acc.indexOfFirst { it.sameExercise(entry) }
        if (index < 0) {
            acc + entry
        } else {
            acc.mapIndexed { i, old -> if (i == index) old.copy(sets = old.sets + entry.sets) else old }
        }
    }
    val merged = existing.copy(
        rawText = listOf(existing.rawText, fresh.rawText).filter { it.isNotBlank() }.joinToString(", "),
        entries = entries,
        feeling = if (fresh.feeling == Feeling.Normal) existing.feeling else fresh.feeling,
    )
    return withSaved(merged)
}

private fun ExerciseEntry.sameExercise(other: ExerciseEntry): Boolean = if (exercise != null) {
    exercise == other.exercise
} else {
    other.exercise == null && customName.orEmpty().equals(other.customName.orEmpty(), ignoreCase = true)
}

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
