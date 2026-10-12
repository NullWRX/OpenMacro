package io.github.nullwrx.openmacro.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nullwrx.openmacro.core.MealDefaults
import io.github.nullwrx.openmacro.core.Nutrients
import io.github.nullwrx.openmacro.data.db.FoodSource
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.ui.ChoiceField
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.NumberField
import io.github.nullwrx.openmacro.ui.NutrientRow
import io.github.nullwrx.openmacro.ui.appViewModel
import io.github.nullwrx.openmacro.ui.nutrientsFor
import io.github.nullwrx.openmacro.ui.sourceLabel
import io.github.nullwrx.openmacro.ui.theme.LocalMacroColors
import io.github.nullwrx.openmacro.ui.theme.NumberStyle
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
    onBack: () -> Unit,
    onLogged: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val vm = appViewModel { c, h -> FoodDetailViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = null)
    val energyUnit = settings?.energyUnit ?: EnergyUnit.KCAL
    val meals = settings?.meals ?: MealDefaults.DEFAULT_MEALS
    val food = state.food

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Edit entry" else "Add food") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (food != null) {
                        IconButton(onClick = vm::toggleFavorite) {
                            if (food.favorite) {
                                Icon(Icons.Filled.Star, contentDescription = "Remove from favorites", tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Outlined.StarBorder, contentDescription = "Add to favorites")
                            }
                        }
                        if (food.source == FoodSource.CUSTOM) {
                            IconButton(onClick = { onEdit(food.id) }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit food") }
                        }
                        if (state.editing) {
                            IconButton(onClick = { vm.deleteEntry(onLogged) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete entry")
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (food != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { vm.save(onLogged) },
                        enabled = state.count != null && state.serving != null,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                    ) {
                        Text(if (state.editing) "Save" else "Add to ${meals.getOrElse(state.meal) { "diary" }}")
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            food == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This food is no longer available.")
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    Text(food.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(food.brand, sourceLabel(food.source)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    NumberField(
                        value = state.countText,
                        onValueChange = vm::setCount,
                        label = "Amount",
                        isError = state.count == null,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.weight(0.8f),
                    )
                    ChoiceField(
                        label = "Serving",
                        selected = state.servingIndex,
                        options = state.choices.indices.toList(),
                        optionLabel = { i -> state.choices.getOrNull(i)?.let { servingLabel(it.label, it.baseAmount, food.baseUnit.symbol) } ?: "" },
                        onSelect = vm::setServing,
                        modifier = Modifier.weight(1.2f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChoiceField(
                        label = "Meal",
                        selected = state.meal.coerceIn(0, (meals.size - 1).coerceAtLeast(0)),
                        options = meals.indices.toList(),
                        optionLabel = { meals.getOrElse(it) { "Meal" } },
                        onSelect = vm::setMeal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Format.dayTitle(LocalDate.ofEpochDay(state.date)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.6f),
                    )
                }

                val serving = state.serving
                val count = state.count
                if (serving != null && count != null) {
                    NutritionPanel(nutrientsFor(food, serving, count), energyUnit)
                }

                if (food.nutriScore != null || food.novaGroup != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        food.nutriScore?.let { AssistChip(onClick = {}, label = { Text("Nutri-Score ${it.uppercase()}") }) }
                        food.novaGroup?.let { AssistChip(onClick = {}, label = { Text("NOVA $it") }) }
                    }
                }

                Text(
                    when (food.source) {
                        FoodSource.OFF -> "Data from Open Food Facts contributors (ODbL). Check it against the package."
                        FoodSource.USDA -> "Data from USDA FoodData Central (public domain)."
                        FoodSource.CUSTOM -> "A food you created."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** "1 cup (128 g)" when the label does not already say the weight. */
private fun servingLabel(label: String, baseAmount: Double, unitSymbol: String): String {
    val plainUnits = setOf("g", "ml", "serving", "100 g", "100 ml")
    if (label in plainUnits || unitSymbol == "serving" || label.contains(Regex("""\d\s*(g|ml)\b"""))) return label
    return "$label (${Format.amount(baseAmount)} $unitSymbol)"
}

@Composable
fun NutritionPanel(n: Nutrients, energyUnit: EnergyUnit) {
    val macro = LocalMacroColors.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${Format.energyValue(n.energyKcal, energyUnit)}",
                    style = MaterialTheme.typography.displaySmall.merge(NumberStyle),
                )
                Text(
                    " ${Format.energyUnit(energyUnit)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroStat("Protein", n.proteinG, 4.0, n.energyKcal, macro.protein)
                MacroStat("Carbs", n.carbsG, 4.0, n.energyKcal, macro.carbs)
                MacroStat("Fat", n.fatG, 9.0, n.energyKcal, macro.fat)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(4.dp))
            NutrientRow("Fiber", n.fiberG?.let(Format::grams) ?: "–")
            NutrientRow("Sugars", n.sugarG?.let(Format::grams) ?: "–")
            NutrientRow("Saturated fat", n.saturatedFatG?.let(Format::grams) ?: "–")
            NutrientRow("Sodium", n.sodiumMg?.let(Format::milligrams) ?: "–")
        }
    }
}

@Composable
private fun MacroStat(label: String, grams: Double, kcalPerGram: Double, totalKcal: Double, color: Color) {
    val pct = if (totalKcal > 0) (grams * kcalPerGram / totalKcal * 100).toInt().coerceIn(0, 100) else 0
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(Format.grams(grams), style = MaterialTheme.typography.titleLarge.merge(NumberStyle))
        Text("$pct% of energy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
