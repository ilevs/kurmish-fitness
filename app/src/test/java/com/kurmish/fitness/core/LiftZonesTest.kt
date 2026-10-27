package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftZonesTest {
    @Test
    fun zonesFollowPercentOfMax() {
        assertNull(LiftZones.zoneOf(50.0, 100.0))
        assertEquals("55–70 %", LiftZones.zoneOf(60.0, 100.0)?.title)
        assertEquals("70–80 %", LiftZones.zoneOf(75.0, 100.0)?.title)
        assertEquals("от 90 %", LiftZones.zoneOf(95.0, 100.0)?.title)
    }

    @Test
    fun countsLiftsPerZoneAndVerdict() {
        val sets = listOf(SetGroup(75.0, 5, 6), SetGroup(85.0, 2, 2), SetGroup(40.0, 10))
        val squat = ExerciseEntry(Exercise.Squat, sets = sets)
        val counts = LiftZones.count(listOf(squat)) { 100.0 }
        assertEquals(2, counts.size)
        val medium = counts.first { it.zone.fromPercent == 70 }
        assertEquals(30, medium.lifts)
        assertEquals(ZoneVerdict.Above, medium.verdict)
        assertEquals("Присед, 70–80 %: 30 подъёмов — больше коридора 12–24 (оптимум 18)", medium.line)
        assertEquals(ZoneVerdict.Below, counts.first { it.zone.fromPercent == 80 }.verdict)
    }

    @Test
    fun lastSessionUsesCurrentMax() {
        val profile = Profile(manualMaxes = mapOf(Exercise.Bench to 100.0))
        val session = WorkoutSession(
            1,
            epochDay = 10,
            rawText = "",
            entries = listOf(ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(80.0, 3, 5)))),
        )
        val (day, counts) = LiftZones.lastSession(AppData(profile, listOf(session)), today = 12) ?: error("нет данных")
        assertEquals(10, day)
        assertEquals(ZoneVerdict.Inside, counts.single().verdict)
    }

    @Test
    fun ownCycleWarnsAboutZoneVolume() {
        val squat = DesignItem("Присед", listOf(DesignWeek("10×5", listOf(75))))
        val design = CycleDesign(id = "my-1", title = "Мой", days = listOf(DesignDay("Д1", listOf(squat))))
        val maxes = mapOf(Exercise.Squat to 100.0)
        val profile = Profile(manualMaxes = maxes, designs = listOf(design), programId = "my-1")
        val plan = Planner.today(AppData(profile), today = 5)
        val item = plan.items.first { it.exercise == Exercise.Squat && it.part == PlanPart.Work }
        assertTrue(item.notes.toString(), item.notes.any { it.startsWith("КПШ 70–80 %: 50 подъёмов") })
    }
}
