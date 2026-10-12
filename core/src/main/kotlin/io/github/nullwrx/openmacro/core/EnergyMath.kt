package io.github.nullwrx.openmacro.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

enum class ActivityLevel(val factor: Double, val label: String, val description: String) {
    SEDENTARY(1.2, "Sedentary", "Desk job, little exercise"),
    LIGHT(1.375, "Lightly active", "Light exercise 1 to 3 days a week"),
    MODERATE(1.55, "Moderately active", "Exercise 3 to 5 days a week"),
    ACTIVE(1.725, "Very active", "Hard exercise 6 to 7 days a week"),
    EXTRA(1.9, "Extra active", "Physical job plus hard training"),
}

data class Profile(
    val sex: Sex,
    val ageYears: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: ActivityLevel,
    /** Planned weekly change in kg: negative to lose, positive to gain, 0 to maintain. */
    val weeklyChangeKg: Double,
)

data class MacroTargets(
    val energyKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
    val sodiumMg: Int,
)

object EnergyMath {
    /** Approximate energy in 1 kg of body-weight change. */
    const val KCAL_PER_KG = 7700.0
    const val KJ_PER_KCAL = 4.184

    /** Lowest daily target the app will suggest without the user overriding it. */
    fun minimumCalories(sex: Sex): Int = if (sex == Sex.MALE) 1500 else 1200

    /** Mifflin-St Jeor resting energy expenditure, in kcal per day. */
    fun bmrMifflinStJeor(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return base + if (sex == Sex.MALE) 5.0 else -161.0
    }

    fun tdee(profile: Profile): Double =
        bmrMifflinStJeor(profile.weightKg, profile.heightCm, profile.ageYears, profile.sex) *
            profile.activity.factor

    /**
     * Daily calorie target for a planned weekly change, rounded to 10 kcal.
     * Deficits never go below [minimumCalories].
     */
    fun dailyCalorieTarget(tdee: Double, weeklyChangeKg: Double, sex: Sex): Int {
        val raw = tdee + weeklyChangeKg * KCAL_PER_KG / 7.0
        val bounded = if (weeklyChangeKg < 0) max(raw, minimumCalories(sex).toDouble()) else raw
        return roundTo(bounded, 10)
    }

    /** Calories implied by macros using the 4-4-9 Atwater factors. */
    fun atwaterKcal(proteinG: Double, carbsG: Double, fatG: Double): Double =
        4.0 * proteinG + 4.0 * carbsG + 9.0 * fatG

    /**
     * True when stated calories roughly match the macros. Labels round values and fibre and
     * alcohol skew the 4-4-9 estimate, so the tolerance is the larger of 25 kcal and 15%.
     */
    fun isEnergyConsistent(n: Nutrients): Boolean {
        val estimate = atwaterKcal(n.proteinG, n.carbsG, n.fatG)
        val diff = abs(estimate - n.energyKcal)
        return diff <= max(25.0, 0.15 * max(n.energyKcal, estimate))
    }

    fun kcalToKj(kcal: Double) = kcal * KJ_PER_KCAL
    fun kjToKcal(kj: Double) = kj / KJ_PER_KCAL

    internal fun roundTo(value: Double, step: Int): Int = ((value / step).roundToInt()) * step
}

object MacroPlanner {
    const val DEFAULT_PROTEIN_G_PER_KG = 1.6
    const val DEFAULT_FAT_FRACTION = 0.30

    /**
     * Splits a calorie target into macros: protein by body weight (capped at 35% of calories),
     * fat as a share of calories, carbs as the remainder. Fibre follows the 14 g per 1,000 kcal
     * guideline; sodium defaults to the 2,300 mg daily limit.
     */
    fun fromCalories(
        kcal: Int,
        weightKg: Double,
        proteinPerKg: Double = DEFAULT_PROTEIN_G_PER_KG,
        fatFraction: Double = DEFAULT_FAT_FRACTION,
    ): MacroTargets {
        val proteinG = min(weightKg * proteinPerKg, kcal * 0.35 / 4.0)
        val fatG = kcal * fatFraction / 9.0
        val carbsG = max(0.0, (kcal - proteinG * 4.0 - fatG * 9.0) / 4.0)
        return MacroTargets(
            energyKcal = kcal,
            proteinG = proteinG.roundToInt(),
            carbsG = carbsG.roundToInt(),
            fatG = fatG.roundToInt(),
            fiberG = (kcal / 1000.0 * 14.0).roundToInt(),
            sodiumMg = 2300,
        )
    }

    /** Macros from percentages of calories; the three percentages should add up to 100. */
    fun fromPercents(kcal: Int, proteinPct: Int, carbsPct: Int, fatPct: Int): MacroTargets =
        MacroTargets(
            energyKcal = kcal,
            proteinG = (kcal * proteinPct / 100.0 / 4.0).roundToInt(),
            carbsG = (kcal * carbsPct / 100.0 / 4.0).roundToInt(),
            fatG = (kcal * fatPct / 100.0 / 9.0).roundToInt(),
            fiberG = (kcal / 1000.0 * 14.0).roundToInt(),
            sodiumMg = 2300,
        )

    fun forProfile(profile: Profile): MacroTargets {
        val kcal = EnergyMath.dailyCalorieTarget(EnergyMath.tdee(profile), profile.weeklyChangeKg, profile.sex)
        return fromCalories(kcal, profile.weightKg)
    }
}

object Units {
    const val KG_PER_LB = 0.45359237
    const val CM_PER_IN = 2.54

    fun kgToLb(kg: Double) = kg / KG_PER_LB
    fun lbToKg(lb: Double) = lb * KG_PER_LB
    fun cmToIn(cm: Double) = cm / CM_PER_IN
    fun inToCm(inches: Double) = inches * CM_PER_IN
}
