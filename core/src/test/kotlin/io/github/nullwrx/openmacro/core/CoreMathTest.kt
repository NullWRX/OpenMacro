package io.github.nullwrx.openmacro.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreMathTest {

    private fun near(expected: Double, actual: Double?, tolerance: Double = 0.01) {
        assertNotNull(actual)
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }

    @Test
    fun nutrientsAddAndScaleKeepUnknownsUnknown() {
        val a = Nutrients(100.0, 5.0, 10.0, 2.0, fiberG = 1.0)
        val b = Nutrients(50.0, 1.0, 2.0, 3.0)
        val sum = a + b
        near(150.0, sum.energyKcal)
        near(1.0, sum.fiberG)
        assertNull(sum.sodiumMg)
        val half = a.scaled(0.5)
        near(50.0, half.energyKcal)
        near(0.5, half.fiberG)
    }

    @Test
    fun portionsConvertBetweenServingAndBasis() {
        val perServing = Nutrients(energyKcal = 220.0, proteinG = 11.0)
        val per100 = Portions.perBasisFromServing(perServing, 55.0, BaseUnit.GRAM)
        near(400.0, per100.energyKcal)
        val back = Portions.nutrientsFor(per100, BaseUnit.GRAM, 55.0)
        near(220.0, back.energyKcal)
        near(11.0, back.proteinG)
    }

    @Test
    fun servingChoicesPutOwnServingsFirstAndDropDuplicates() {
        val choices = Portions.servingChoices(BaseUnit.GRAM, listOf(Serving("1 cup", 128.0), Serving("100 g", 100.0)))
        assertEquals("1 cup", choices.first().label)
        assertEquals(1, choices.count { it.label == "100 g" })
        assertTrue(choices.any { it.label == "oz" })
        assertEquals(listOf("serving"), Portions.servingChoices(BaseUnit.SERVING, emptyList()).map { it.label })
    }

    @Test
    fun mifflinStJeorMatchesPublishedFormula() {
        // 10 * 80 + 6.25 * 180 - 5 * 30 + 5 = 1780
        near(1780.0, EnergyMath.bmrMifflinStJeor(80.0, 180.0, 30, Sex.MALE))
        // 10 * 60 + 6.25 * 165 - 5 * 40 - 161 = 1270.25
        near(1270.25, EnergyMath.bmrMifflinStJeor(60.0, 165.0, 40, Sex.FEMALE))
    }

    @Test
    fun calorieTargetAppliesDeficitAndFloor() {
        // 0.5 kg per week loss is 550 kcal per day.
        assertEquals(1950, EnergyMath.dailyCalorieTarget(2500.0, -0.5, Sex.MALE))
        assertEquals(1200, EnergyMath.dailyCalorieTarget(1500.0, -1.0, Sex.FEMALE))
        assertEquals(2500, EnergyMath.dailyCalorieTarget(2500.0, 0.0, Sex.FEMALE))
    }

    @Test
    fun macroPlannerFillsCarbsWithRemainder() {
        val t = MacroPlanner.fromCalories(2000, 70.0)
        assertEquals(112, t.proteinG) // 1.6 g/kg
        assertEquals(67, t.fatG) // 30% of 2000 / 9
        val kcalFromMacros = t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9
        assertTrue(kotlin.math.abs(kcalFromMacros - 2000) < 10)
        assertEquals(28, t.fiberG)
    }

    @Test
    fun proteinIsCappedForHeavyBodies() {
        val t = MacroPlanner.fromCalories(1600, 160.0)
        assertEquals(140, t.proteinG) // 35% of 1600 kcal / 4
    }

    @Test
    fun energyConsistencyCheck() {
        assertTrue(EnergyMath.isEnergyConsistent(Nutrients(230.0, 3.0, 37.0, 8.0)))
        assertFalse(EnergyMath.isEnergyConsistent(Nutrients(500.0, 3.0, 37.0, 8.0)))
    }

    @Test
    fun weightTrendSmoothsAndHandlesGaps() {
        val trend = WeightTrend.compute(
            listOf(WeightPoint(0, 80.0), WeightPoint(1, 82.0), WeightPoint(11, 80.0)),
        )
        near(80.0, trend[0].trendKg)
        near(80.2, trend[1].trendKg) // 80 + 0.1 * 2
        // A 10-day gap moves the trend most of the way: alpha = 1 - 0.9^10 = 0.6513.
        near(80.2 - 0.6513 * 0.2, trend[2].trendKg, tolerance = 0.001)
    }

    @Test
    fun weeklyRateFromSteadyLoss() {
        val points = (0L until 28L).map { WeightPoint(it, 90.0 - it * 0.1) }
        val rate = WeightTrend.weeklyRateKg(WeightTrend.compute(points, alpha = 1.0))
        near(-0.7, rate, tolerance = 0.001)
        assertNull(WeightTrend.weeklyRateKg(WeightTrend.compute(listOf(WeightPoint(0, 80.0)))))
    }

    @Test
    fun mealDefaultsByHour() {
        assertEquals(0, MealDefaults.mealForHour(7, 4))
        assertEquals(1, MealDefaults.mealForHour(12, 4))
        assertEquals(3, MealDefaults.mealForHour(16, 4))
        assertEquals(2, MealDefaults.mealForHour(19, 4))
        assertEquals(3, MealDefaults.mealForHour(23, 4))
        assertEquals(0, MealDefaults.mealForHour(5, 3))
        assertEquals(2, MealDefaults.mealForHour(22, 3))
    }

    @Test
    fun ftsQueryBuildsPrefixTerms() {
        assertEquals("chicken* bre*", FtsQuery.build("Chicken  bre"))
        assertEquals("crème* brûlée*", FtsQuery.build("Crème-brûlée"))
        assertNull(FtsQuery.build("  \"*  "))
    }

    @Test
    fun barcodeVariantsAndCheckDigits() {
        assertEquals(listOf("012345678905", "0012345678905"), Barcodes.variants("012345678905"))
        assertEquals(listOf("3017624010701"), Barcodes.variants("3017624010701"))
        assertTrue(Barcodes.isValidGtin("3017624010701"))
        assertTrue(Barcodes.isValidGtin("012345678905"))
        assertFalse(Barcodes.isValidGtin("3017624010702"))
        assertFalse(Barcodes.isValidGtin("12345"))
    }

    @Test
    fun csvEscapesSpecialCharacters() {
        assertEquals("a,\"b,c\",\"say \"\"hi\"\"\",", Csv.row("a", "b,c", "say \"hi\"", null))
    }
}
