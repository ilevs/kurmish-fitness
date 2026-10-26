package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryEditTest {
    private val bench = WorkoutSession(
        id = 3,
        epochDay = 20_000,
        rawText = "жим 100 на 5 по 3",
        entries = listOf(ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(100.0, 5, 3)))),
    )

    @Test
    fun draftKeepsSessionAsIs() {
        val session = bench.copy(
            entries = bench.entries + ExerciseEntry(null, "планка", listOf(SetGroup(null, 60, 2))),
            feeling = Feeling.Hard,
        )
        assertEquals(session, SessionDraft.of(session).toSession())
    }

    @Test
    fun setFieldsAcceptCommaAndBodyweight() {
        assertEquals(SetGroup(102.5, 3, 2), SetDraft("102,5", "3", "2").toSetGroup())
        assertEquals(SetGroup(null, 10, 1), SetDraft("", "10", "").toSetGroup())
        assertTrue(SetDraft("сто", "5").weightInvalid)
        assertNull(SetDraft("100", "0").toSetGroup())
        assertNull(SetDraft("100", "5", "0").toSetGroup())
    }

    @Test
    fun blankRowsAreSkippedButBrokenRowsBlockSaving() {
        val entry = EntryDraft(Exercise.Squat, sets = listOf(SetDraft("120", "5"), SetDraft()))
        assertEquals(listOf(SetGroup(120.0, 5)), entry.toEntry()?.sets)
        assertNull(entry.copy(sets = listOf(SetDraft("120", "5"), SetDraft("130", ""))).toEntry())
        assertNull(EntryDraft(null, " ", listOf(SetDraft("", "10"))).toEntry())
        assertFalse(SessionDraft.of(bench).copy(entries = emptyList()).valid)
    }

    @Test
    fun reparseReplacesExercisesAndFeeling() {
        val draft = SessionDraft.of(bench).copy(rawText = "присед 120 на 5, было легко").reparsed()
        assertEquals(listOf(Exercise.Squat), draft.entries.map { it.exercise })
        assertEquals(Feeling.Easy, draft.feeling)
        assertEquals(bench.epochDay, draft.epochDay)
    }

    @Test
    fun newSessionGetsNextId() {
        val added = checkNotNull(SessionDraft.of(bench).copy(id = 0).toSession())
        val data = AppData(sessions = listOf(bench)).withSaved(added)
        assertEquals(listOf(3L, 4L), data.sessions.map { it.id })
        assertTrue(data.sessions.last().awaitsTelegram)
    }

    @Test
    fun editOfSentSessionMarksCopyStale() {
        val sent = bench.copy(telegramMessageId = 50)
        val data = AppData(sessions = listOf(sent)).withSaved(bench.copy(epochDay = bench.epochDay - 1))
        val edited = data.sessions.single()
        assertEquals(bench.epochDay - 1, edited.epochDay)
        assertEquals(50L, edited.telegramMessageId)
        assertTrue(edited.awaitsTelegramUpdate)
    }

    @Test
    fun saveWithoutChangesKeepsData() {
        val data = AppData(sessions = listOf(bench.copy(telegramMessageId = 50)))
        assertSame(data, data.withSaved(bench))
    }

    @Test
    fun editedTelegramSessionLivesOnItsOwn() {
        val fromBot = bench.copy(telegramParts = listOf(TelegramPart(10, "жим 100 на 5"), TelegramPart(11, "по 3")))
        val data = AppData(sessions = listOf(fromBot)).withSaved(bench.copy(feeling = Feeling.Easy))
        val edited = data.sessions.single()
        assertFalse(edited.fromTelegram)
        assertTrue(edited.awaitsTelegram)
        assertEquals(listOf(10L, 11L), data.telegram.detachedMessages)
    }

    @Test
    fun deleteQueuesCopyAndDetachesMessages() {
        val sent = bench.copy(telegramMessageId = 50)
        val fromBot = bench.copy(id = 4, telegramParts = listOf(TelegramPart(10, "жим 100 на 5")))
        val data = AppData(sessions = listOf(sent, fromBot)).withoutSession(3).withoutSession(4)
        assertTrue(data.sessions.isEmpty())
        assertEquals(listOf(50L), data.telegram.pendingDeletes)
        assertEquals(listOf(10L), data.telegram.detachedMessages)
    }
}
