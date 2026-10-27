package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChecksTest {
    private val monday = LocalDate.of(2026, 10, 26).toEpochDay()

    private fun session(id: Long, day: Long, vararg entries: ExerciseEntry) =
        WorkoutSession(id, day, "", entries.toList())

    private fun entry(exercise: Exercise, weight: Double?, reps: Int, sets: Int = 3) =
        ExerciseEntry(exercise, sets = listOf(SetGroup(weight, reps, sets)))

    /** Тренировка на все плоскости, в которой все проверки молчат. */
    private fun full(id: Long, day: Long, bench: Double = 80.0) = session(
        id,
        day,
        entry(Exercise.Bench, bench, 5),
        entry(Exercise.Squat, 100.0, 4),
        entry(Exercise.Deadlift, 120.0, 3),
        entry(Exercise.BentOverRow, 60.0, 5),
        entry(Exercise.OverheadPress, 40.0, 8),
        entry(Exercise.PullUp, null, 5),
        entry(Exercise.FacePull, 10.0, 15),
    )

    private fun lines(vararg sessions: WorkoutSession, today: Long = monday) =
        Checks.lines(AppData(sessions = sessions.toList()), today)

    @Test
    fun balancedProgramPasses() {
        val sessions = listOf(0L, 2L, 4L, 7L, 9L, 11L).mapIndexed { i, day -> full(i.toLong(), monday + day) }
        assertEquals(emptyList<String>(), lines(*sessions.toTypedArray(), today = monday + 11))
    }

    @Test
    fun inwardRotationExcessIsReported() {
        val result = lines(
            session(
                1,
                monday,
                entry(Exercise.Bench, 80.0, 10),
                entry(Exercise.PullUp, null, 10),
                entry(Exercise.FacePull, 10.0, 5),
            ),
        )
        assertTrue(result.any { it.startsWith("Плечо вращается внутрь в 4 раза") })
    }

    @Test
    fun baseVolumeOrderIsChecked() {
        val result = lines(
            session(
                1,
                monday,
                entry(Exercise.Bench, 80.0, 3),
                entry(Exercise.Squat, 100.0, 5),
                entry(Exercise.Deadlift, 120.0, 8),
            ),
        )
        assertTrue(result.any { it.startsWith("Становой за 4 недели больше") })
        assertTrue(result.any { it.startsWith("Приседа за 4 недели больше") })
    }

    @Test
    fun weeklyJumpOverFivePercentIsReported() {
        val result = lines(full(1, monday - 7, bench = 80.0), full(2, monday, bench = 90.0))
        assertTrue(result.any { it.startsWith("«Жим лёжа»: рабочий вес вырос за неделю с 80 кг до 90 кг (+13 %)") })
        val smallStep = lines(full(1, monday - 7, bench = 80.0), full(2, monday, bench = 82.5))
        assertTrue(smallStep.none { it.contains("рабочий вес вырос") })
    }

    @Test
    fun eightHardWeeksAskForDeload() {
        val hard = (0L until Checks.DELOAD_WEEKS).map { full(it, monday - 7 * (it + 1)) }
        assertTrue(lines(*hard.toTypedArray()).any { it.startsWith("8 недель подряд без лёгкой недели") })
        val light = hard.mapIndexed { i, s ->
            if (i == 3) s.copy(entries = s.entries.map { it.copy(sets = listOf(SetGroup(20.0, 5))) }) else s
        }
        assertTrue(lines(*light.toTypedArray()).none { it.contains("без лёгкой недели") })
    }

    @Test
    fun rarePlaneIsReported() {
        val withPress = full(1, monday)
        val withoutPress = listOf(2L, 4L, 7L).map { d ->
            val s = full(d, monday + d)
            s.copy(entries = s.entries.filter { it.exercise != Exercise.OverheadPress })
        }
        val result = lines(withPress, *withoutPress.toTypedArray(), today = monday + 7)
        assertTrue(result.any { it.startsWith("«Жим вверх, брусья» за 2 недели только 1 раз") })
    }

    @Test
    fun longSessionIsReported() {
        val long = full(1, monday).let {
            it.copy(entries = it.entries + entry(Exercise.BicepsCurl, 15.0, 10) + entry(Exercise.Plank, null, 1))
        }
        assertTrue(lines(long).any { it.startsWith("В последней тренировке 9 упражнений") })
    }

    @Test
    fun plateauCoversEveryWeightedExercise() {
        val old = session(1, monday - 40, entry(Exercise.OverheadPress, 50.0, 5))
        val recent = (0L..2L).map { session(it + 2, monday - it * 3, entry(Exercise.OverheadPress, 45.0, 5)) }
        assertTrue(lines(old, *recent.toTypedArray()).any { it.startsWith("«Жим стоя» 4 недели без роста") })
    }
}
