package com.kurmish.fitness.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Настройки и состояние синхронизации Дневника с ботом Telegram.
 *
 * @property token токен бота от @BotFather; пусто — синхронизация выключена.
 * @property botId номер бота: по нему понятно, что после отключения подключили того же бота.
 * @property botUsername имя бота без @.
 * @property chatId чат с владельцем: первый, кто написал боту. Сообщения из других чатов не принимаются.
 * @property lastUpdateId номер последнего обработанного события Bot API.
 * @property pendingDeletes сообщения бота, которые надо удалить: их записи удалены в приложении.
 * @property detachedMessages сообщения боту, чьи записи исправлены или удалены в приложении: их правки
 * в Telegram Дневник больше не меняют.
 * @property lastSyncEpochSecond когда синхронизация последний раз прошла без ошибок.
 */
@Serializable
data class TelegramState(
    val token: String = "",
    val botId: Long? = null,
    val botUsername: String = "",
    val chatId: Long? = null,
    val lastUpdateId: Long = 0,
    val pendingDeletes: List<Long> = emptyList(),
    val detachedMessages: List<Long> = emptyList(),
    val lastSyncEpochSecond: Long? = null,
) {
    /** Бот подключён. */
    val connected: Boolean
        get() = token.isNotBlank()
}

/** Текст сообщений бота. */
object TelegramFormat {
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru-RU"))

    /** Подсказка в ответ на /start и непонятные команды. */
    const val HELP = "Это бот вашего Дневника тренировок. Пишите тренировку как в приложении, например:\n" +
        "присед 120 на 5 по 3, жим 90х5х4, подтягивания 3 по 10, было легко\n\n" +
        "Начните со слова «вчера», чтобы записать вчерашнюю. Сообщения за один день собираются в одну запись, " +
        "исправленное сообщение исправляет запись. Записи из приложения тоже приходят сюда. " +
        "Всё попадает в Дневник, когда телефон с приложением будет в сети."

    /** Ответ на сообщение без упражнений. */
    const val NOT_UNDERSTOOD = "Не нашёл упражнений с подходами. Пример: «жим 100 на 5 по 3». " +
        "Исправьте сообщение, и я запишу его."

    /** Ответ на правку сообщения, чья запись уже исправлена в приложении. */
    const val EDITED_IN_APP = "Эта запись уже исправлена в приложении, поэтому правка сообщения её не меняет. " +
        "Исправьте запись в Дневнике приложения."

    /** Ответ на голосовое, фото и прочее без текста. */
    const val TEXT_ONLY = "Понимаю только текст. Голосом удобно записывать прямо в приложении."

    /** Реакция «записал». */
    const val RECORDED = "👍"

    /** Копия записи приложения: дата, упражнения, самочувствие. */
    fun session(session: WorkoutSession): String {
        val date = LocalDate.ofEpochDay(session.epochDay).format(dateFormat).replaceFirstChar { it.uppercaseChar() }
        val lines = session.entries.map { entry -> "${entry.displayName}: ${entry.sets.joinToString { it.label() }}" }
        val feeling = when (session.feeling) {
            Feeling.Easy -> listOf("Было легко")
            Feeling.Hard -> listOf("Было тяжело")
            Feeling.Normal -> emptyList()
        }
        return (listOf("📒 $date") + lines + feeling).joinToString("\n")
    }

    private fun SetGroup.label(): String {
        val weight = weightKg?.let { "${it.kg()} × " }.orEmpty()
        val tail = if (sets > 1) " × $sets" else ""
        return "$weight$reps$tail"
    }
}

/**
 * Что ответить в чат после разбора входящих сообщений.
 *
 * @property messageId на какое сообщение.
 * @property text текст ответа; null — только реакция [TelegramFormat.RECORDED].
 */
data class TelegramReply(val messageId: Long, val text: String?)

/**
 * Итог разбора событий.
 *
 * @property data данные с новыми и исправленными записями.
 * @property replies ответы, которые надо отправить.
 */
data class InboxResult(val data: AppData, val replies: List<TelegramReply>)

/**
 * Превращает сообщения боту в записи Дневника.
 *
 * Сообщения за один день собираются в одну запись: тренировку часто пишут по упражнению.
 * Исправленное в Telegram сообщение исправляет запись.
 */
object TelegramInbox {
    /** Применяет [updates] к [data]. Даты сообщений считаются в часовом поясе [zone]. */
    fun apply(data: AppData, updates: List<TgUpdate>, zone: ZoneId): InboxResult {
        var current = data
        val replies = mutableListOf<TelegramReply>()
        updates.sortedBy { it.updateId }.forEach { update ->
            val telegram = current.telegram.copy(lastUpdateId = maxOf(current.telegram.lastUpdateId, update.updateId))
            current = current.copy(telegram = telegram)
            val message = update.message ?: update.editedMessage ?: return@forEach
            val chatId = telegram.chatId ?: message.chat.id.takeIf { message.chat.type == PRIVATE }
            if (chatId != message.chat.id) return@forEach
            current = current.copy(telegram = telegram.copy(chatId = chatId))
            val text = message.text
            val reply = when {
                text == null -> TelegramReply(message.messageId, TelegramFormat.TEXT_ONLY)
                text.startsWith("/") -> TelegramReply(message.messageId, TelegramFormat.HELP)
                message.messageId in telegram.detachedMessages ->
                    TelegramReply(message.messageId, TelegramFormat.EDITED_IN_APP)
                else -> {
                    val (updated, understood) = record(current, message, text, zone)
                    current = updated
                    TelegramReply(message.messageId, if (understood) null else TelegramFormat.NOT_UNDERSTOOD)
                }
            }
            replies += reply
        }
        return InboxResult(current, replies)
    }

    /** Добавляет или исправляет часть записи. Возвращает данные и то, нашлись ли упражнения. */
    private fun record(data: AppData, message: TgMessage, text: String, zone: ZoneId): Pair<AppData, Boolean> {
        val part = TelegramPart(message.messageId, text.trim())
        val parsed = WorkoutParser.parse(part.text)
        val owner = data.sessions.firstOrNull { session ->
            session.telegramParts.any { it.messageId == part.messageId }
        }
        val withoutPart = if (owner == null) {
            data.sessions
        } else {
            val parts = owner.telegramParts.filterNot { it.messageId == part.messageId }
            data.sessions.mapNotNull { if (it.id == owner.id) rebuild(it, parts) else it }
        }
        if (parsed.entries.isEmpty()) return data.copy(sessions = withoutPart) to false
        val day = Instant.ofEpochSecond(message.date).atZone(zone).toLocalDate().toEpochDay() + parsed.dayOffset
        val sameDay = withoutPart.lastOrNull { it.fromTelegram && it.epochDay == day }
        val sessions = if (sameDay == null) {
            val id = (withoutPart.maxOfOrNull { it.id } ?: 0L) + 1
            val session = WorkoutSession(id = id, epochDay = day, rawText = "", entries = emptyList())
            withoutPart + checkNotNull(rebuild(session, listOf(part)))
        } else {
            withoutPart.map { if (it.id == sameDay.id) checkNotNull(rebuild(it, it.telegramParts + part)) else it }
        }
        return data.copy(sessions = sessions) to true
    }

    /** Пересобирает запись из сообщений; null, если сообщений не осталось. */
    private fun rebuild(session: WorkoutSession, parts: List<TelegramPart>): WorkoutSession? {
        if (parts.isEmpty()) return null
        val sorted = parts.sortedBy { it.messageId }
        val parsed = sorted.map { WorkoutParser.parse(it.text) }
        return session.copy(
            rawText = sorted.joinToString("\n") { it.text },
            entries = parsed.flatMap { it.entries },
            feeling = parsed.lastOrNull { it.feeling != Feeling.Normal }?.feeling ?: Feeling.Normal,
            telegramParts = sorted,
        )
    }

    private const val PRIVATE = "private"
}

/**
 * Итог синхронизации.
 *
 * @property received сколько сообщений пришло из Telegram.
 * @property sent сколько записей отправлено в Telegram.
 */
data class SyncReport(val received: Int, val sent: Int)

/**
 * Синхронизирует Дневник с чатом бота: забирает новые сообщения, удаляет копии удалённых записей,
 * обновляет копии исправленных и отправляет записи, которых в чате ещё нет.
 *
 * Данные меняются только через [change]: пока идёт сеть, человек может записать новую тренировку,
 * поэтому каждый шаг применяется к свежим данным, а не к снимку.
 *
 * @param change атомарно меняет и сохраняет данные, возвращает новые.
 */
class TelegramSync(
    private val api: TelegramApi,
    private val zone: ZoneId,
    private val change: ((AppData) -> AppData) -> AppData,
) {
    /** Один проход синхронизации. Сетевые ошибки пробрасывает, сделанное до ошибки сохраняется. */
    fun run(): SyncReport {
        val received = receive()
        removeDeleted()
        val sent = updateEdited() + sendPending()
        change { it.copy(telegram = it.telegram.copy(lastSyncEpochSecond = Instant.now().epochSecond)) }
        return SyncReport(received, sent)
    }

    private fun receive(): Int {
        val before = change { it }.telegram.lastUpdateId
        val updates = api.getUpdates(before + 1)
        if (updates.isEmpty()) return 0
        var replies = emptyList<TelegramReply>()
        val data = change { current ->
            val fresh = updates.filter { it.updateId > current.telegram.lastUpdateId }
            val result = TelegramInbox.apply(current, fresh, zone)
            replies = result.replies
            result.data
        }
        data.telegram.chatId?.let { reply(it, replies) }
        return updates.count { it.message != null }
    }

    private fun reply(chatId: Long, replies: List<TelegramReply>) {
        replies.forEach { reply ->
            // Ответ не важнее записи: если Telegram его не принял, запись всё равно в Дневнике.
            runCatching {
                if (reply.text == null) {
                    api.react(chatId, reply.messageId, TelegramFormat.RECORDED)
                } else {
                    api.sendMessage(chatId, reply.text)
                }
            }
        }
    }

    private fun removeDeleted() {
        val telegram = change { it }.telegram
        val chatId = telegram.chatId ?: return
        telegram.pendingDeletes.forEach { messageId ->
            try {
                api.deleteMessage(chatId, messageId)
            } catch (old: TelegramException) {
                // Старше 48 часов бот удалить не может: сообщение остаётся, повторять бессмысленно.
                if (old.unauthorized) throw old
            }
            change { it.copy(telegram = it.telegram.copy(pendingDeletes = it.telegram.pendingDeletes - messageId)) }
        }
    }

    /** Переписывает копии исправленных записей. Бот может править свои сообщения любой давности. */
    private fun updateEdited(): Int {
        val data = change { it }
        val chatId = data.telegram.chatId ?: return 0
        val edited = data.sessions.filter { it.awaitsTelegramUpdate }
        edited.forEach { session ->
            val copyId = checkNotNull(session.telegramMessageId)
            val text = TelegramFormat.session(session)
            val messageId = try {
                api.editMessage(chatId, copyId, text)
                copyId
            } catch (failed: TelegramException) {
                when {
                    failed.unauthorized -> throw failed
                    failed.notModified -> copyId
                    // Копию удалили в чате: отправляем запись заново.
                    else -> api.sendMessage(chatId, text).messageId
                }
            }
            change { current -> markSent(current, session, messageId) }
        }
        return edited.size
    }

    private fun sendPending(): Int {
        val data = change { it }
        val chatId = data.telegram.chatId ?: return 0
        val pending = data.sessions.filter { it.awaitsTelegram }.sortedWith(compareBy({ it.epochDay }, { it.id }))
        pending.forEach { session ->
            val message = api.sendMessage(chatId, TelegramFormat.session(session))
            change { current -> markSent(current, session, message.messageId) }
        }
        return pending.size
    }

    /**
     * Запоминает копию записи [sent]. Если запись успели удалить, копию надо удалить тоже; если успели
     * исправить, копию надо обновить ещё раз.
     */
    private fun markSent(data: AppData, sent: WorkoutSession, messageId: Long): AppData {
        val current = data.sessions.firstOrNull { it.id == sent.id }
        return if (current == null) {
            val deletes = (data.telegram.pendingDeletes + messageId).distinct()
            data.copy(telegram = data.telegram.copy(pendingDeletes = deletes))
        } else {
            val marked = current.copy(telegramMessageId = messageId, telegramStale = !current.sameContent(sent))
            data.copy(sessions = data.sessions.map { if (it.id == sent.id) marked else it })
        }
    }
}
