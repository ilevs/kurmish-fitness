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
    }

    @Test
    fun editReplacesSession() {
        val data = AppData(sessions = listOf(bench)).withSaved(bench.copy(epochDay = bench.epochDay - 1))
        assertEquals(bench.epochDay - 1, data.sessions.single().epochDay)
    }

    @Test
    fun saveWithoutChangesKeepsData() {
        val data = AppData(sessions = listOf(bench))
        assertSame(data, data.withSaved(bench))
    }

    @Test
    fun deleteRemovesSession() {
        val data = AppData(sessions = listOf(bench, bench.copy(id = 4))).withoutSession(3)
        assertEquals(listOf(4L), data.sessions.map { it.id })
    }
}
