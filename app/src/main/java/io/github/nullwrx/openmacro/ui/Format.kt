package io.github.nullwrx.openmacro.ui

import io.github.nullwrx.openmacro.core.EnergyMath
import io.github.nullwrx.openmacro.core.Units
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.data.settings.WeightUnit
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {
    private val oneDecimal = DecimalFormat("0.#")
    private val twoDecimals = DecimalFormat("0.##")

    fun energyValue(kcal: Double, unit: EnergyUnit): Int = when (unit) {
        EnergyUnit.KCAL -> kcal.roundToInt()
        EnergyUnit.KJ -> EnergyMath.kcalToKj(kcal).roundToInt()
    }

    fun energyUnit(unit: EnergyUnit) = if (unit == EnergyUnit.KCAL) "kcal" else "kJ"

    fun energy(kcal: Double, unit: EnergyUnit): String = "${energyValue(kcal, unit)} ${energyUnit(unit)}"

    /** Grams: one decimal below 10, whole numbers above. */
    fun grams(g: Double): String = if (abs(g) < 10) "${oneDecimal.format(g)} g" else "${g.roundToInt()} g"

    fun gramsValue(g: Double): String = if (abs(g) < 10) oneDecimal.format(g) else g.roundToInt().toString()

    fun milligrams(mg: Double): String = "${mg.roundToInt()} mg"

    /** "1.5", "2", "0.33": short and without trailing zeros. */
    fun amount(x: Double): String = twoDecimals.format(x)

    fun weightValue(kg: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) kg else Units.kgToLb(kg)

    fun weight(kg: Double, unit: WeightUnit): String =
        "${oneDecimal.format(weightValue(kg, unit))} ${if (unit == WeightUnit.KG) "kg" else "lb"}"

    fun weightUnit(unit: WeightUnit) = if (unit == WeightUnit.KG) "kg" else "lb"

    fun toKg(value: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) value else Units.lbToKg(value)

    private val longDate = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val longDateYear = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    private val mediumDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun dayTitle(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> if (day.year == today.year) day.format(longDate) else day.format(longDateYear)
    }

    fun date(day: LocalDate): String = day.format(mediumDate)

    /** Parses what a person typed into a number field: accepts "," or "." as decimal mark. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
}
