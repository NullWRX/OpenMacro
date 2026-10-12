package io.github.nullwrx.openmacro.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import io.github.nullwrx.openmacro.core.CalorieMode
import io.github.nullwrx.openmacro.core.GoalPlan
import io.github.nullwrx.openmacro.core.GoalPlanner
import io.github.nullwrx.openmacro.core.GoalPreset
import io.github.nullwrx.openmacro.core.MacroMode
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.ui.appViewModel
import io.github.nullwrx.openmacro.ui.goals.GoalDetails
import io.github.nullwrx.openmacro.ui.goals.GoalForm
import io.github.nullwrx.openmacro.ui.goals.GoalPresetPicker
import io.github.nullwrx.openmacro.ui.goals.TargetsPreview
import io.github.nullwrx.openmacro.ui.onboarding.ProfileFields
import io.github.nullwrx.openmacro.ui.onboarding.ProfileForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoalsState(
    val loaded: Boolean = false,
    val profile: ProfileForm = ProfileForm(),
    val goal: GoalForm = GoalForm(),
    val autoAdjust: Boolean = true,
    val energyUnit: EnergyUnit = EnergyUnit.KCAL,
) {
    val plan: GoalPlan?
        get() {
            val p = profile.toProfile() ?: return null
            val g = goal.toGoal() ?: return null
            return GoalPlanner.plan(p, g)
        }

    /** Whether targets depend on body weight, so following weigh-ins makes a difference. */
    val followsWeight: Boolean
        get() = goal.preset != GoalPreset.CUSTOM ||
            goal.macroMode == MacroMode.PER_KG ||
            (goal.macroMode == MacroMode.PERCENT && goal.calorieMode == CalorieMode.FROM_RATE)
}

class GoalsViewModel(private val c: AppContainer, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(GoalsState())
    val state: StateFlow<GoalsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = c.settings.current()
            _state.value = GoalsState(
                loaded = true,
                profile = ProfileForm.from(s),
                goal = GoalForm.from(s.goal, s.weightUnit),
                autoAdjust = s.autoAdjustTargets,
                energyUnit = s.energyUnit,
            )
        }
    }

    fun editProfile(transform: (ProfileForm) -> ProfileForm) = _state.update { s ->
        val profile = transform(s.profile)
        s.copy(profile = profile, goal = s.goal.convertedTo(profile.unit))
    }

    fun editGoal(transform: (GoalForm) -> GoalForm) = _state.update { it.copy(goal = transform(it.goal)) }

    fun selectPreset(preset: GoalPreset) = _state.update { s ->
        s.copy(goal = s.goal.withPreset(preset, s.profile.weightKgValue))
    }

    fun setAutoAdjust(on: Boolean) = _state.update { it.copy(autoAdjust = on) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val plan = s.plan ?: return
        val goal = s.goal.toGoal() ?: return
        viewModelScope.launch {
            c.settings.update { current ->
                s.profile.applyTo(current).copy(
                    goal = goal.copy(weeklyChangeKg = plan.weeklyChangeKg),
                    autoAdjustTargets = s.autoAdjust,
                    targets = plan.targets,
                )
            }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, h -> GoalsViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val plan = state.plan

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goal and targets") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { TextButton(onClick = { vm.save(onBack) }, enabled = plan != null) { Text("Save") } },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("What are you working toward?", style = MaterialTheme.typography.titleMedium)
            GoalPresetPicker(state.goal.preset, vm::selectPreset)
            GoalDetails(state.goal, state.profile.weightKgValue, vm::editGoal)
            TargetsPreview(
                plan = plan,
                energyUnit = state.energyUnit,
                missing = if (state.profile.toProfile() == null) {
                    "Complete your profile below to see your targets."
                } else {
                    "Fill in the goal fields above to see your targets."
                },
            )
            ListItem(
                headlineContent = { Text("Update targets as my weight changes") },
                supportingContent = {
                    Text(
                        if (state.followsWeight) {
                            "Recalculated when you log a new weight, so a cut or bulk keeps fitting your body."
                        } else {
                            "This goal uses fixed numbers, so weigh-ins won't change it."
                        },
                    )
                },
                trailingContent = { Switch(checked = state.autoAdjust, onCheckedChange = vm::setAutoAdjust) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Profile", style = MaterialTheme.typography.titleMedium)
            Text(
                "Used to estimate how many calories you burn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProfileFields(state.profile, vm::editProfile)
            Spacer(Modifier.height(24.dp))
        }
    }
}
