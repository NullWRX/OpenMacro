package io.github.nullwrx.openmacro.core

/**
 * Nutrient amounts for some quantity of food.
 *
 * Calories and the three macros are always present (0 when unknown). The extra nutrients are
 * nullable so "not on the label" stays distinguishable from "zero".
 */
data class Nutrients(
    val energyKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    val saturatedFatG: Double? = null,
    val sodiumMg: Double? = null,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        energyKcal = energyKcal + other.energyKcal,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
        fiberG = addNullable(fiberG, other.fiberG),
        sugarG = addNullable(sugarG, other.sugarG),
        saturatedFatG = addNullable(saturatedFatG, other.saturatedFatG),
        sodiumMg = addNullable(sodiumMg, other.sodiumMg),
    )

    fun scaled(factor: Double) = Nutrients(
        energyKcal = energyKcal * factor,
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
        fiberG = fiberG?.times(factor),
        sugarG = sugarG?.times(factor),
        saturatedFatG = saturatedFatG?.times(factor),
        sodiumMg = sodiumMg?.times(factor),
    )

    companion object {
        val ZERO = Nutrients()

        fun sum(items: Iterable<Nutrients>): Nutrients = items.fold(ZERO) { acc, n -> acc + n }
    }
}

private fun addNullable(a: Double?, b: Double?): Double? =
    if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)

/**
 * The unit a food's nutrients are stored against.
 *
 * Gram and millilitre foods store nutrients per 100 units, matching food labels and databases.
 * Foods with no known weight (for example "1 bar" with no grams on the label) store per 1 serving.
 */
enum class BaseUnit(val symbol: String, val basis: Double) {
    GRAM("g", 100.0),
    MILLILITER("ml", 100.0),
    SERVING("serving", 1.0),
}

/** A way to measure a food: [label] such as "1 cup", worth [baseAmount] base units. */
data class Serving(val label: String, val baseAmount: Double)

object Portions {
    const val GRAMS_PER_OUNCE = 28.349523125
    const val ML_PER_FLUID_OUNCE = 29.5735295625

    /** Nutrients for [baseAmount] base units of a food whose nutrients are given per basis. */
    fun nutrientsFor(perBasis: Nutrients, unit: BaseUnit, baseAmount: Double): Nutrients =
        perBasis.scaled(baseAmount / unit.basis)

    /** Converts per-serving label values to the per-basis values a food stores. */
    fun perBasisFromServing(perServing: Nutrients, servingBaseAmount: Double, unit: BaseUnit): Nutrients {
        require(servingBaseAmount > 0) { "Serving size must be positive" }
        return perServing.scaled(unit.basis / servingBaseAmount)
    }

    /**
     * The serving choices offered for a food: its own servings first, then standard units.
     * Duplicates by label are dropped.
     */
    fun servingChoices(unit: BaseUnit, own: List<Serving>): List<Serving> {
        val standard = when (unit) {
            BaseUnit.GRAM -> listOf(
                Serving("g", 1.0),
                Serving("100 g", 100.0),
                Serving("oz", GRAMS_PER_OUNCE),
            )
            BaseUnit.MILLILITER -> listOf(
                Serving("ml", 1.0),
                Serving("100 ml", 100.0),
                Serving("fl oz", ML_PER_FLUID_OUNCE),
            )
            BaseUnit.SERVING -> if (own.isEmpty()) listOf(Serving("serving", 1.0)) else emptyList()
        }
        return (own + standard).distinctBy { it.label.lowercase() }
    }
}
