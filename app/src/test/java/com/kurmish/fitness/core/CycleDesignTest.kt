package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CycleDesignTest {
    private fun sessions(count: Int) = (1..count).map {
        WorkoutSession(it.toLong(), epochDay = it.toLong(), rawText = "", entries = emptyList())
    }

    @Test
    fun copyOfProgramGivesSameSessions() {
        listOf(Programs.maximum, Programs.hypertrophy).forEach { program ->
            val copy = CycleDesign.copyOf(program, "my-1").toProgram()
            assertEquals(program.sessions, copy.sessions)
            assertEquals(program.daysPerWeek, copy.daysPerWeek)
        }
    }

    @Test
    fun editsFlowIntoProgram() {
        val design = CycleDesign.copyOf(Programs.hypertrophy, "my-1")
        val day = design.days.first()
        val squat = day.items.first().withWeek(0, DesignWeek.parse("5×5", "70", inPercents = true))
        val extra = design.newItem("Пуловер").withWeek(0, DesignWeek.parse("3×12", "22,5 кг", inPercents = false), true)
        val edited = design.withDay(0, day.withItem(0, squat).withItem(day.items.size, extra).moved(day.items.size, -1))
            .withMicrocycles(5)
        val first = edited.toProgram().sessions.first()
        assertEquals(ProgramItem("Присед с низкой штангой", "5×5", Exercise.Squat, listOf(70)), first.items[0])
        assertEquals(ProgramItem("Пуловер", "3×12", null, emptyList(), 22.5), first.items[day.items.size - 1])
        assertEquals(5, edited.toProgram().microcycles)
        assertEquals(edited.days[0].items[0].weeks[3], edited.days[0].items[0].weeks[4])
    }

    @Test
    fun oldEquipmentSlotsMoveToNewNames() {
        val profile = Profile(pool = mapOf("Жим лёжа в слинге" to Exercise.DumbbellPress)).migrated()
        assertEquals(mapOf("Жим штанги лёжа" to Exercise.DumbbellPress), profile.pool)
    }

    @Test
    fun weightInKgGoesToPlan() {
        val item = ProgramItem("Пуловер", "3×12", null, emptyList(), 22.5)
        val planned = Planner.fromProgram(item, Exercise.Pullover, WorkingMax(100.0))
        assertEquals(listOf(PlannedSets(3, "12", 22.5)), planned.sets)
    }

    @Test
    fun macrocycleRunsBlocksOneAfterAnother() {
        val design = CycleDesign.copyOf(Programs.hypertrophy, CycleDesign.nextId(emptyList()))
        val profile = Profile().withDesign(design).withBlocks(listOf("maximum", design.id), today = 1)
        assertEquals(listOf("maximum", "my-1"), profile.blockIds)
        val blocks = Macrocycle.blocks(profile, today = 1)
        assertEquals(36L, blocks[1].startEpochDay)
        val inSecond = Macrocycle.position(profile, sessions(21), today = 30)
        assertEquals(1, inSecond?.index)
        assertEquals(1, inSecond?.progress?.done)
        assertNull(Macrocycle.position(profile, sessions(40), today = 60)?.progress?.next)
        val plan = Planner.today(AppData(profile, sessions(21)), today = 30)
        assertEquals("Тренировка Б, микроцикл 1 из 4", plan.title)
        assertEquals(listOf("maximum"), profile.withoutDesign(design.id, today = 30).blockIds)
    }
}
