package com.kurmish.fitness.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DietTest {
    private val start = 100L
    private val deficit = DietSettings(phase = DietPhase.Deficit, phaseStartEpochDay = start, bodyFatPercent = 20.0)

    private fun data(
        diet: DietSettings = deficit,
        weights: List<WeightEntry> = emptyList(),
        sessions: List<WorkoutSession> = emptyList(),
    ) = AppData(Profile(bodyweightKg = 80.0, daysPerWeek = 3, diet = diet), sessions, weights)

    @Test
    fun offPhaseGivesNothing() {
        val off = data(DietSettings())
        assertNull(FlexibleDiet.day(off, start))
        assertNull(FlexibleDiet.missing(off))
    }

    @Test
    fun categoriesBySexAndFat() {
        assertEquals(1, FlexibleDiet.category(Sex.Male, 15.0))
        assertEquals(2, FlexibleDiet.category(Sex.Male, 20.0))
        assertEquals(3, FlexibleDiet.category(Sex.Male, 26.0))
        assertEquals(1, FlexibleDiet.category(Sex.Female, 24.0))
        assertEquals(2, FlexibleDiet.category(Sex.Female, 30.0))
        assertEquals(3, FlexibleDiet.category(Sex.Female, 35.0))
    }

    @Test
    fun maintenanceAndDeficitMacros() {
        // 80 кг × 2,2046 × 13 ккал на фунт ≈ 2292 ккал; сухая масса 64 кг ≈ 141 фунт.
        val maintenance = FlexibleDiet.day(data(deficit.copy(phase = DietPhase.Maintenance)), start)!!
        assertEquals(2292, maintenance.maintenanceKcal)
        assertEquals(141, maintenance.target.proteinG)
        assertEquals(241, maintenance.target.carbsG)
        assertEquals(2292, maintenance.target.kcal)
        val cut = FlexibleDiet.day(data(), start)!!
        assertEquals((2292 * 0.85).toInt(), cut.target.kcal)
        assertEquals(maintenance.target.proteinG, cut.target.proteinG)
        assertEquals(maintenance.target.fatG, cut.target.fatG)
        assertTrue(cut.target.carbsG < maintenance.target.carbsG)
    }

    @Test
    fun refeedScheduleForCategoryTwoTrainingHard() {
        val first = FlexibleDiet.day(data(), start + 6)!!.refeed!!
        assertEquals(RefeedLength.FiveHours, first.length)
        assertEquals(start + 7, first.nextEpochDay)
        assertFalse(first.due)
        // Сухая масса 64 кг × 3–6 г.
        assertEquals(192..384, first.carbsG)
        val due = FlexibleDiet.day(data(), start + 7)!!
        assertTrue(due.refeed!!.due)
        assertNotNull(DietAdvice.planNote(due))
        val done = data().withRefeed(start + 7)
        val after = FlexibleDiet.day(done, start + 8)!!.refeed!!
        assertEquals(start + 14, after.nextEpochDay)
        assertFalse(after.due)
    }

    @Test
    fun categoryThreeWaitsSixWeeksForRefeed() {
        val heavy = data(deficit.copy(bodyFatPercent = 30.0))
        assertEquals(start + 42, FlexibleDiet.day(heavy, start)!!.refeed!!.nextEpochDay)
    }

    @Test
    fun slowLossSuggestsCuttingTenPercent() {
        val today = start + 20
        val weights = listOf(WeightEntry(today - 10, 80.0), WeightEntry(today - 2, 79.9))
        val day = FlexibleDiet.day(data(weights = weights), today)!!
        assertEquals(-10, day.suggestion!!.percent)
        val corrected = data(weights = weights).withCalorieCorrection(-10, today)
        val after = FlexibleDiet.day(corrected, today)!!
        assertNull(after.suggestion)
        // Дефицит 15 % и поправка −10 %: 0,85 × 0,9 = 76,5 % от поддержки при последнем весе.
        assertEquals((after.maintenanceKcal * 76.5 / 100).toInt(), after.target.kcal)
    }

    @Test
    fun weightsRightAfterRefeedAreIgnored() {
        val today = start + 20
        val weights = listOf(WeightEntry(today - 10, 80.0), WeightEntry(today - 1, 81.5))
        val diet = deficit.copy(refeedDays = listOf(today - 2))
        assertNull(DietProgress.weeklyLoss(data(diet, weights), today))
    }

    @Test
    fun strengthDropIsDetected() {
        fun squat(day: Long, kg: Double) =
            WorkoutSession(day, day, "", listOf(ExerciseEntry(Exercise.Squat, sets = listOf(SetGroup(kg, 3, 3)))))
        val sessions = listOf(squat(start - 20, 140.0), squat(start - 3, 120.0))
        assertTrue(DietProgress.strengthDropping(sessions, start))
        assertFalse(DietProgress.strengthDropping(listOf(squat(start - 3, 140.0)), start))
    }

    @Test
    fun deficitKeepsWeightsWhenEasy() {
        val easy = WorkoutSession(1, start, "", emptyList(), Feeling.Easy)
        val advice = Coach.advise(data(sessions = listOf(easy)), start + 2)
        assertTrue(advice.points.any { "сброс жира" in it && "не поднимай" in it })
    }

    @Test
    fun estimatedBodyFatAndWeighIn() {
        val noFat = data(deficit.copy(bodyFatPercent = null))
        assertNotNull(FlexibleDiet.missing(noFat))
        val withHeight = data(deficit.copy(bodyFatPercent = null, heightCm = 180.0, age = 30))
        val day = FlexibleDiet.day(withHeight, start)!!
        assertTrue(day.bodyFatEstimated)
        assertTrue(day.bodyFatPercent in 15.0..25.0)
        val weighed = withHeight.withWeight(start, 79.0).withWeight(start, 78.5)
        assertEquals(listOf(WeightEntry(start, 78.5)), weighed.weights)
        assertEquals(78.5, weighed.profile.bodyweightKg)
    }

    @Test
    fun breakEndsAfterTwoWeeks() {
        val breakDiet = deficit.withPhase(DietPhase.Break, start)
        val day = FlexibleDiet.day(data(breakDiet), start + 14)!!
        assertNull(day.refeed)
        assertTrue(day.target.carbsG >= 100)
        assertTrue(day.points.first().startsWith("Две недели перерыва прошли"))
    }

    @Test
    fun activityComesFromProfile() {
        val base = Profile(daysPerWeek = 3)
        assertEquals(Activity.Moderate, base.activityLevel())
        assertEquals(Activity.High, base.copy(daysPerWeek = 4).activityLevel())
        assertEquals(Activity.Extreme, base.copy(daysPerWeek = 5, rank = Rank.Kms).activityLevel())
        val onFeet = base.copy(diet = DietSettings(lifestyle = Lifestyle.OnFeet))
        assertEquals(Activity.High, onFeet.activityLevel())
        assertEquals(Activity.Extreme, onFeet.copy(daysPerWeek = 5, rank = Rank.Ms).activityLevel())
    }
}
