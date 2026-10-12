package io.github.nullwrx.openmacro.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoalPlannerTest {
    // 30-year-old man, 180 cm, 80 kg, moderately active: BMR 1780, TDEE 1780 * 1.55 = 2759.
    private val man = Profile(Sex.MALE, 30, 180.0, 80.0, ActivityLevel.MODERATE, weeklyChangeKg = 0.0)

    private fun preset(p: GoalPreset, weightKg: Double = 80.0) =
        GoalSettings(preset = p, weeklyChangeKg = p.defaultWeeklyChangeKg(weightKg))

    @Test
    fun loseFatUsesDeficitHighProteinAndFatFloor() {
        val plan = GoalPlanner.plan(man, preset(GoalPreset.LOSE_FAT))
        // 0.5% of 80 kg = 0.4 kg/week = 440 kcal/day below 2759 -> 2319 -> rounded 2320.
        assertEquals(2759, plan.tdeeKcal)
        assertEquals(-0.4, plan.weeklyChangeKg, 1e-9)
        assertEquals(2320, plan.targets.energyKcal)
        assertEquals(160, plan.targets.proteinG) // 2.0 g/kg
        assertEquals(64, plan.targets.fatG) // 25% of 2320 / 9 = 64.4
        val total = plan.targets.proteinG * 4 + plan.targets.carbsG * 4 + plan.targets.fatG * 9
        assertTrue(kotlin.math.abs(total - 2320) < 10)
        assertTrue(plan.warnings.isEmpty())
    }

    @Test
    fun presetsForceTheirDirection() {
        val bulk = GoalPlanner.plan(man, GoalSettings(GoalPreset.BULK, weeklyChangeKg = -0.4))
        assertEquals(0.4, bulk.weeklyChangeKg, 1e-9)
        assertTrue(bulk.targets.energyKcal > bulk.tdeeKcal)

        val cut = GoalPlanner.plan(man, GoalSettings(GoalPreset.LOSE_FAT, weeklyChangeKg = 0.3))
        assertEquals(-0.3, cut.weeklyChangeKg, 1e-9)

        val maintain = GoalPlanner.plan(man, GoalSettings(GoalPreset.MAINTAIN, weeklyChangeKg = 0.5))
        assertEquals(0.0, maintain.weeklyChangeKg, 1e-9)
        assertEquals(2760, maintain.targets.energyKcal)
    }

    @Test
    fun leanBulkAndBulkDifferInSurplus() {
        val lean = GoalPlanner.plan(man, preset(GoalPreset.LEAN_BULK)).targets
        val bulk = GoalPlanner.plan(man, preset(GoalPreset.BULK)).targets
        assertEquals(2980, lean.energyKcal) // +0.2 kg/week = +220 kcal
        assertEquals(3200, bulk.energyKcal) // +0.4 kg/week = +440 kcal
        assertEquals(144, lean.proteinG) // 1.8 g/kg
        assertEquals(128, bulk.proteinG) // 1.6 g/kg
    }

    @Test
    fun rateIsCappedAtOnePercentPerWeek() {
        val plan = GoalPlanner.plan(man, GoalSettings(GoalPreset.LOSE_FAT, weeklyChangeKg = -2.0))
        assertEquals(-0.8, plan.weeklyChangeKg, 1e-9)
        assertTrue(plan.warnings.any { "capped" in it })
    }

    @Test
    fun calorieFloorIsReportedForPresets() {
        val small = Profile(Sex.FEMALE, 40, 155.0, 50.0, ActivityLevel.SEDENTARY, 0.0)
        val plan = GoalPlanner.plan(small, GoalSettings(GoalPreset.LOSE_FAT, weeklyChangeKg = -0.5))
        assertEquals(1200, plan.targets.energyKcal)
        assertTrue(plan.warnings.any { "minimum" in it })
        // Fat never drops below 0.5 g/kg on presets: max(1200 * 0.25 / 9, 25) = 33.
        assertEquals(33, plan.targets.fatG)
    }

    @Test
    fun customPercentWithFixedCalories() {
        val goal = GoalSettings(
            preset = GoalPreset.CUSTOM, calorieMode = CalorieMode.FIXED, fixedKcal = 2500,
            macroMode = MacroMode.PERCENT, proteinPct = 30, carbsPct = 45, fatPct = 25,
        )
        val t = GoalPlanner.plan(man, goal)
        assertEquals(2500, t.targets.energyKcal)
        assertEquals(188, t.targets.proteinG)
        assertEquals(281, t.targets.carbsG)
        assertEquals(69, t.targets.fatG)
        assertTrue(t.warnings.isEmpty())
    }

    @Test
    fun customPercentsThatDontSumTo100AreFlagged() {
        val goal = GoalSettings(GoalPreset.CUSTOM, macroMode = MacroMode.PERCENT, proteinPct = 40, carbsPct = 40, fatPct = 30)
        assertTrue(GoalPlanner.plan(man, goal).warnings.any { "110%" in it })
    }

    @Test
    fun customPerKgFillsCarbsAndWarnsWhenOver() {
        val ok = GoalPlanner.plan(
            man,
            GoalSettings(GoalPreset.CUSTOM, weeklyChangeKg = 0.0, macroMode = MacroMode.PER_KG, proteinPerKg = 2.2, fatPerKg = 1.0),
        ).targets
        assertEquals(176, ok.proteinG)
        assertEquals(80, ok.fatG)
        assertEquals((2760 - 176 * 4 - 80 * 9) / 4, ok.carbsG)

        val over = GoalPlanner.plan(
            man,
            GoalSettings(
                GoalPreset.CUSTOM, calorieMode = CalorieMode.FIXED, fixedKcal = 1500,
                macroMode = MacroMode.PER_KG, proteinPerKg = 3.0, fatPerKg = 1.5,
            ),
        )
        assertEquals(0, over.targets.carbsG)
        assertTrue(over.warnings.any { "carbs are set to 0" in it })
    }

    @Test
    fun customGramsDeriveCalories() {
        val t = GoalPlanner.plan(
            man,
            GoalSettings(GoalPreset.CUSTOM, macroMode = MacroMode.GRAMS, proteinG = 180, carbsG = 250, fatG = 70),
        ).targets
        assertEquals(2350, t.energyKcal) // 720 + 1000 + 630
        assertEquals(180, t.proteinG)
    }

    @Test
    fun fiberAndSodiumFollowSettings() {
        val auto = GoalPlanner.plan(man, preset(GoalPreset.MAINTAIN)).targets
        assertEquals(39, auto.fiberG) // 14 g per 1,000 of 2760
        val fixed = GoalPlanner.plan(man, preset(GoalPreset.MAINTAIN).copy(fiberG = 30, sodiumMg = 1500)).targets
        assertEquals(30, fixed.fiberG)
        assertEquals(1500, fixed.sodiumMg)
    }
}
