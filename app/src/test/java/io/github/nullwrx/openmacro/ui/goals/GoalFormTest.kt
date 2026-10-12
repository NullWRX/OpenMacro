package io.github.nullwrx.openmacro.ui.goals

import io.github.nullwrx.openmacro.core.CalorieMode
import io.github.nullwrx.openmacro.core.GoalPreset
import io.github.nullwrx.openmacro.core.GoalSettings
import io.github.nullwrx.openmacro.core.MacroMode
import io.github.nullwrx.openmacro.data.settings.WeightUnit
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GoalFormTest {

    @Test
    fun presetFillsSuggestedRateForTheBody() {
        val form = GoalForm(unit = WeightUnit.KG).withPreset(GoalPreset.LOSE_FAT, weightKg = 80.0)
        assertEquals("0.4", form.rate)
        assertEquals(-0.4, form.weeklyChangeKg()!!, 1e-9)
    }

    @Test
    fun ratesConvertBetweenUnits() {
        val kg = GoalForm(unit = WeightUnit.KG, preset = GoalPreset.BULK, rate = "0.5")
        val lb = kg.convertedTo(WeightUnit.LB)
        assertEquals("1.1", lb.rate)
        assertEquals(0.5, lb.weeklyChangeKg()!!, 0.01)
    }

    @Test
    fun gramsPerPoundBecomeGramsPerKg() {
        val form = GoalForm(
            unit = WeightUnit.LB, preset = GoalPreset.CUSTOM, macroMode = MacroMode.PER_KG,
            direction = Direction.MAINTAIN, proteinPerWeight = "1", fatPerWeight = "0.4",
        )
        val goal = assertNotNull(form.toGoal())
        assertEquals(2.2046, goal.proteinPerKg, 0.001)
        assertEquals(0.8818, goal.fatPerKg, 0.001)
    }

    @Test
    fun roundTripKeepsCustomGoal() {
        val goal = GoalSettings(
            preset = GoalPreset.CUSTOM, weeklyChangeKg = -0.25, calorieMode = CalorieMode.FROM_RATE,
            macroMode = MacroMode.PERCENT, proteinPct = 35, carbsPct = 35, fatPct = 30, fiberG = 35, sodiumMg = 2000,
        )
        val back = assertNotNull(GoalForm.from(goal, WeightUnit.KG).toGoal())
        assertEquals(goal.copy(weeklyChangeKg = back.weeklyChangeKg), back)
        assertEquals(-0.25, back.weeklyChangeKg, 1e-9)
    }

    @Test
    fun missingRequiredFieldsGiveNoGoal() {
        assertNull(GoalForm(preset = GoalPreset.LOSE_FAT, rate = "").toGoal())
        assertNull(
            GoalForm(preset = GoalPreset.CUSTOM, macroMode = MacroMode.GRAMS, proteinG = "", carbsG = "200", fatG = "70").toGoal(),
        )
        // Maintain needs no rate.
        assertNotNull(GoalForm(preset = GoalPreset.MAINTAIN, rate = "").toGoal())
    }
}
