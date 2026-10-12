package io.github.nullwrx.openmacro.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.core.MealDefaults
import io.github.nullwrx.openmacro.core.Nutrients
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.core.EnergyMath
import io.github.nullwrx.openmacro.ui.ChoiceField
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.NumberField
import io.github.nullwrx.openmacro.ui.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickAddState(
    val name: String = "",
    val energy: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val meal: Int = 0,
    val editing: Boolean = false,
) {
    val energyValue: Double? get() = Format.parse(energy)?.takeIf { it >= 0 }
}

class QuickAddViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val date: Long = checkNotNull(handle["date"])
    private val entryId: Long? = (handle.get<Long>("entryId") ?: -1L).takeIf { it >= 0 }
    private val _state = MutableStateFlow(QuickAddState(meal = checkNotNull<Int>(handle["meal"]), editing = entryId != null))
    val state: StateFlow<QuickAddState> = _state.asStateFlow()
    val settings = c.settings.settings

    init {
        if (entryId != null) {
            viewModelScope.launch {
                val e = c.diary.get(entryId) ?: return@launch
                val unit = c.settings.current().energyUnit
                val n = e.nutrients
                _state.value = QuickAddState(
                    name = e.name,
                    energy = Format.energyValue(n.energyKcal, unit).toString(),
                    protein = n.proteinG.takeIf { it > 0 }?.let(Format::amount).orEmpty(),
                    carbs = n.carbsG.takeIf { it > 0 }?.let(Format::amount).orEmpty(),
                    fat = n.fatG.takeIf { it > 0 }?.let(Format::amount).orEmpty(),
                    meal = e.meal,
                    editing = true,
                )
            }
        }
    }

    fun edit(transform: (QuickAddState) -> QuickAddState) = _state.update(transform)

    fun save(energyUnit: EnergyUnit, onDone: () -> Unit) {
        val s = _state.value
        val energy = s.energyValue ?: return
        val kcal = if (energyUnit == EnergyUnit.KJ) EnergyMath.kjToKcal(energy) else energy
        viewModelScope.launch {
            c.diary.quickAdd(
                date = date,
                meal = s.meal,
                name = s.name.trim(),
                nutrients = Nutrients(
                    energyKcal = kcal,
                    proteinG = Format.parse(s.protein) ?: 0.0,
                    carbsG = Format.parse(s.carbs) ?: 0.0,
                    fatG = Format.parse(s.fat) ?: 0.0,
                ),
                entryId = entryId,
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = entryId ?: return
        viewModelScope.launch {
            c.diary.delete(id)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel { c, h -> QuickAddViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = null)
    val unit = settings?.energyUnit ?: EnergyUnit.KCAL
    val meals = settings?.meals ?: MealDefaults.DEFAULT_MEALS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Edit quick add" else "Quick add") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.editing) {
                        IconButton(onClick = { vm.delete(onSaved) }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Log calories without looking up a food, for restaurant meals or when you know the number.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> vm.edit { it.copy(name = v) } },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                value = state.energy,
                onValueChange = { v -> vm.edit { it.copy(energy = v) } },
                label = "Calories",
                suffix = Format.energyUnit(unit),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(state.protein, { v -> vm.edit { it.copy(protein = v) } }, "Protein", Modifier.weight(1f), suffix = "g")
                NumberField(state.carbs, { v -> vm.edit { it.copy(carbs = v) } }, "Carbs", Modifier.weight(1f), suffix = "g")
                NumberField(state.fat, { v -> vm.edit { it.copy(fat = v) } }, "Fat", Modifier.weight(1f), suffix = "g")
            }
            ChoiceField(
                label = "Meal",
                selected = state.meal.coerceIn(0, (meals.size - 1).coerceAtLeast(0)),
                options = meals.indices.toList(),
                optionLabel = { meals.getOrElse(it) { "Meal" } },
                onSelect = { m -> vm.edit { it.copy(meal = m) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.save(unit, onSaved) },
                enabled = state.energyValue != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.editing) "Save" else "Add") }
        }
    }
}
