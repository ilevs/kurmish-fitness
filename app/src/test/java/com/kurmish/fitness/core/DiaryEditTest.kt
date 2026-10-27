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
        assertEquals(SetGroup(102.5, 3), SetDraft("102,5", "3").toSetGroup())
        assertEquals(SetGroup(null, 10), SetDraft("", "10").toSetGroup())
        assertTrue(SetDraft("сто", "5").weightInvalid)
        assertNull(SetDraft("100", "0").toSetGroup())
    }

    @Test
    fun onlyDoneSetsAreSavedAndSameSetsMerge() {
        val entry = EntryDraft(
            Exercise.Squat,
            sets = listOf(
                SetDraft("120", "5", done = true),
                SetDraft("120", "5", done = true),
                SetDraft("125", "3", done = true),
                SetDraft("130", "3"),
                SetDraft(),
            ),
        )
        assertEquals(listOf(SetGroup(120.0, 5, 2), SetGroup(125.0, 3)), entry.toEntry()?.sets)
    }

    @Test
    fun brokenDoneSetBlocksSavingButUndoneExerciseIsSkipped() {
        val broken = EntryDraft(Exercise.Squat, sets = listOf(SetDraft("120", "", done = true)))
        assertTrue(broken.broken)
        assertTrue(EntryDraft(null, " ", listOf(SetDraft("", "10", done = true))).broken)
        val untouched = EntryDraft(Exercise.Deadlift, sets = listOf(SetDraft("150", "5")))
        val draft = SessionDraft.of(bench).copy(entries = SessionDraft.of(bench).entries + untouched)
        assertEquals(bench, draft.toSession())
        assertFalse(draft.copy(entries = listOf(untouched)).valid)
        assertFalse(SessionDraft.of(bench).copy(entries = emptyList()).valid)
    }

    @Test
    fun recordedSetsOpenOneByOneAndDone() {
        val sets = SessionDraft.of(bench).entries.single().sets
        assertEquals(List(3) { SetDraft("100", "5", done = true) }, sets)
    }

    @Test
    fun editingRepsMarksSetDoneAndNewSetCopiesLast() {
        assertTrue(SetDraft("100", "").edited(reps = "5").done)
        assertFalse(SetDraft("100", "5", done = true).edited(reps = "").done)
        val entry = EntryDraft(Exercise.Bench, sets = listOf(SetDraft("100", "5", done = true))).withSet()
        assertEquals(SetDraft("100", "5"), entry.sets.last())
    }

    @Test
    fun previousSetsComeFromLastSessionBeforeDay() {
        val earlier = bench.copy(id = 1, epochDay = bench.epochDay - 7)
        val data = AppData(sessions = listOf(earlier, bench))
        val entry = EntryDraft(Exercise.Bench, sets = emptyList())
        assertEquals(List(3) { SetGroup(100.0, 5) }, data.previousSets(entry, bench.epochDay, excludeId = 3))
        assertEquals(emptyList<SetGroup>(), data.previousSets(entry, bench.epochDay - 8, excludeId = 0))
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
