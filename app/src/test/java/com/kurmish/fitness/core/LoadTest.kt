package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadTest {
    private fun session(vararg entries: ExerciseEntry) = WorkoutSession(1, 0, "", entries.toList())

    @Test
    fun tonnageLiftsAndAverage() {
        val load = LoadCalculator.session(
            session(ExerciseEntry(Exercise.Bench, sets = listOf(SetGroup(100.0, 5, 3), SetGroup(60.0, 10)))),
        ) { null }
        assertEquals(2100.0, load.tonnageKg, 0.0)
        assertEquals(25, load.lifts)
        assertEquals(84.0, load.averageKg, 0.0)
        assertNull(load.relativeIntensity)
    }

    @Test
    fun weightedIntensityUsesZoneTable() {
        assertEquals(0.0, LoadCalculator.zoneFactor(0.25), 0.0)
        assertEquals(0.18, LoadCalculator.zoneFactor(0.30), 0.0)
        assertEquals(0.47, LoadCalculator.zoneFactor(0.49), 0.0)
        assertEquals(1.0, LoadCalculator.zoneFactor(0.72), 0.0)
        assertEquals(2.8, LoadCalculator.zoneFactor(0.90), 0.0)
        assertEquals(25.0, LoadCalculator.zoneFactor(1.05), 0.0)
    }

    @Test
    fun intensityAndTimeWithMax() {
        val load = LoadCalculator.session(
            session(ExerciseEntry(Exercise.Squat, sets = listOf(SetGroup(80.0, 5, 5)))),
        ) { 100.0 }
        assertEquals(0.8, load.relativeIntensity!!, 1e-9)
        // 80 % → коэффициент 1,53; тоннаж 2000 кг; базовое движение с коэффициентом 1.
        assertEquals(3060.0, load.weightedIntensity, 1e-6)
        // 5 подходов × 1,1 × 6 минут + 30 минут.
        assertEquals(63, load.minutes)
    }

    @Test
    fun assistedPullUpCountsBodyweightMinusCounterweight() {
        val sets = listOf(SetGroup(40.0, 20))
        val assisted = session(ExerciseEntry(Exercise.AssistedPullUp, sets = sets))
        val withBody = LoadCalculator.session(assisted, 80.0) { null }
        assertEquals(800.0, withBody.tonnageKg, 0.0)
        assertEquals(40.0, withBody.averageKg, 0.0)
        val pullUps = LoadCalculator.session(
            session(ExerciseEntry(Exercise.PullUp, sets = listOf(SetGroup(null, 10), SetGroup(10.0, 5)))),
            80.0,
        ) { 100.0 }
        assertEquals(1250.0, pullUps.tonnageKg, 0.0)
        assertEquals(0.8333, pullUps.relativeIntensity!!, 1e-3)
        assertEquals(20.0, LoadCalculator.session(assisted) { null }.tonnageKg, 0.0)
    }
}
