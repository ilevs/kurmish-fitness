package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekSplitTest {
    private val maxes = mapOf(
        Exercise.Squat to 100.0,
        Exercise.Bench to 80.0,
        Exercise.Deadlift to 120.0,
        Exercise.OverheadPress to 50.0,
    )

    /** План тренировки по счёту [done] (с 0) при [days] тренировках в неделю: до неё записано [done] тренировок. */
    private fun plan(days: Int, done: Int): DayPlan {
        val sessions = (0 until done).map { WorkoutSession(it.toLong(), it.toLong(), "", emptyList()) }
        val profile = Profile(goal = Goal.Strength, daysPerWeek = days, manualMaxes = maxes)
        return Planner.today(AppData(profile, sessions), today = done.toLong())
    }

    private fun DayPlan.work(): List<PlanItem> = items.filter { it.part == PlanPart.Work }

    private fun DayPlan.setsOf(exercise: Exercise): PlannedSets = work().first { it.exercise == exercise }.sets.single()

    @Test
    fun everyFrequencyCoversAllMovementTypesInAWeek() {
        WeekSplit.days.forEach { days ->
            val week = (0 until maxOf(days, 2)).map { plan(days, it) }
            val movements = week.flatMap { day -> day.work().mapNotNull { it.exercise?.movement } }.toSet()
            val expected = Movement.entries.toSet() - Movement.Arms
            assertEquals("$days в неделю", expected, movements)
            week.forEach { day ->
                assertTrue("$days в неделю: ${day.title}", day.work().size >= 5)
                assertTrue(day.work().all { it.sets.isNotEmpty() })
            }
        }
    }

    @Test
    fun weeksRepeatForEveryFrequency() {
        WeekSplit.days.forEach { days ->
            val size = WeekSplit.week(days).size
            assertEquals(plan(days, 0).title, plan(days, size).title)
        }
    }

    @Test
    fun oneDayAlternatesFullBodyByWeeksAndWarnsItOnlyMaintains() {
        assertTrue(plan(1, 0).title.startsWith("Общая тренировка А"))
        assertTrue(plan(1, 1).title.startsWith("Общая тренировка Б"))
        assertTrue(plan(1, 0).note.orEmpty().contains("только поддерживает"))
        assertEquals(PlannedSets(5, "5", 80.0), plan(1, 0).setsOf(Exercise.Squat))
    }

    @Test
    fun twoDaysAlternateFullBodyWithRestBetween() {
        assertEquals(
            listOf("А", "Б", "А", "Б"),
            (0..3).map { plan(2, it).title.substringAfter("Общая тренировка ").take(1) },
        )
        assertTrue(plan(2, 0).note.orEmpty().contains("пн и чт"))
    }

    @Test
    fun fourDaysSplitUpperAndLowerWithOwnHeavyLift() {
        val week = (0..3).map { plan(4, it) }
        assertEquals(
            listOf(Exercise.Bench, Exercise.Squat, Exercise.OverheadPress, Exercise.Deadlift),
            week.map { it.work().first().exercise },
        )
        // Жим стоя тяжёлый в своём дне и 3 × 10 вторым упражнением в день жима лёжа.
        assertEquals(PlannedSets(5, "5", 40.0), week[2].setsOf(Exercise.OverheadPress))
        assertEquals("3 × 10", week[0].setsOf(Exercise.OverheadPress).label.substringBefore(" по"))
        assertEquals("3 × мах", week[0].setsOf(Exercise.PullUp).label)
    }

    @Test
    fun fiveDaysEndWithLightFullBodyBeforeRest() {
        val fifth = plan(5, 4)
        assertTrue(fifth.title.startsWith("Лёгкая тренировка Б"))
        assertEquals(PlannedSets(3, "12", 27.5), fifth.setsOf(Exercise.OverheadPress))
        assertEquals(PlannedSets(3, "6", 65.0), fifth.setsOf(Exercise.Deadlift))
        assertTrue(plan(5, 5).title.startsWith("Верх: жим лёжа"))
    }

    @Test
    fun sixDaysTrainEachHalfThreeTimesHeavyAndLight() {
        val week = (0..5).map { plan(6, it) }
        assertEquals(3, week.count { it.title.contains("ерх") })
        assertEquals(3, week.count { it.title.contains("из") })
        assertEquals(PlannedSets(5, "5", 65.0), week[0].setsOf(Exercise.Bench))
        assertEquals(PlannedSets(3, "12", 45.0), week[4].setsOf(Exercise.Bench))
        assertEquals(PlannedSets(3, "12", 55.0), week[5].setsOf(Exercise.Squat))
    }

    @Test
    fun sevenDaysKeepOneRecoveryDay() {
        val recovery = plan(7, 6)
        assertTrue(recovery.title.startsWith("Восстановительная тренировка"))
        assertTrue(recovery.work().all { item -> item.sets.all { it.weightKg == null } })
        assertTrue(recovery.work().all { item -> item.notes.any { it.contains("МФР") } })
        assertTrue(plan(7, 7).title.startsWith("Верх: жим лёжа"))
    }

    @Test
    fun macrocycleDatesFollowTrainingsPerWeek() {
        val twoDays = Profile(daysPerWeek = 2).withBlocks(listOf("maximum"), today = 0)
        val block = Macrocycle.blocks(twoDays, today = 0).single()
        // 20 тренировок по две в неделю — 10 недель, а не 5 микроциклов.
        assertEquals(10, block.weeks)
        assertEquals(69L, block.endEpochDay)
    }
}
