package io.github.nullwrx.openmacro.ui.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.nullwrx.openmacro.core.CalorieMode
import io.github.nullwrx.openmacro.core.EnergyMath
import io.github.nullwrx.openmacro.core.GoalPlan
import io.github.nullwrx.openmacro.core.GoalPreset
import io.github.nullwrx.openmacro.core.MacroMode
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.data.settings.WeightUnit
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.NumberField
import io.github.nullwrx.openmacro.ui.NutrientRow
import io.github.nullwrx.openmacro.ui.theme.LocalMacroColors
import io.github.nullwrx.openmacro.ui.theme.NumberStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/** Short description of a goal for lists: "Lose fat · 0.4 kg/week". */
fun goalSummary(form: GoalForm): String {
    val change = form.weeklyChangeKg()
    val unit = Format.weightUnit(form.unit)
    return when {
        form.preset == GoalPreset.CUSTOM -> "Custom goal"
        change == null || change == 0.0 -> form.preset.label
        else -> "${form.preset.label} · ${Format.amount(abs(Format.weightValue(change, form.unit)))} $unit/week"
    }
}

/** The list of goals to choose from; the chosen one expands to explain itself. */
@Composable
fun GoalPresetPicker(selected: GoalPreset, onSelect: (GoalPreset) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GoalPreset.entries.forEach { preset ->
            val isSelected = preset == selected
            OutlinedCard(
                border = BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, onClick = { onSelect(preset) }, role = Role.RadioButton),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    RadioButton(selected = isSelected, onClick = null, modifier = Modifier.padding(top = 2.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(preset.label, style = MaterialTheme.typography.titleMedium)
                        Text(preset.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isSelected && preset != GoalPreset.CUSTOM) {
                            Spacer(Modifier.height(4.dp))
                            Text(preset.description, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Protein ${Format.amount(preset.proteinPerKg)} g/kg · fat ${(preset.fatFraction * 100).roundToInt()}% of calories · carbs fill the rest",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rate for presets, or the full custom editor; plus fiber and sodium. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetails(form: GoalForm, weightKg: Double?, edit: ((GoalForm) -> GoalForm) -> Unit) {
    val unit = Format.weightUnit(form.unit)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (form.preset) {
            GoalPreset.MAINTAIN -> Unit
            GoalPreset.CUSTOM -> CustomGoalEditor(form, edit)
            else -> {
                val verb = if (form.preset.direction < 0) "Lose" else "Gain"
                val suggested = weightKg?.let { abs(form.preset.defaultWeeklyChangeKg(it)) }
                NumberField(
                    value = form.rate,
                    onValueChange = { v -> edit { it.copy(rate = v) } },
                    label = "$verb per week",
                    suffix = unit,
                    isError = form.weeklyChangeKg() == null,
                    supportingText = suggested?.let {
                        "Suggested: ${Format.amount(Format.weightValue(it, form.unit).let { v -> (v * 20).roundToInt() / 20.0 })} $unit " +
                            "(${Format.amount(abs(form.preset.weeklyChangePct))}% of body weight). Up to 1% a week."
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                form.fiber, { v -> edit { it.copy(fiber = v) } }, "Fiber", Modifier.weight(1f), suffix = "g",
                supportingText = if (form.fiber.isBlank()) "Blank = automatic" else null,
            )
            NumberField(form.sodium, { v -> edit { it.copy(sodium = v) } }, "Sodium limit", Modifier.weight(1f), suffix = "mg")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomGoalEditor(form: GoalForm, edit: ((GoalForm) -> GoalForm) -> Unit) {
    val unit = Format.weightUnit(form.unit)
    Text("Split macros by", style = MaterialTheme.typography.titleSmall)
    val modes = listOf(MacroMode.PERCENT to "% of calories", MacroMode.PER_KG to "g per $unit", MacroMode.GRAMS to "Grams")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = form.macroMode == mode,
                onClick = { edit { it.copy(macroMode = mode) } },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
            ) { Text(label) }
        }
    }

    if (form.macroMode != MacroMode.GRAMS) {
        Text("Set calories", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = form.calorieMode == CalorieMode.FROM_RATE,
                onClick = { edit { it.copy(calorieMode = CalorieMode.FROM_RATE) } },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("From a weekly change") }
            SegmentedButton(
                selected = form.calorieMode == CalorieMode.FIXED,
                onClick = { edit { it.copy(calorieMode = CalorieMode.FIXED) } },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Exact number") }
        }
        if (form.calorieMode == CalorieMode.FROM_RATE) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Direction.entries.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = form.direction == d,
                        onClick = { edit { it.copy(direction = d) } },
                        shape = SegmentedButtonDefaults.itemShape(i, Direction.entries.size),
                    ) { Text(d.label) }
                }
            }
            if (form.direction != Direction.MAINTAIN) {
                NumberField(
                    form.rate, { v -> edit { it.copy(rate = v) } }, "${form.direction.label} per week", Modifier.fillMaxWidth(),
                    suffix = unit, isError = form.weeklyChangeKg() == null,
                )
            }
        } else {
            NumberField(form.fixedKcal, { v -> edit { it.copy(fixedKcal = v) } }, "Calories per day", Modifier.fillMaxWidth(), suffix = "kcal")
        }
    }

    when (form.macroMode) {
        MacroMode.PERCENT -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(form.proteinPct, { v -> edit { it.copy(proteinPct = v) } }, "Protein", Modifier.weight(1f), suffix = "%")
                NumberField(form.carbsPct, { v -> edit { it.copy(carbsPct = v) } }, "Carbs", Modifier.weight(1f), suffix = "%")
                NumberField(form.fatPct, { v -> edit { it.copy(fatPct = v) } }, "Fat", Modifier.weight(1f), suffix = "%")
            }
            val sum = listOf(form.proteinPct, form.carbsPct, form.fatPct).sumOf { Format.parse(it)?.roundToInt() ?: 0 }
            Text(
                if (sum == 100) "Adds up to 100%." else "Adds up to $sum%; make it 100%.",
                style = MaterialTheme.typography.bodySmall,
                color = if (sum == 100) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        MacroMode.PER_KG -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(form.proteinPerWeight, { v -> edit { it.copy(proteinPerWeight = v) } }, "Protein", Modifier.weight(1f), suffix = "g/$unit")
                NumberField(form.fatPerWeight, { v -> edit { it.copy(fatPerWeight = v) } }, "Fat", Modifier.weight(1f), suffix = "g/$unit")
            }
            Text(
                "Per $unit of body weight. Carbs fill the remaining calories. " +
                    if (form.unit == WeightUnit.LB) "1 g/lb of protein is a common lifting target." else "1.6 to 2.2 g/kg of protein suits most people who train.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MacroMode.GRAMS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(form.proteinG, { v -> edit { it.copy(proteinG = v) } }, "Protein", Modifier.weight(1f), suffix = "g")
                NumberField(form.carbsG, { v -> edit { it.copy(carbsG = v) } }, "Carbs", Modifier.weight(1f), suffix = "g")
                NumberField(form.fatG, { v -> edit { it.copy(fatG = v) } }, "Fat", Modifier.weight(1f), suffix = "g")
            }
            Text(
                "Calories come from these grams (4 per gram of protein and carbs, 9 per gram of fat).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The targets a goal produces, with any warnings, updated live as the goal changes. */
@Composable
fun TargetsPreview(plan: GoalPlan?, energyUnit: EnergyUnit, missing: String = "Fill in the fields above to see your targets.") {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Your daily targets", style = MaterialTheme.typography.titleMedium)
            if (plan == null) {
                Text(missing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                PlanBody(plan, energyUnit)
            }
        }
    }
}

@Composable
private fun PlanBody(plan: GoalPlan, energyUnit: EnergyUnit) {
    val macro = LocalMacroColors.current
    val t = plan.targets
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
        Text("${Format.energyValue(t.energyKcal.toDouble(), energyUnit)}", style = MaterialTheme.typography.displaySmall.merge(NumberStyle))
        Text(" ${Format.energyUnit(energyUnit)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
    }
    val diff = t.energyKcal - plan.tdeeKcal
    val note = when {
        abs(diff) < 20 -> ""
        diff < 0 -> ", so this is a ${Format.energy(-diff.toDouble(), energyUnit)} deficit"
        else -> ", so this is a ${Format.energy(diff.toDouble(), energyUnit)} surplus"
    }
    Text(
        "Maintenance is about ${Format.energy(plan.tdeeKcal.toDouble(), energyUnit)}$note.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    val total = t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9
    fun share(kcal: Int): String = if (total > 0) "${(kcal * 100.0 / total).roundToInt()}% of energy" else ""
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MacroCell("Protein", t.proteinG, share(t.proteinG * 4), macro.protein)
        MacroCell("Carbs", t.carbsG, share(t.carbsG * 4), macro.carbs)
        MacroCell("Fat", t.fatG, share(t.fatG * 9), macro.fat)
    }
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    NutrientRow("Fiber", "${t.fiberG} g")
    NutrientRow("Sodium limit", "${t.sodiumMg} mg")
    if (abs(EnergyMath.atwaterKcal(t.proteinG.toDouble(), t.carbsG.toDouble(), t.fatG.toDouble()) - t.energyKcal) > 50) {
        Text(
            "The macros add up to $total kcal, which doesn't match the calorie target.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    plan.warnings.forEach { w ->
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 8.dp))
            Text(w, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MacroCell(label: String, grams: Int, share: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text("$grams g", style = MaterialTheme.typography.titleLarge.merge(NumberStyle))
        Text(share, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
