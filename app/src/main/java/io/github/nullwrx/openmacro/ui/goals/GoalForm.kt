package io.github.nullwrx.openmacro.ui.goals

import io.github.nullwrx.openmacro.core.CalorieMode
import io.github.nullwrx.openmacro.core.GoalPreset
import io.github.nullwrx.openmacro.core.GoalSettings
import io.github.nullwrx.openmacro.core.MacroMode
import io.github.nullwrx.openmacro.core.Units
import io.github.nullwrx.openmacro.data.settings.WeightUnit
import io.github.nullwrx.openmacro.ui.Format
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Direction(val label: String, val sign: Int) { LOSE("Lose", -1), MAINTAIN("Maintain", 0), GAIN("Gain", 1) }

/**
 * A goal as typed. Weights are in the person's unit: the rate in kg or lb per week, and the
 * per-body-weight macros in g per kg or g per lb.
 */
data class GoalForm(
    val unit: WeightUnit = WeightUnit.KG,
    val preset: GoalPreset = GoalPreset.MAINTAIN,
    /** Weekly change, as a positive number; the direction comes from the preset (or [direction]). */
    val rate: String = "",
    val direction: Direction = Direction.MAINTAIN,
    val calorieMode: CalorieMode = CalorieMode.FROM_RATE,
    val fixedKcal: String = "2000",
    val macroMode: MacroMode = MacroMode.PERCENT,
    val proteinPct: String = "30",
    val carbsPct: String = "40",
    val fatPct: String = "30",
    val proteinPerWeight: String = "2",
    val fatPerWeight: String = "0.8",
    val proteinG: String = "150",
    val carbsG: String = "200",
    val fatG: String = "70",
    /** Blank means automatic (14 g per 1,000 kcal). */
    val fiber: String = "",
    val sodium: String = "2300",
) {
    private val perWeightToPerKg: Double get() = if (unit == WeightUnit.KG) 1.0 else 1.0 / Units.KG_PER_LB

    /** The weekly change the form describes, in kg (signed), or null if the rate is unreadable. */
    fun weeklyChangeKg(): Double? {
        val sign = when (preset) {
            GoalPreset.CUSTOM -> direction.sign
            else -> preset.direction
        }
        if (sign == 0) return 0.0
        val magnitude = Format.parse(rate)?.takeIf { it >= 0 } ?: return null
        return sign * Format.toKg(magnitude, unit)
    }

    /** The goal, or null while a field the chosen options need is missing or invalid. */
    fun toGoal(): GoalSettings? {
        val custom = preset == GoalPreset.CUSTOM
        val needsRate = !custom || (macroMode != MacroMode.GRAMS && calorieMode == CalorieMode.FROM_RATE)
        val change = if (needsRate) weeklyChangeKg() ?: return null else (weeklyChangeKg() ?: 0.0)
        fun int(s: String) = Format.parse(s)?.roundToInt()
        fun dbl(s: String) = Format.parse(s)
        var g = GoalSettings(
            preset = preset,
            weeklyChangeKg = change,
            calorieMode = calorieMode,
            macroMode = macroMode,
            fiberG = if (fiber.isBlank()) null else int(fiber)?.takeIf { it in 0..150 } ?: return null,
            sodiumMg = int(sodium)?.takeIf { it in 0..10000 } ?: return null,
        )
        if (!custom) return g
        if (macroMode != MacroMode.GRAMS && calorieMode == CalorieMode.FIXED) {
            g = g.copy(fixedKcal = int(fixedKcal)?.takeIf { it in 800..10000 } ?: return null)
        }
        g = when (macroMode) {
            MacroMode.PERCENT -> g.copy(
                proteinPct = int(proteinPct)?.takeIf { it in 0..100 } ?: return null,
                carbsPct = int(carbsPct)?.takeIf { it in 0..100 } ?: return null,
                fatPct = int(fatPct)?.takeIf { it in 0..100 } ?: return null,
            )
            MacroMode.PER_KG -> g.copy(
                proteinPerKg = dbl(proteinPerWeight)?.takeIf { it in 0.0..10.0 }?.times(perWeightToPerKg) ?: return null,
                fatPerKg = dbl(fatPerWeight)?.takeIf { it in 0.0..10.0 }?.times(perWeightToPerKg) ?: return null,
            )
            MacroMode.GRAMS -> g.copy(
                proteinG = int(proteinG)?.takeIf { it in 0..1000 } ?: return null,
                carbsG = int(carbsG)?.takeIf { it in 0..2000 } ?: return null,
                fatG = int(fatG)?.takeIf { it in 0..1000 } ?: return null,
            )
        }
        return g
    }

    /** Switches to [p], filling in its default rate for a body of [weightKg] when known. */
    fun withPreset(p: GoalPreset, weightKg: Double?): GoalForm {
        val defaultRate = weightKg?.let { abs(p.defaultWeeklyChangeKg(it)) }
            ?.takeIf { it > 0 }
            ?.let { Format.amount(roundRate(Format.weightValue(it, unit))) }
        return copy(preset = p, rate = if (p == GoalPreset.CUSTOM) rate.ifBlank { defaultRate ?: "0.5" } else defaultRate ?: rate)
    }

    /** Re-expresses unit-dependent fields in [target] units. */
    fun convertedTo(target: WeightUnit): GoalForm {
        if (target == unit) return this
        val factor = if (target == WeightUnit.LB) 1.0 / Units.KG_PER_LB else Units.KG_PER_LB
        fun conv(s: String, perWeight: Boolean): String {
            val v = Format.parse(s) ?: return s
            // A rate in kg becomes more lb; a per-kg amount becomes less per lb.
            val out = if (perWeight) v / factor else v * factor
            return Format.amount(if (perWeight) roundTo(out, 0.05) else roundRate(out))
        }
        return copy(
            unit = target,
            rate = conv(rate, perWeight = false),
            proteinPerWeight = conv(proteinPerWeight, perWeight = true),
            fatPerWeight = conv(fatPerWeight, perWeight = true),
        )
    }

    companion object {
        fun from(g: GoalSettings, unit: WeightUnit): GoalForm {
            val perKgToUnit = if (unit == WeightUnit.KG) 1.0 else Units.KG_PER_LB
            val rateValue = Format.weightValue(abs(g.weeklyChangeKg), unit)
            return GoalForm(
                unit = unit,
                preset = g.preset,
                rate = if (g.weeklyChangeKg == 0.0) "" else Format.amount(roundRate(rateValue)),
                direction = when {
                    g.weeklyChangeKg < 0 -> Direction.LOSE
                    g.weeklyChangeKg > 0 -> Direction.GAIN
                    else -> Direction.MAINTAIN
                },
                calorieMode = g.calorieMode,
                fixedKcal = g.fixedKcal.toString(),
                macroMode = g.macroMode,
                proteinPct = g.proteinPct.toString(),
                carbsPct = g.carbsPct.toString(),
                fatPct = g.fatPct.toString(),
                proteinPerWeight = Format.amount(roundTo(g.proteinPerKg * perKgToUnit, 0.05)),
                fatPerWeight = Format.amount(roundTo(g.fatPerKg * perKgToUnit, 0.05)),
                proteinG = g.proteinG.toString(),
                carbsG = g.carbsG.toString(),
                fatG = g.fatG.toString(),
                fiber = g.fiberG?.toString().orEmpty(),
                sodium = g.sodiumMg.toString(),
            )
        }

        /** Rates and per-weight amounts are shown in steps of 0.05. */
        private fun roundRate(v: Double): Double = roundTo(v, 0.05)

        private fun roundTo(v: Double, step: Double): Double = (v / step).roundToInt() * step
    }
}
