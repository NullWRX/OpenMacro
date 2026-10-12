package io.github.nullwrx.openmacro.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullwrx.openmacro.core.ActivityLevel
import io.github.nullwrx.openmacro.core.Profile
import io.github.nullwrx.openmacro.core.Sex
import io.github.nullwrx.openmacro.core.Units
import io.github.nullwrx.openmacro.data.settings.UserSettings
import io.github.nullwrx.openmacro.data.settings.WeightUnit
import io.github.nullwrx.openmacro.ui.ChoiceField
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.NumberField
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Goal(val label: String) { LOSE("Lose"), MAINTAIN("Maintain"), GAIN("Gain") }

/** Profile fields as typed, in the person's chosen units. */
data class ProfileForm(
    val unit: WeightUnit = WeightUnit.KG,
    val sex: Sex = Sex.FEMALE,
    val birthYear: String = "",
    val heightCm: String = "",
    val heightFt: String = "",
    val heightIn: String = "",
    val weight: String = "",
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val goal: Goal = Goal.MAINTAIN,
    val rate: String = "0.5",
) {
    val heightCmValue: Double?
        get() = if (unit == WeightUnit.KG) {
            Format.parse(heightCm)
        } else {
            val ft = Format.parse(heightFt) ?: return null
            Units.inToCm(ft * 12 + (Format.parse(heightIn) ?: 0.0))
        }

    val weightKgValue: Double? get() = Format.parse(weight)?.let { Format.toKg(it, unit) }

    val rateKgValue: Double? get() = Format.parse(rate)?.let { Format.toKg(it, unit) }

    fun toProfile(): Profile? {
        val year = birthYear.toIntOrNull() ?: return null
        val age = LocalDate.now().year - year
        if (age !in 13..110) return null
        val h = heightCmValue?.takeIf { it in 100.0..250.0 } ?: return null
        val w = weightKgValue?.takeIf { it in 30.0..350.0 } ?: return null
        val rate = when (goal) {
            Goal.MAINTAIN -> 0.0
            else -> rateKgValue?.takeIf { it in 0.0..1.5 } ?: return null
        }
        val change = when (goal) {
            Goal.LOSE -> -rate
            Goal.MAINTAIN -> 0.0
            Goal.GAIN -> rate
        }
        return Profile(sex, age, h, w, activity, change)
    }

    fun applyTo(s: UserSettings): UserSettings {
        val p = toProfile() ?: return s.copy(weightUnit = unit)
        return s.copy(
            weightUnit = unit,
            sex = p.sex,
            birthYear = birthYear.toInt(),
            heightCm = p.heightCm,
            weightKg = p.weightKg,
            activity = p.activity,
            weeklyChangeKg = p.weeklyChangeKg,
        )
    }

    companion object {
        fun from(s: UserSettings): ProfileForm {
            val totalIn = Units.cmToIn(s.heightCm)
            val ft = (totalIn / 12).toInt()
            val inches = (totalIn - ft * 12).roundToInt()
            val goal = when {
                s.weeklyChangeKg < 0 -> Goal.LOSE
                s.weeklyChangeKg > 0 -> Goal.GAIN
                else -> Goal.MAINTAIN
            }
            val rateKg = kotlin.math.abs(s.weeklyChangeKg).takeIf { it > 0 } ?: 0.5
            return ProfileForm(
                unit = s.weightUnit,
                sex = s.sex,
                birthYear = s.birthYear.toString(),
                heightCm = s.heightCm.roundToInt().toString(),
                heightFt = ft.toString(),
                heightIn = inches.toString(),
                weight = Format.amount(Format.weightValue(s.weightKg, s.weightUnit)),
                activity = s.activity,
                goal = goal,
                rate = Format.amount(Format.weightValue(rateKg, s.weightUnit)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFields(form: ProfileForm, edit: ((ProfileForm) -> ProfileForm) -> Unit) {
    val unitLabel = Format.weightUnit(form.unit)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = form.unit == WeightUnit.KG,
                onClick = { edit { it.convertedTo(WeightUnit.KG) } },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Metric (kg, cm)") }
            SegmentedButton(
                selected = form.unit == WeightUnit.LB,
                onClick = { edit { it.convertedTo(WeightUnit.LB) } },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Imperial (lb, ft)") }
        }
        Text("Sex (for the energy formula)", style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = form.sex == Sex.FEMALE,
                onClick = { edit { it.copy(sex = Sex.FEMALE) } },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Female") }
            SegmentedButton(
                selected = form.sex == Sex.MALE,
                onClick = { edit { it.copy(sex = Sex.MALE) } },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Male") }
        }
        NumberField(form.birthYear, { v -> edit { it.copy(birthYear = v.filter(Char::isDigit).take(4)) } }, "Birth year", Modifier.fillMaxWidth())
        if (form.unit == WeightUnit.KG) {
            NumberField(form.heightCm, { v -> edit { it.copy(heightCm = v) } }, "Height", Modifier.fillMaxWidth(), suffix = "cm")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(form.heightFt, { v -> edit { it.copy(heightFt = v) } }, "Height", Modifier.weight(1f), suffix = "ft")
                NumberField(form.heightIn, { v -> edit { it.copy(heightIn = v) } }, "", Modifier.weight(1f), suffix = "in")
            }
        }
        NumberField(form.weight, { v -> edit { it.copy(weight = v) } }, "Current weight", Modifier.fillMaxWidth(), suffix = unitLabel)
        ChoiceField(
            label = "Activity",
            selected = form.activity,
            options = ActivityLevel.entries,
            optionLabel = { "${it.label} · ${it.description}" },
            onSelect = { a -> edit { it.copy(activity = a) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Goal", style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Goal.entries.forEachIndexed { i, g ->
                SegmentedButton(
                    selected = form.goal == g,
                    onClick = { edit { it.copy(goal = g) } },
                    shape = SegmentedButtonDefaults.itemShape(i, Goal.entries.size),
                ) { Text(g.label) }
            }
        }
        if (form.goal != Goal.MAINTAIN) {
            NumberField(
                form.rate,
                { v -> edit { it.copy(rate = v) } },
                "Rate",
                Modifier.fillMaxWidth(),
                suffix = "$unitLabel per week",
                supportingText = if (form.unit == WeightUnit.KG) {
                    "0.25 to 0.75 kg a week is sustainable for most people"
                } else {
                    "0.5 to 1.5 lb a week is sustainable for most people"
                },
            )
        }
    }
}

/** Switches units, converting what was already typed. */
private fun ProfileForm.convertedTo(target: WeightUnit): ProfileForm {
    if (target == unit) return this
    val cm = heightCmValue
    val kg = weightKgValue
    val rateKg = rateKgValue
    val totalIn = cm?.let { Units.cmToIn(it) }
    return copy(
        unit = target,
        heightCm = cm?.roundToInt()?.toString() ?: heightCm,
        heightFt = totalIn?.let { (it / 12).toInt().toString() } ?: heightFt,
        heightIn = totalIn?.let { (it - (it / 12).toInt() * 12).roundToInt().toString() } ?: heightIn,
        weight = kg?.let { Format.amount(Format.weightValue(it, target)) } ?: weight,
        rate = rateKg?.let { Format.amount(Format.weightValue(it, target)) } ?: rate,
    )
}
