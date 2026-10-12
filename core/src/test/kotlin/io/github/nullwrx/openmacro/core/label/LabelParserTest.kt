package io.github.nullwrx.openmacro.core.label

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelParserTest {

    private fun assertNear(expected: Double, actual: Double?, tolerance: Double = 0.01) {
        assertNotNull(actual, "expected $expected but value was missing")
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }

    @Test
    fun usNutritionFactsPanel() {
        val text = """
            Nutrition Facts
            8 servings per container
            Serving size 2/3 cup (55g)
            Amount per serving
            Calories 230
            % Daily Value*
            Total Fat 8g 10%
            Saturated Fat 1g 5%
            Trans Fat 0g
            Cholesterol 0mg 0%
            Sodium 160mg 7%
            Total Carbohydrate 37g 13%
            Dietary Fiber 4g 14%
            Total Sugars 12g
            Includes 10g Added Sugars 20%
            Protein 3g
            Vitamin D 2mcg 10%
            * The % Daily Value (DV) tells you how much a nutrient in a serving of food
            contributes to a daily diet. 2,000 calories a day is used for general nutrition advice.
        """.trimIndent()

        val p = LabelParser.parse(text)

        assertNear(230.0, p[LabelField.ENERGY_KCAL])
        assertNear(8.0, p[LabelField.FAT])
        assertNear(1.0, p[LabelField.SATURATED_FAT])
        assertNear(0.0, p[LabelField.TRANS_FAT])
        assertNear(0.0, p[LabelField.CHOLESTEROL_MG])
        assertNear(160.0, p[LabelField.SODIUM_MG])
        assertNear(37.0, p[LabelField.CARBS])
        assertNear(4.0, p[LabelField.FIBER])
        assertNear(12.0, p[LabelField.SUGARS])
        assertNear(10.0, p[LabelField.ADDED_SUGARS])
        assertNear(3.0, p[LabelField.PROTEIN])
        assertNear(55.0, p[LabelField.SERVING_AMOUNT])
        assertNear(8.0, p[LabelField.SERVINGS_PER_CONTAINER])
        assertEquals("g", p.servingUnit)
        assertEquals("2/3 cup (55g)", p.servingText)
        assertFalse(p.per100)
    }

    @Test
    fun euPer100gTableUsesFirstColumnAndConvertsSalt() {
        val text = """
            Nutrition information
            Typical values per 100g per serving (30g)
            Energy 1569kJ/375kcal 471kJ/113kcal
            Fat 8,0g 2,4g
            of which saturates 1,0g 0,3g
            Carbohydrate 60g 18g
            of which sugars 22g 6,6g
            Fibre 7,0g 2,1g
            Protein 9,0g 2,7g
            Salt 0,50g 0,15g
        """.trimIndent()

        val p = LabelParser.parse(text)

        assertTrue(p.per100)
        assertNear(375.0, p[LabelField.ENERGY_KCAL])
        assertNear(8.0, p[LabelField.FAT])
        assertNear(1.0, p[LabelField.SATURATED_FAT])
        assertNear(60.0, p[LabelField.CARBS])
        assertNear(22.0, p[LabelField.SUGARS])
        assertNear(7.0, p[LabelField.FIBER])
        assertNear(9.0, p[LabelField.PROTEIN])
        assertNear(200.0, p[LabelField.SODIUM_MG])
        assertNear(30.0, p[LabelField.SERVING_AMOUNT])
    }

    @Test
    fun kilojoulesOnlyConvertToKcal() {
        val p = LabelParser.parse("Energy 1046 kJ\nFat 3 g")
        assertNear(250.0, p[LabelField.ENERGY_KCAL], tolerance = 0.1)
        assertNear(3.0, p[LabelField.FAT])
    }

    @Test
    fun valueOnNextLineIsUsedWhenNameStandsAlone() {
        val p = LabelParser.parse("Calories\n190\nProtein\n7g")
        assertNear(190.0, p[LabelField.ENERGY_KCAL])
        assertNear(7.0, p[LabelField.PROTEIN])
    }

    @Test
    fun ocrSlipsAreRepaired() {
        val p = LabelParser.parse("Total Fat 1og 13%\nTrans Fat Og\nSodium 2,5g")
        assertNear(10.0, p[LabelField.FAT])
        assertNear(0.0, p[LabelField.TRANS_FAT])
        assertNear(2500.0, p[LabelField.SODIUM_MG])
    }

    @Test
    fun oldLabelCaloriesFromFatIsIgnored() {
        val p = LabelParser.parse("Calories 250 Calories from Fat 110\nTotal Fat 12g 18%")
        assertNear(250.0, p[LabelField.ENERGY_KCAL])
        assertNear(12.0, p[LabelField.FAT])
    }

    @Test
    fun lessThanValuesAreHalved() {
        val p = LabelParser.parse("Dietary Fiber <1g 2%")
        assertNear(0.5, p[LabelField.FIBER])
    }

    @Test
    fun percentagesAreNeverTakenAsValues() {
        val p = LabelParser.parse("Saturated Fat 10%")
        assertNull(p[LabelField.SATURATED_FAT])
    }

    @Test
    fun accumulatorVotesAcrossFrames() {
        val acc = LabelAccumulator()
        acc.add(LabelParser.parse("Calories 230\nProtein 3g"))
        acc.add(LabelParser.parse("Calories 280\nProtein 3g")) // a misread frame
        acc.add(LabelParser.parse("Calories 230\nTotal Fat 8g"))
        val best = acc.best()
        assertNear(230.0, best[LabelField.ENERGY_KCAL])
        assertNear(3.0, best[LabelField.PROTEIN])
        assertNear(8.0, best[LabelField.FAT])
        assertEquals(3, acc.readings)
    }

    @Test
    fun toNutrientsMapsFields() {
        val n = LabelParser.parse("Calories 100\nTotal Fat 2g\nSodium 50mg\nProtein 5g").toNutrients()
        assertNear(100.0, n.energyKcal)
        assertNear(2.0, n.fatG)
        assertNear(5.0, n.proteinG)
        assertNear(50.0, n.sodiumMg)
        assertNull(n.fiberG)
    }
}
