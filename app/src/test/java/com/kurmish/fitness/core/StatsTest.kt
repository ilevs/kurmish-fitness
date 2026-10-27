package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatsTest {
    private val monday = LocalDate.of(2026, 10, 26).toEpochDay()

    private fun session(id: Long, day: Long, vararg entries: ExerciseEntry) =
        WorkoutSession(id, day, "", entries.toList())

    private fun bench(vararg sets: SetGroup) = ExerciseEntry(Exercise.Bench, sets = sets.toList())

    @Test
    fun weekStartsOnMonday() {
        (0L..6L).forEach { shift ->
            assertEquals(monday, Stats.weekStart(monday + shift))
        }
        assertEquals(monday - 7, Stats.weekStart(monday - 1))
        assertEquals(DayOfWeek.MONDAY, LocalDate.ofEpochDay(Stats.weekStart(0)).dayOfWeek)
    }

    @Test
    fun weeksSumLoadAndKeepEmptyWeeks() {
        val data = AppData(
            sessions = listOf(
                session(1, monday + 1, bench(SetGroup(100.0, 5, 3))),
                session(2, monday + 3, bench(SetGroup(50.0, 10))),
                session(3, monday - 14, bench(SetGroup(80.0, 5))),
            ),
        )
        val weeks = Stats.weeks(data, today = monday + 4, count = 4)
        assertEquals(listOf(monday - 21, monday - 14, monday - 7, monday), weeks.map { it.weekStart })
        assertEquals(listOf(0, 1, 0, 2), weeks.map { it.sessions })
        assertEquals(2000.0, weeks.last().tonnageKg, 0.0)
        assertEquals(25, weeks.last().lifts)
        assertNull(weeks[2].relativeIntensity)
    }

    @Test
    fun weeklyIntensityIsWeightedByLifts() {
        val data = AppData(
            profile = Profile(manualMaxes = mapOf(Exercise.Bench to 100.0)),
            sessions = listOf(
                session(1, monday, bench(SetGroup(80.0, 5, 3))),
                session(2, monday + 2, bench(SetGroup(50.0, 5))),
            ),
        )
        val week = Stats.weeks(data, today = monday + 2, count = 1).single()
        // 15 повторов по 80 % и 5 по 50 %.
        assertEquals((0.8 * 15 + 0.5 * 5) / 20, week.relativeIntensity!!, 1e-9)
    }

    @Test
    fun maxHistoryTakesBestSetPerDay() {
        val data = AppData(
            sessions = listOf(
                session(2, monday + 3, bench(SetGroup(105.0, 1))),
                session(1, monday, bench(SetGroup(90.0, 3), SetGroup(100.0, 1), SetGroup(60.0, 15))),
                session(3, monday + 5, ExerciseEntry(Exercise.Squat, sets = listOf(SetGroup(140.0, 2)))),
            ),
        )
        val history = Stats.maxHistory(data, Exercise.Bench)
        assertEquals(listOf(monday, monday + 3), history.map { it.epochDay })
        assertEquals(listOf(100.0, 105.0), history.map { it.kg })
        // Жим лёжа делали два дня, присед один.
        assertEquals(listOf(Exercise.Bench, Exercise.Squat), Stats.exercisesWithMaxes(data))
    }

    @Test
    fun balanceCountsRepsByMovement() {
        val data = AppData(
            sessions = listOf(
                session(
                    1,
                    monday,
                    bench(SetGroup(100.0, 5, 3)),
                    ExerciseEntry(Exercise.PullUp, sets = listOf(SetGroup(null, 8, 2))),
                    ExerciseEntry(null, customName = "махи", sets = listOf(SetGroup(10.0, 20))),
                ),
                session(2, monday - 40, ExerciseEntry(Exercise.Squat, sets = listOf(SetGroup(100.0, 5)))),
            ),
        )
        val balance = Stats.balance(data, today = monday + 1)
        assertEquals(15, balance[Movement.HorizontalPush])
        assertEquals(16, balance[Movement.VerticalPull])
        assertEquals(0, balance[Movement.Squat])
        assertEquals(Movement.entries.size, balance.size)
    }

    @Test
    fun simpleExercisesWeighLessInPairs() {
        val data = AppData(
            sessions = listOf(
                session(
                    1,
                    monday,
                    bench(SetGroup(80.0, 10, 3)),
                    ExerciseEntry(Exercise.SeatedCableRow, sets = listOf(SetGroup(50.0, 10, 3))),
                    ExerciseEntry(Exercise.FacePull, sets = listOf(SetGroup(10.0, 10, 3))),
                ),
            ),
        )
        val balance = Stats.balance(data, today = monday)
        assertEquals(30, balance[Movement.HorizontalPush])
        // Тяга в тренажёре считается с коэффициентом 0,6: 30 повторов не уравновешивают 30 жимов штанги.
        assertEquals(18, balance[Movement.HorizontalPull])
        // Вращатели вне пар считаются как есть.
        assertEquals(30, balance[Movement.Rotation])
    }

    @Test
    fun balanceIssuesFollowOppositePairs() {
        val balance = Movement.entries.associateWith { 0 } + mapOf(
            Movement.HorizontalPush to 30,
            Movement.HorizontalPull to 15,
            Movement.VerticalPush to 20,
            Movement.VerticalPull to 10,
            Movement.Squat to 10,
        )
        val issues = Stats.balanceIssues(balance)
        assertEquals(
            listOf(
                BalanceIssue.Excess(Movement.HorizontalPush, Movement.HorizontalPull),
                BalanceIssue.Missing(Movement.Hinge),
                BalanceIssue.Missing(Movement.Rotation),
                BalanceIssue.Missing(Movement.Core),
            ),
            issues,
        )
        // Больше вертикальных жимов, чем тяг, — допустимый перекос, о нём не предупреждаем.
        assertTrue(issues.none { it is BalanceIssue.Excess && it.more == Movement.VerticalPush })
    }

    @Test
    fun pairsShowShiftAndNorm() {
        val balance = mapOf(
            Movement.HorizontalPush to 30,
            Movement.HorizontalPull to 15,
            Movement.VerticalPull to 20,
            Movement.Squat to 10,
            Movement.Hinge to 20,
        )
        val (horizontal, vertical, legs) = Stats.pairs(balance)
        assertEquals(-1.0 / 3, horizontal.shift!!, 1e-9)
        assertFalse(horizontal.balanced)
        assertFalse(vertical.balanced)
        assertEquals(Movement.VerticalPush, vertical.right)
        assertTrue(legs.balanced)
        assertNull(OppositePair(Movement.Squat, Movement.Hinge, 0, 0).shift)
    }

    @Test
    fun balanceAdviceWaitsForThreeWorkouts() {
        val sessions = (1L..3L).map { WorkoutSession(it, epochDay = 100 + it, rawText = "", entries = emptyList()) }
        assertFalse(Stats.enoughForBalance(AppData(sessions = sessions.take(2)), today = 110))
        assertTrue(Stats.enoughForBalance(AppData(sessions = sessions), today = 110))
        assertFalse(Stats.enoughForBalance(AppData(sessions = sessions), today = 140))
    }

    @Test
    fun balancedWeekHasNoIssuesAndCalvesAreIgnored() {
        val balance = Movement.entries.associateWith { 10 }
        assertTrue(Stats.balanceIssues(balance).isEmpty())
        assertTrue(Stats.balanceIssues(Movement.entries.associateWith { 0 }).isEmpty())
        val calves = AppData(
            sessions = listOf(
                session(1, monday, ExerciseEntry(Exercise.StandingCalfRaise, sets = listOf(SetGroup(50.0, 20, 3)))),
            ),
        )
        assertEquals(0, Stats.balance(calves, today = monday)[Movement.Squat])
    }

    @Test
    fun summaryOfRecentWeeks() {
        val data = AppData(
            sessions = listOf(
                session(1, monday - 2, bench(SetGroup(100.0, 5))),
                session(2, monday - 10, bench(SetGroup(100.0, 5))),
                session(3, monday - 30, bench(SetGroup(100.0, 5))),
            ),
        )
        val summary = Stats.summary(data, today = monday)
        assertEquals(2, summary.sessions)
        assertEquals(0.5, summary.perWeek, 1e-9)
        assertEquals(1000.0, summary.tonnageKg, 0.0)
        assertEquals(2L, summary.daysSinceLast)
        assertNull(Stats.summary(AppData(), monday).daysSinceLast)
    }

    @Test
    fun everyExerciseHasMovement() {
        Exercise.entries.forEach { it.movement }
        assertEquals(Movement.Hinge, Exercise.Deadlift.movement)
    }
}
