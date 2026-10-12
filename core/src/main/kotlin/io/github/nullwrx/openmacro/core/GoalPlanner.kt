package io.github.nullwrx.openmacro.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Ready-made fitness goals. Rates are a share of body weight per week, which scales sensibly
 * from small to large bodies; protein is per kg of body weight; fat is a share of calories with
 * a floor of [GoalPlanner.MIN_FAT_G_PER_KG]. Carbs fill the remaining calories.
 *
 * The defaults follow common sports-nutrition practice (protein of 1.6 to 2.2 g/kg for people
 * who train, higher in a deficit; fat around a quarter to a third of calories; weight change
 * of roughly 0.25 to 1% of body weight a week). They are starting points, not prescriptions.
 */
enum class GoalPreset(
    val label: String,
    val tagline: String,
    val description: String,
    /** Default weekly change as % of body weight; negative loses, positive gains. */
    val weeklyChangePct: Double,
    val proteinPerKg: Double,
    val fatFraction: Double,
) {
    LOSE_FAT(
        "Lose fat", "Steady deficit, high protein",
        "Lose about 0.5% of your body weight a week, with high protein so the weight comes from fat, not muscle.",
        -0.5, 2.0, 0.25,
    ),
    RECOMP(
        "Recomp", "Lose fat and build muscle together",
        "A small deficit with the most protein. Works best for newer lifters or people returning to training.",
        -0.25, 2.2, 0.25,
    ),
    MAINTAIN(
        "Maintain", "Hold your weight",
        "Eat at maintenance with moderate protein. Good for a break between cutting and bulking.",
        0.0, 1.6, 0.30,
    ),
    LEAN_BULK(
        "Build muscle", "Lean bulk with a small surplus",
        "Gain about 0.25% of your body weight a week to build muscle while keeping fat gain low.",
        0.25, 1.8, 0.25,
    ),
    BULK(
        "Bulk", "Gain size and strength faster",
        "Gain about 0.5% of your body weight a week. Faster size and strength, with more fat gain.",
        0.5, 1.6, 0.25,
    ),
    CUSTOM(
        "Custom", "Set your own goal",
        "Choose your own calories and macro split.",
        0.0, 1.6, 0.30,
    ),
    ;

    /** -1 for goals that lose weight, +1 for goals that gain, 0 otherwise (custom is free). */
    val direction: Int get() = weeklyChangePct.sign.toInt()

    fun defaultWeeklyChangeKg(weightKg: Double): Double = weightKg * weeklyChangePct / 100.0
}

/** How a custom goal sets calories. Ignored when macros are given in grams. */
enum class CalorieMode { FROM_RATE, FIXED }

/** How a custom goal splits calories into macros. */
enum class MacroMode { PERCENT, PER_KG, GRAMS }

/** Everything a person chose about their goal; [GoalPlanner] turns it into targets. */
data class GoalSettings(
    val preset: GoalPreset = GoalPreset.MAINTAIN,
    /** Planned weekly change in kg (negative to lose); used by presets and FROM_RATE goals. */
    val weeklyChangeKg: Double = 0.0,
    val calorieMode: CalorieMode = CalorieMode.FROM_RATE,
    val fixedKcal: Int = 2000,
    val macroMode: MacroMode = MacroMode.PERCENT,
    val proteinPct: Int = 30,
    val carbsPct: Int = 40,
    val fatPct: Int = 30,
    val proteinPerKg: Double = 2.0,
    val fatPerKg: Double = 0.8,
    val proteinG: Int = 150,
    val carbsG: Int = 200,
    val fatG: Int = 70,
    /** Fiber target; null follows 14 g per 1,000 kcal. */
    val fiberG: Int? = null,
    val sodiumMg: Int = 2300,
)

data class GoalPlan(
    val targets: MacroTargets,
    /** Estimated maintenance calories. */
    val tdeeKcal: Int,
    /** The weekly change actually planned, after direction and safety limits. */
    val weeklyChangeKg: Double,
    /** Plain-language notes about limits that were applied or values that look off. */
    val warnings: List<String>,
)

object GoalPlanner {
    /** Fastest planned change, as % of body weight per week, in either direction. */
    const val MAX_CHANGE_PCT = 1.0

    /** Fat below this for long stretches can disrupt hormones; presets never go under it. */
    const val MIN_FAT_G_PER_KG = 0.5

    /** Protein from presets never takes more than this share of calories. */
    private const val MAX_PROTEIN_FRACTION = 0.35

    /**
     * Turns a goal into daily targets for [profile]. The profile's own weekly change is ignored;
     * the goal decides it.
     */
    fun plan(profile: Profile, goal: GoalSettings): GoalPlan {
        val w = profile.weightKg
        val tdee = EnergyMath.tdee(profile)
        val warnings = mutableListOf<String>()
        val preset = goal.preset

        // Weekly change: presets force their direction; everything is capped.
        var change = when (preset) {
            GoalPreset.MAINTAIN -> 0.0
            GoalPreset.CUSTOM -> goal.weeklyChangeKg
            else -> abs(goal.weeklyChangeKg) * preset.direction
        }
        val maxChange = w * MAX_CHANGE_PCT / 100.0
        if (abs(change) > maxChange + 1e-9) {
            change = maxChange * change.sign
            warnings += "Rate capped at 1% of body weight per week."
        }

        val usesGrams = preset == GoalPreset.CUSTOM && goal.macroMode == MacroMode.GRAMS
        val usesFixed = preset == GoalPreset.CUSTOM && goal.calorieMode == CalorieMode.FIXED && !usesGrams

        val kcal: Int = when {
            usesGrams -> EnergyMath.roundTo(
                EnergyMath.atwaterKcal(goal.proteinG.toDouble(), goal.carbsG.toDouble(), goal.fatG.toDouble()), 10,
            )
            usesFixed -> goal.fixedKcal
            else -> {
                val raw = tdee + change * EnergyMath.KCAL_PER_KG / 7.0
                val target = EnergyMath.dailyCalorieTarget(tdee, change, profile.sex)
                if (change < 0 && raw < EnergyMath.minimumCalories(profile.sex)) {
                    warnings += "Calories raised to the ${EnergyMath.minimumCalories(profile.sex)} kcal minimum; " +
                        "the deficit is smaller than the rate asks for."
                }
                target
            }
        }
        if ((usesGrams || usesFixed) && kcal < EnergyMath.minimumCalories(profile.sex)) {
            warnings += "That's below the usual minimum of ${EnergyMath.minimumCalories(profile.sex)} kcal. " +
                "Check with a professional before eating this little."
        }

        val proteinG: Double
        val carbsG: Double
        val fatG: Double
        when {
            preset != GoalPreset.CUSTOM -> {
                proteinG = min(w * preset.proteinPerKg, kcal * MAX_PROTEIN_FRACTION / 4.0)
                fatG = max(kcal * preset.fatFraction / 9.0, w * MIN_FAT_G_PER_KG)
                carbsG = max(0.0, (kcal - proteinG * 4.0 - fatG * 9.0) / 4.0)
            }
            goal.macroMode == MacroMode.PERCENT -> {
                val sum = goal.proteinPct + goal.carbsPct + goal.fatPct
                if (sum != 100) warnings += "Percentages add up to $sum%, not 100%."
                proteinG = kcal * goal.proteinPct / 100.0 / 4.0
                carbsG = kcal * goal.carbsPct / 100.0 / 4.0
                fatG = kcal * goal.fatPct / 100.0 / 9.0
            }
            goal.macroMode == MacroMode.PER_KG -> {
                proteinG = w * goal.proteinPerKg
                fatG = w * goal.fatPerKg
                val left = kcal - proteinG * 4.0 - fatG * 9.0
                if (left < 0) {
                    warnings += "Protein and fat alone come to ${(kcal - left).roundToInt()} kcal, " +
                        "over your $kcal kcal target, so carbs are set to 0."
                }
                carbsG = max(0.0, left / 4.0)
            }
            else -> {
                proteinG = goal.proteinG.toDouble()
                carbsG = goal.carbsG.toDouble()
                fatG = goal.fatG.toDouble()
            }
        }
        if (preset == GoalPreset.CUSTOM && fatG < w * MIN_FAT_G_PER_KG - 0.5) {
            warnings += "Fat is under 0.5 g per kg of body weight, which is low to keep up for long."
        }

        val targets = MacroTargets(
            energyKcal = kcal,
            proteinG = proteinG.roundToInt(),
            carbsG = carbsG.roundToInt(),
            fatG = fatG.roundToInt(),
            fiberG = goal.fiberG ?: (kcal / 1000.0 * 14.0).roundToInt(),
            sodiumMg = goal.sodiumMg,
        )
        return GoalPlan(targets, tdee.roundToInt(), change, warnings)
    }
}
