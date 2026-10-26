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

        private fun formatWeight(kg: Double): String =
            if (kg == Math.floor(kg)) kg.toLong().toString() else kg.toString().replace('.', ',')
    }
}

/**
 * Упражнение в редакторе.
 *
 * @property exercise упражнение из каталога; null — своё название.
 * @property customName своё название, когда [exercise] не выбран.
 * @property sets строки подходов.
 */
data class EntryDraft(val exercise: Exercise?, val customName: String = "", val sets: List<SetDraft>) {
    /** Не выбрано упражнение и не введено название. */
    val nameMissing: Boolean
        get() = exercise == null && customName.isBlank()

    /** Упражнение для Дневника; null, если нет названия, подходов или какая-то строка с ошибкой. */
    fun toEntry(): ExerciseEntry? {
        val filled = sets.filterNot { it.blank }
        val groups = filled.mapNotNull { it.toSetGroup() }
        if (nameMissing || groups.isEmpty() || groups.size != filled.size) return null
        val name = customName.trim().takeIf { exercise == null }
        return ExerciseEntry(exercise = exercise, customName = name, sets = groups)
    }

    /** Значения по умолчанию. */
    companion object {
        /** Пустое упражнение с одной строкой подходов. */
        fun empty(): EntryDraft = EntryDraft(exercise = null, sets = listOf(SetDraft()))

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

/**
 * Сохраняет запись из редактора: новую добавляет, существующую заменяет.
 *
 * Копия исправленной записи в Telegram обновится при следующей синхронизации. Запись, собранная из
 * сообщений боту, после правки в приложении живёт сама по себе: её сообщения больше её не меняют,
 * а в чат уходит исправленная копия.
 */
fun AppData.withSaved(edited: WorkoutSession): AppData {
    val old = sessions.firstOrNull { it.id == edited.id }
    // Новой записи и записи из Telegram копии в чате ещё нет: она уйдёт при синхронизации.
    val sentCopy = old?.telegramMessageId?.takeUnless { old.fromTelegram }
    val updated = edited.copy(
        id = old?.id ?: nextId,
        telegramMessageId = sentCopy,
        telegramParts = emptyList(),
        telegramStale = sentCopy != null,
    )
    return when {
        old == null -> copy(sessions = sessions + updated)
        old.sameContent(edited) -> this
        else -> copy(
            sessions = sessions.map { if (it.id == old.id) updated else it },
            telegram = telegram.detached(old),
        )
    }
}

/** Удаляет запись; её копия в Telegram удалится при следующей синхронизации. */
fun AppData.withoutSession(id: Long): AppData {
    val old = sessions.firstOrNull { it.id == id } ?: return this
    val deletes = listOfNotNull(old.telegramMessageId)
    val telegram = telegram.detached(old).let { it.copy(pendingDeletes = it.pendingDeletes + deletes) }
    return copy(sessions = sessions.filterNot { it.id == id }, telegram = telegram)
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

/** Сообщения боту, из которых собрана [session], больше не меняют Дневник. */
private fun TelegramState.detached(session: WorkoutSession): TelegramState =
    if (session.fromTelegram) {
        copy(detachedMessages = detachedMessages + session.telegramParts.map { it.messageId })
    } else {
        this
    }
