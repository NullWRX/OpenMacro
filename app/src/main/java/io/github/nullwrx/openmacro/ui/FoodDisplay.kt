package io.github.nullwrx.openmacro.ui

import io.github.nullwrx.openmacro.core.BaseUnit
import io.github.nullwrx.openmacro.core.Nutrients
import io.github.nullwrx.openmacro.core.Portions
import io.github.nullwrx.openmacro.core.Serving
import io.github.nullwrx.openmacro.data.db.DiaryEntryEntity
import io.github.nullwrx.openmacro.data.db.FoodEntity
import io.github.nullwrx.openmacro.data.db.FoodSource
import io.github.nullwrx.openmacro.data.db.coreServings
import io.github.nullwrx.openmacro.data.settings.EnergyUnit

/** The serving shown by default: the food's own first serving, else 100 g/ml, else 1 serving. */
fun defaultServing(food: FoodEntity): Serving = food.coreServings().firstOrNull()
    ?: when (food.baseUnit) {
        BaseUnit.GRAM -> Serving("100 g", 100.0)
        BaseUnit.MILLILITER -> Serving("100 ml", 100.0)
        BaseUnit.SERVING -> Serving("serving", 1.0)
    }

fun nutrientsFor(food: FoodEntity, serving: Serving, count: Double): Nutrients =
    Portions.nutrientsFor(food.nutrients.toCore(), food.baseUnit, serving.baseAmount * count)

/** The serving and amount someone last logged of this food, if it still makes sense. */
fun lastServing(food: FoodEntity, last: DiaryEntryEntity?, choices: List<Serving>): Pair<Serving, Double>? {
    if (last == null || last.servingCount <= 0 || last.baseUnit != food.baseUnit) return null
    val serving = choices.firstOrNull { it.label == last.servingLabel }
        ?: Serving(last.servingLabel, last.baseAmount / last.servingCount)
    return serving to last.servingCount
}

fun sourceLabel(source: FoodSource): String = when (source) {
    FoodSource.CUSTOM -> "My food"
    FoodSource.OFF -> "Open Food Facts"
    FoodSource.USDA -> "USDA"
}

/** "Brand · 1 cup · 230 kcal" for list rows. */
fun foodSubtitle(food: FoodEntity, energyUnit: EnergyUnit): String {
    val serving = defaultServing(food)
    val kcal = nutrientsFor(food, serving, 1.0).energyKcal
    return listOfNotNull(food.brand, serving.label, Format.energy(kcal, energyUnit)).joinToString(" · ")
}
