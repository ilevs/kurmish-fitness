package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

class TelegramSyncTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 26)
    private val noon = day.atTime(12, 0).toEpochSecond(zone)
    private val owner = TgChat(id = 42, type = "private")

    private fun message(id: Long, text: String?, chat: TgChat = owner) =
        TgMessage(messageId = id, date = noon, chat = chat, text = text)

    private fun connected(vararg sessions: WorkoutSession) =
        AppData(sessions = sessions.toList(), telegram = TelegramState(token = "t", chatId = owner.id))

    @Test
    fun firstPrivateChatBecomesOwner() {
        val result = TelegramInbox.apply(
            data = AppData(telegram = TelegramState(token = "t")),
            updates = listOf(TgUpdate(updateId = 7, message = message(1, "/start"))),
            zone = zone,
        )
        assertEquals(owner.id, result.data.telegram.chatId)
        assertEquals(7L, result.data.telegram.lastUpdateId)
        assertEquals(TelegramFormat.HELP, result.replies.single().text)
    }

    @Test
    fun strangersAreIgnored() {
        val stranger = TgChat(id = 99, type = "private")
        val result = TelegramInbox.apply(
            data = connected(),
            updates = listOf(TgUpdate(updateId = 1, message = message(1, "жим 100 на 5", stranger))),
            zone = zone,
        )
        assertTrue(result.data.sessions.isEmpty())
        assertTrue(result.replies.isEmpty())
        assertEquals(1L, result.data.telegram.lastUpdateId)
    }

    @Test
    fun messagesOfOneDayMakeOneSession() {
        val result = TelegramInbox.apply(
            data = connected(),
            updates = listOf(
                TgUpdate(updateId = 1, message = message(10, "присед 120х5х3")),
                TgUpdate(updateId = 2, message = message(11, "жим 90 на 5, было тяжело")),
            ),
            zone = zone,
        )
        val session = result.data.sessions.single()
        assertEquals(day.toEpochDay(), session.epochDay)
        assertEquals(listOf(Exercise.Squat, Exercise.Bench), session.entries.map { it.exercise })
        assertEquals(Feeling.Hard, session.feeling)
        assertTrue(session.fromTelegram)
        assertTrue(result.replies.all { it.text == null })
    }

    @Test
    fun yesterdayGoesToPreviousDay() {
        val result = TelegramInbox.apply(
            data = connected(),
            updates = listOf(TgUpdate(updateId = 1, message = message(10, "вчера тяга 180 на 3"))),
            zone = zone,
        )
        assertEquals(day.toEpochDay() - 1, result.data.sessions.single().epochDay)
    }

    @Test
    fun editReplacesPartAndEmptyEditRemovesSession() {
        val first = TelegramInbox.apply(
            data = connected(),
            updates = listOf(TgUpdate(updateId = 1, message = message(10, "жим 100 на 5"))),
            zone = zone,
        ).data
        val edited = TelegramInbox.apply(
            data = first,
            updates = listOf(TgUpdate(updateId = 2, editedMessage = message(10, "жим 105 на 5"))),
            zone = zone,
        ).data
        assertEquals(105.0, edited.sessions.single().entries.single().sets.single().weightKg)
        val cleared = TelegramInbox.apply(
            data = edited,
            updates = listOf(TgUpdate(updateId = 3, editedMessage = message(10, "ошибся"))),
            zone = zone,
        )
        assertTrue(cleared.data.sessions.isEmpty())
        assertEquals(TelegramFormat.NOT_UNDERSTOOD, cleared.replies.single().text)
    }

    @Test
    fun voiceGetsTextOnlyReply() {
        val result = TelegramInbox.apply(
            data = connected(),
            updates = listOf(TgUpdate(updateId = 1, message = message(10, null))),
            zone = zone,
        )
        assertEquals(TelegramFormat.TEXT_ONLY, result.replies.single().text)
    }

    @Test
    fun formatShowsDateSetsAndFeeling() {
        val session = WorkoutSession(
            id = 1,
            epochDay = day.toEpochDay(),
            rawText = "",
            entries = listOf(ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(100.0, 5, 3)))),
            feeling = Feeling.Easy,
        )
        val expected = "📒 Понедельник, 26 октября\nЖим лёжа: 100 кг × 5 × 3\nБыло легко"
        assertEquals(expected, TelegramFormat.session(session))
    }

    @Test
    fun syncReceivesSendsAndDeletes() {
        val local = WorkoutSession(id = 1, epochDay = day.toEpochDay(), rawText = "", entries = emptyList())
        val api = FakeApi(listOf(TgUpdate(updateId = 5, message = message(10, "присед 100 на 5"))))
        var data = connected(local).let { it.copy(telegram = it.telegram.copy(pendingDeletes = listOf(3))) }
        val report = TelegramSync(api, zone) { transform -> transform(data).also { data = it } }.run()

        assertEquals(SyncReport(received = 1, sent = 1), report)
        assertEquals(listOf(3L), api.deleted)
        assertEquals(listOf(10L), api.reactions)
        assertEquals(1, api.sent.size)
        assertEquals(api.nextId - 1, data.sessions.first { it.id == 1L }.telegramMessageId)
        assertTrue(data.sessions.none { it.awaitsTelegram })
        assertTrue(data.telegram.pendingDeletes.isEmpty())
        assertEquals(5L, data.telegram.lastUpdateId)
    }

    @Test
    fun offlineKeepsQueue() {
        val local = WorkoutSession(id = 1, epochDay = day.toEpochDay(), rawText = "", entries = emptyList())
        var data = connected(local)
        val sync = TelegramSync(OfflineApi, zone) { transform -> transform(data).also { data = it } }
        val failure = runCatching { sync.run() }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertNull(data.sessions.single().telegramMessageId)
        assertNull(data.telegram.lastSyncEpochSecond)
    }

    @Test
    fun editedSessionUpdatesItsCopy() {
        val edited = sentBench.copy(feeling = Feeling.Hard, telegramStale = true)
        val api = FakeApi(emptyList())
        var data = connected(edited)
        val report = TelegramSync(api, zone) { transform -> transform(data).also { data = it } }.run()

        assertEquals(SyncReport(received = 0, sent = 1), report)
        assertEquals(listOf(50L), api.edited)
        assertTrue(api.sent.isEmpty())
        assertEquals(50L, data.sessions.single().telegramMessageId)
        assertFalse(data.sessions.single().telegramStale)
    }

    @Test
    fun copyDeletedInChatIsSentAgain() {
        val api = FakeApi(emptyList(), editError = TelegramException("Bad Request: message to edit not found", 400))
        var data = connected(sentBench.copy(telegramStale = true))
        TelegramSync(api, zone) { transform -> transform(data).also { data = it } }.run()

        assertEquals(1, api.sent.size)
        assertEquals(api.nextId - 1, data.sessions.single().telegramMessageId)
        assertFalse(data.sessions.single().awaitsTelegramUpdate)
    }

    @Test
    fun unchangedCopyIsNotSentAgain() {
        val api = FakeApi(emptyList(), editError = TelegramException("Bad Request: message is not modified", 400))
        var data = connected(sentBench.copy(telegramStale = true))
        TelegramSync(api, zone) { transform -> transform(data).also { data = it } }.run()

        assertTrue(api.sent.isEmpty())
        assertEquals(50L, data.sessions.single().telegramMessageId)
        assertFalse(data.sessions.single().telegramStale)
    }

    @Test
    fun editOfMessageDetachedInAppIsIgnored() {
        val session = sentBench.copy(telegramMessageId = null)
        val data = connected(session).let { it.copy(telegram = it.telegram.copy(detachedMessages = listOf(10))) }
        val result = TelegramInbox.apply(
            data = data,
            updates = listOf(TgUpdate(updateId = 1, editedMessage = message(10, "жим 120 на 5"))),
            zone = zone,
        )
        assertEquals(listOf(session), result.data.sessions)
        assertEquals(TelegramFormat.EDITED_IN_APP, result.replies.single().text)
    }

    private val sentBench = WorkoutSession(
        id = 1,
        epochDay = day.toEpochDay(),
        rawText = "жим 100 на 5",
        entries = listOf(ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(100.0, 5)))),
        telegramMessageId = 50,
    )

    private class FakeApi(
        private val updates: List<TgUpdate>,
        private val editError: TelegramException? = null,
    ) : TelegramApi {
        val sent = mutableListOf<String>()
        val edited = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()
        val reactions = mutableListOf<Long>()
        var nextId = 100L

        override fun getMe() = TgUser(id = 1, username = "bot")

        override fun getUpdates(offset: Long) = updates.filter { it.updateId >= offset }

        override fun sendMessage(chatId: Long, text: String): TgMessage {
            sent += text
            return TgMessage(messageId = nextId++, chat = TgChat(chatId))
        }

        override fun editMessage(chatId: Long, messageId: Long, text: String) {
            editError?.let { throw it }
            edited += messageId
        }

        override fun deleteMessage(chatId: Long, messageId: Long) {
            deleted += messageId
        }

        override fun react(chatId: Long, messageId: Long, emoji: String) {
            reactions += messageId
        }
    }

    private object OfflineApi : TelegramApi {
        override fun getMe(): TgUser = throw IOException("offline")

        override fun getUpdates(offset: Long): List<TgUpdate> = throw IOException("offline")

        override fun sendMessage(chatId: Long, text: String): TgMessage = throw IOException("offline")

        override fun editMessage(chatId: Long, messageId: Long, text: String): Unit = throw IOException("offline")

        override fun deleteMessage(chatId: Long, messageId: Long): Unit = throw IOException("offline")

        override fun react(chatId: Long, messageId: Long, emoji: String): Unit = throw IOException("offline")
    }
}
