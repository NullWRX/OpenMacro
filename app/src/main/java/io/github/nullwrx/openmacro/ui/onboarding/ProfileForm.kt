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

/** Body and activity fields as typed, in the person's chosen units. The goal lives in GoalForm. */
data class ProfileForm(
    val unit: WeightUnit = WeightUnit.KG,
    val sex: Sex = Sex.FEMALE,
    val birthYear: String = "",
    val heightCm: String = "",
    val heightFt: String = "",
    val heightIn: String = "",
    val weight: String = "",
    val activity: ActivityLevel = ActivityLevel.LIGHT,
) {
    val heightCmValue: Double?
        get() = if (unit == WeightUnit.KG) {
            Format.parse(heightCm)
        } else {
            val ft = Format.parse(heightFt) ?: return null
            Units.inToCm(ft * 12 + (Format.parse(heightIn) ?: 0.0))
        }

    val weightKgValue: Double? get() = Format.parse(weight)?.let { Format.toKg(it, unit) }

    /** The profile for planning, or null while something is missing or out of range. */
    fun toProfile(): Profile? {
        val year = birthYear.toIntOrNull() ?: return null
        val age = LocalDate.now().year - year
        if (age !in 13..110) return null
        val h = heightCmValue?.takeIf { it in 100.0..250.0 } ?: return null
        val w = weightKgValue?.takeIf { it in 30.0..350.0 } ?: return null
        return Profile(sex, age, h, w, activity, weeklyChangeKg = 0.0)
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
        )
    }

    /** Switches units, converting what was already typed. */
    fun convertedTo(target: WeightUnit): ProfileForm {
        if (target == unit) return this
        val cm = heightCmValue
        val kg = weightKgValue
        val totalIn = cm?.let { Units.cmToIn(it) }
        return copy(
            unit = target,
            heightCm = cm?.roundToInt()?.toString() ?: heightCm,
            heightFt = totalIn?.let { (it / 12).toInt().toString() } ?: heightFt,
            heightIn = totalIn?.let { (it - (it / 12).toInt() * 12).roundToInt().toString() } ?: heightIn,
            weight = kg?.let { Format.amount(Format.weightValue(it, target)) } ?: weight,
        )
    }

    companion object {
        fun from(s: UserSettings): ProfileForm {
            val totalIn = Units.cmToIn(s.heightCm)
            val ft = (totalIn / 12).toInt()
            return ProfileForm(
                unit = s.weightUnit,
                sex = s.sex,
                birthYear = s.birthYear.toString(),
                heightCm = s.heightCm.roundToInt().toString(),
                heightFt = ft.toString(),
                heightIn = (totalIn - ft * 12).roundToInt().toString(),
                weight = Format.amount(Format.weightValue(s.weightKg, s.weightUnit)),
                activity = s.activity,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFields(form: ProfileForm, edit: ((ProfileForm) -> ProfileForm) -> Unit) {
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
        NumberField(
            form.weight,
            { v -> edit { it.copy(weight = v) } },
            "Current weight",
            Modifier.fillMaxWidth(),
            suffix = Format.weightUnit(form.unit),
        )
        ChoiceField(
            label = "Activity",
            selected = form.activity,
            options = ActivityLevel.entries,
            optionLabel = { "${it.label} · ${it.description}" },
            onSelect = { a -> edit { it.copy(activity = a) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
