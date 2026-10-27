package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WavesTest {
    private val program = WavePrograms.highRep
    private val bench = Exercise.Bench

    private fun day(id: Long, weight: Double, reps: Int) = WorkoutSession(
        id,
        epochDay = 100 + id,
        rawText = "",
        entries = listOf(ExerciseEntry(bench, sets = listOf(SetGroup(weight, reps, 3)))),
    )

    private fun other(id: Long) = WorkoutSession(id, epochDay = 100 + id, rawText = "", entries = emptyList())

    /** Микроцикл из трёх тренировок: жим в первой и третьей. */
    private fun week(start: Long, reps: Int) =
        listOf(day(start, 100.0, reps), other(start + 1), day(start + 2, 100.0, reps))

    @Test
    fun ladderPercentsGrowEveryMicrocycle() {
        val rule = WaveRule(
            sets = 4,
            reps = 10,
            startPercent = 40.0,
            setStepPercent = 7.0,
            incrementPercent = 3.5,
            endReps = 2,
        )
        assertEquals(listOf(40.0, 47.0, 54.0, 61.0), rule.percents(0))
        assertEquals(listOf(47.0, 54.0, 61.0, 68.0), rule.percents(2))
        val sets = Waves.sets(rule, step = 0, maxKg = 300.0, barbell = true)
        assertEquals(listOf(120.0, 140.0, 162.5, 182.5), sets.map { it.weightKg })
        assertEquals(PlannedSets(4, "10", null), Waves.sets(rule, 0, null, barbell = true).single())
    }

    @Test
    fun weightGrowsWhateverRepsUntilWaveEnds() {
        val history = week(1, 12) + week(4, 8)
        val at = Waves.position(program.waves, days = 3, history = history)
        assertEquals(0, at.wave)
        assertEquals(2, at.step)
        assertNull(at.record)
    }

    @Test
    fun twoRepsEndWaveAndRollBackToSecondMicrocycle() {
        val history = week(1, 10) + week(4, 2)
        val ended = Waves.position(program.waves, days = 3, history = history.take(4))
        assertEquals(0, ended.wave)
        assertEquals(100.0, ended.record)
        val next = Waves.position(program.waves, days = 3, history = history)
        assertEquals(1, next.wave)
        assertEquals(1, next.step)
        assertEquals(100.0, next.record)
    }

    @Test
    fun programEndsAfterTwoWavesOfLastPhase() {
        val history = week(1, 2) + week(4, 2)
        assertEquals(6, Waves.position(program.waves, days = 3, history = history).length)
        assertNull(Waves.position(program.waves, days = 3, history = history.dropLast(1)).length)
    }

    @Test
    fun worstSetEndsLinearWaveWhenSchemeFails() {
        val failed = WorkoutSession(
            1,
            epochDay = 1,
            rawText = "",
            entries = listOf(ExerciseEntry(bench, sets = listOf(SetGroup(80.0, 5, 3), SetGroup(80.0, 4, 2)))),
        )
        assertEquals(4, Waves.topSet(failed, bench, worst = true)?.reps)
        assertEquals(5, Waves.topSet(failed, bench)?.reps)
    }

    @Test
    fun plannerFollowsWavesInMacrocycle() {
        val profile = Profile(programId = program.id, programStartEpochDay = 101, manualMaxes = mapOf(bench to 100.0))
        val data = AppData(profile, week(1, 10))
        val plan = Planner.today(data, today = 110)
        val title = "Д1 · жим и присед · фаза 1 из 1 (3×15), волна 1 из 2, микроцикл 2"
        assertEquals(title, plan.title)
        val press = plan.items.first { it.part == PlanPart.Work }
        assertEquals(listOf(PlannedSets(3, "15", 60.0)), press.sets)
        assertTrue(press.notes.any { it.startsWith("Волна кончится") })
        assertTrue(Programs.all.contains(program))
        assertTrue(program.summary.contains("≈"))
    }

    @Test
    fun macrocycleMovesOnWhenWavesAreDone() {
        val profile = Profile(
            programId = program.id,
            nextProgramIds = listOf(Programs.maximum.id),
            programStartEpochDay = 101,
        )
        val history = week(1, 2) + week(4, 2) + other(7)
        val position = Macrocycle.position(profile, history, today = 120)
        assertEquals(1, position?.index)
        assertEquals(1, position?.progress?.done)
    }
}
