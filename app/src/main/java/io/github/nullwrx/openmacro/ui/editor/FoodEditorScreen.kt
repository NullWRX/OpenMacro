package io.github.nullwrx.openmacro.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nullwrx.openmacro.core.BaseUnit
import io.github.nullwrx.openmacro.ui.ConfirmDialog
import io.github.nullwrx.openmacro.ui.NumberField
import io.github.nullwrx.openmacro.ui.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorScreen(
    onBack: () -> Unit,
    onScanLabel: (barcode: String?) -> Unit,
    onSaved: (foodId: Long, date: Long, meal: Int) -> Unit,
) {
    val vm = appViewModel { c, h -> FoodEditorViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val f = state.form
    val unitSymbol = if (f.unit == BaseUnit.MILLILITER) "ml" else "g"
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.existing != null) "Edit food" else "New food") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete food")
                        }
                    }
                    TextButton(onClick = { vm.save { id -> onSaved(id, vm.date, vm.meal) } }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(onClick = { onScanLabel(f.barcode.ifBlank { null }) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DocumentScanner, contentDescription = null)
                Text(
                    if (state.filledFromLabel) "Scan the label again" else "Fill in from the nutrition label",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.filledFromLabel) {
                Text(
                    "Filled in from the label. Check each value before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            OutlinedTextField(
                value = f.name,
                onValueChange = { v -> vm.edit { it.copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                isError = state.showErrors && state.nameError,
                supportingText = if (state.showErrors && state.nameError) ({ Text("A name is required") }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = f.brand,
                    onValueChange = { v -> vm.edit { it.copy(brand = v) } },
                    label = { Text("Brand (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f.barcode,
                    onValueChange = { v -> vm.edit { it.copy(barcode = v.filter(Char::isDigit).take(14)) } },
                    label = { Text("Barcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Nutrition values are", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = f.basis == Basis.PER_SERVING,
                    onClick = { vm.edit { it.copy(basis = Basis.PER_SERVING) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 3),
                ) { Text("Per serving") }
                SegmentedButton(
                    selected = f.basis == Basis.PER_100 && f.unit == BaseUnit.GRAM,
                    onClick = { vm.edit { it.copy(basis = Basis.PER_100, unit = BaseUnit.GRAM) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 3),
                ) { Text("Per 100 g") }
                SegmentedButton(
                    selected = f.basis == Basis.PER_100 && f.unit == BaseUnit.MILLILITER,
                    onClick = { vm.edit { it.copy(basis = Basis.PER_100, unit = BaseUnit.MILLILITER) } },
                    shape = SegmentedButtonDefaults.itemShape(2, 3),
                ) { Text("Per 100 ml") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = f.servingDesc,
                    onValueChange = { v -> vm.edit { it.copy(servingDesc = v) } },
                    label = { Text("Serving (e.g. 1 bar)") },
                    singleLine = true,
                    modifier = Modifier.weight(1.3f),
                )
                NumberField(
                    value = f.servingWeight,
                    onValueChange = { v -> vm.edit { it.copy(servingWeight = v) } },
                    label = "Weighs",
                    suffix = unitSymbol,
                    isError = state.showErrors && state.weightError,
                    modifier = Modifier.weight(1f),
                )
            }
            if (f.basis == Basis.PER_SERVING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.edit { it.copy(unit = BaseUnit.GRAM) } }, enabled = f.unit != BaseUnit.GRAM) {
                        Text("Weight in g")
                    }
                    TextButton(onClick = { vm.edit { it.copy(unit = BaseUnit.MILLILITER) } }, enabled = f.unit != BaseUnit.MILLILITER) {
                        Text("Volume in ml")
                    }
                }
            }
            Text(
                when {
                    f.basis == Basis.PER_100 -> "Give the serving's weight so you can log by serving, not just by $unitSymbol."
                    f.servingWeight.isBlank() -> "Without a weight you can log this food by serving only."
                    else -> "You can log by serving or by $unitSymbol."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.showErrors && state.weightError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberField(
                value = f.servingsPerPackage,
                onValueChange = { v -> vm.edit { it.copy(servingsPerPackage = v) } },
                label = "Servings per package (optional)",
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                if (f.basis == Basis.PER_100) "Per 100 $unitSymbol" else "Per serving",
                style = MaterialTheme.typography.titleSmall,
            )
            NumberField(
                value = f.kcal,
                onValueChange = { v -> vm.edit { it.copy(kcal = v) } },
                label = "Calories",
                suffix = "kcal",
                isError = state.showErrors && state.kcalError,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(f.protein, { v -> vm.edit { it.copy(protein = v) } }, "Protein", Modifier.weight(1f), suffix = "g")
                NumberField(f.carbs, { v -> vm.edit { it.copy(carbs = v) } }, "Carbs", Modifier.weight(1f), suffix = "g")
                NumberField(f.fat, { v -> vm.edit { it.copy(fat = v) } }, "Fat", Modifier.weight(1f), suffix = "g")
            }
            state.suggestedKcal?.let { suggested ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                        Text(
                            "The macros add up to about $suggested kcal. Check the calories or the macros.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        TextButton(onClick = vm::useSuggestedKcal) { Text("Use $suggested") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(f.fiber, { v -> vm.edit { it.copy(fiber = v) } }, "Fiber", Modifier.weight(1f), suffix = "g")
                NumberField(f.sugar, { v -> vm.edit { it.copy(sugar = v) } }, "Sugars", Modifier.weight(1f), suffix = "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(f.saturatedFat, { v -> vm.edit { it.copy(saturatedFat = v) } }, "Saturated fat", Modifier.weight(1f), suffix = "g")
                NumberField(f.sodium, { v -> vm.edit { it.copy(sodium = v) } }, "Sodium", Modifier.weight(1f), suffix = "mg")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this food?",
            text = "It will disappear from search. Entries already in your diary stay as they are.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { vm.delete(onBack) },
        )
    }
}
