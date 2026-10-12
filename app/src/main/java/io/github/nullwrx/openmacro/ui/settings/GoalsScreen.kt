package io.github.nullwrx.openmacro.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.nullwrx.openmacro.core.EnergyMath
import io.github.nullwrx.openmacro.core.MacroPlanner
import io.github.nullwrx.openmacro.core.MacroTargets
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.NumberField
import io.github.nullwrx.openmacro.ui.appViewModel
import io.github.nullwrx.openmacro.ui.onboarding.ProfileFields
import io.github.nullwrx.openmacro.ui.onboarding.ProfileForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class GoalsForm(
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val sodium: String = "",
) {
    fun toTargets(): MacroTargets? {
        val k = Format.parse(kcal)?.roundToInt()?.takeIf { it in 500..10000 } ?: return null
        return MacroTargets(
            energyKcal = k,
            proteinG = Format.parse(protein)?.roundToInt() ?: 0,
            carbsG = Format.parse(carbs)?.roundToInt() ?: 0,
            fatG = Format.parse(fat)?.roundToInt() ?: 0,
            fiberG = Format.parse(fiber)?.roundToInt() ?: 0,
            sodiumMg = Format.parse(sodium)?.roundToInt() ?: 0,
        )
    }

    companion object {
        fun from(t: MacroTargets) = GoalsForm(
            t.energyKcal.toString(), t.proteinG.toString(), t.carbsG.toString(), t.fatG.toString(),
            t.fiberG.toString(), t.sodiumMg.toString(),
        )
    }
}

data class GoalsState(val loaded: Boolean = false, val goals: GoalsForm = GoalsForm(), val profile: ProfileForm = ProfileForm())

class GoalsViewModel(private val c: AppContainer, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(GoalsState())
    val state: StateFlow<GoalsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = c.settings.current()
            _state.value = GoalsState(true, GoalsForm.from(s.targets), ProfileForm.from(s))
        }
    }

    fun editGoals(transform: (GoalsForm) -> GoalsForm) = _state.update { it.copy(goals = transform(it.goals)) }
    fun editProfile(transform: (ProfileForm) -> ProfileForm) = _state.update { it.copy(profile = transform(it.profile)) }

    /** Recomputes all targets from the profile. */
    fun recalculate() {
        val profile = _state.value.profile.toProfile() ?: return
        _state.update { it.copy(goals = GoalsForm.from(MacroPlanner.forProfile(profile))) }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val targets = s.goals.toTargets() ?: return
        viewModelScope.launch {
            c.settings.update { current -> s.profile.applyTo(current).copy(targets = targets) }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, h -> GoalsViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val g = state.goals
    val targets = g.toTargets()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goals and profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { TextButton(onClick = { vm.save(onBack) }, enabled = targets != null) { Text("Save") } },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Daily targets", style = MaterialTheme.typography.titleMedium)
            NumberField(g.kcal, { v -> vm.editGoals { it.copy(kcal = v) } }, "Calories", Modifier.fillMaxWidth(), suffix = "kcal", isError = targets == null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(g.protein, { v -> vm.editGoals { it.copy(protein = v) } }, "Protein", Modifier.weight(1f), suffix = "g")
                NumberField(g.carbs, { v -> vm.editGoals { it.copy(carbs = v) } }, "Carbs", Modifier.weight(1f), suffix = "g")
                NumberField(g.fat, { v -> vm.editGoals { it.copy(fat = v) } }, "Fat", Modifier.weight(1f), suffix = "g")
            }
            if (targets != null) {
                val fromMacros = EnergyMath.atwaterKcal(targets.proteinG.toDouble(), targets.carbsG.toDouble(), targets.fatG.toDouble()).roundToInt()
                val diff = fromMacros - targets.energyKcal
                Text(
                    if (kotlin.math.abs(diff) <= 25) "Macros add up to $fromMacros kcal, matching your calorie target."
                    else "Macros add up to $fromMacros kcal, ${kotlin.math.abs(diff)} ${if (diff > 0) "over" else "under"} your calorie target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(g.fiber, { v -> vm.editGoals { it.copy(fiber = v) } }, "Fiber", Modifier.weight(1f), suffix = "g")
                NumberField(g.sodium, { v -> vm.editGoals { it.copy(sodium = v) } }, "Sodium limit", Modifier.weight(1f), suffix = "mg")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Profile", style = MaterialTheme.typography.titleMedium)
            Text(
                "Used to estimate your energy needs. Change it and tap Recalculate to get new targets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProfileFields(state.profile, vm::editProfile)
            FilledTonalButton(onClick = vm::recalculate, enabled = state.profile.toProfile() != null, modifier = Modifier.fillMaxWidth()) {
                Text("Recalculate targets from profile")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
