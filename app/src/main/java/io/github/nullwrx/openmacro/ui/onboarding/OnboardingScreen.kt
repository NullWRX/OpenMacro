package io.github.nullwrx.openmacro.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.core.GoalPlan
import io.github.nullwrx.openmacro.core.GoalPlanner
import io.github.nullwrx.openmacro.core.GoalPreset
import io.github.nullwrx.openmacro.data.settings.WeightUnit
import io.github.nullwrx.openmacro.ui.appViewModel
import io.github.nullwrx.openmacro.ui.goals.GoalDetails
import io.github.nullwrx.openmacro.ui.goals.GoalForm
import io.github.nullwrx.openmacro.ui.goals.GoalPresetPicker
import io.github.nullwrx.openmacro.ui.goals.TargetsPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class OnboardingState(
    val step: Int = 0,
    val profile: ProfileForm = ProfileForm(),
    val goal: GoalForm = GoalForm(),
) {
    val plan: GoalPlan?
        get() {
            val p = profile.toProfile() ?: return null
            val g = goal.toGoal() ?: return null
            return GoalPlanner.plan(p, g)
        }
}

class OnboardingViewModel(private val c: AppContainer, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        // Default to imperial units where people usually use them.
        val country = java.util.Locale.getDefault().country
        if (country in setOf("US", "LR", "MM")) {
            _state.update {
                it.copy(profile = it.profile.copy(unit = WeightUnit.LB), goal = it.goal.copy(unit = WeightUnit.LB))
            }
        }
    }

    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun next() = _state.update { s ->
        // Entering the goal step: fill the chosen goal's suggested rate for this body.
        val goal = if (s.step == 1) s.goal.withPreset(s.goal.preset, s.profile.weightKgValue) else s.goal
        s.copy(step = s.step + 1, goal = goal)
    }

    fun editProfile(transform: (ProfileForm) -> ProfileForm) = _state.update { s ->
        val profile = transform(s.profile)
        s.copy(profile = profile, goal = s.goal.convertedTo(profile.unit))
    }

    fun editGoal(transform: (GoalForm) -> GoalForm) = _state.update { it.copy(goal = transform(it.goal)) }

    fun selectPreset(preset: GoalPreset) = _state.update { s ->
        s.copy(goal = s.goal.withPreset(preset, s.profile.weightKgValue))
    }

    fun finish(onDone: () -> Unit) {
        val s = _state.value
        val plan = s.plan ?: return
        val goal = s.goal.toGoal() ?: return
        viewModelScope.launch {
            c.settings.update { current ->
                s.profile.applyTo(current).copy(
                    goal = goal.copy(weeklyChangeKg = plan.weeklyChangeKg),
                    autoAdjustTargets = true,
                    targets = plan.targets,
                    onboarded = true,
                )
            }
            s.profile.weightKgValue?.let { c.weights.log(LocalDate.now().toEpochDay(), it) }
            onDone()
        }
    }

    /** Skips setup with default targets, which can be changed later in Settings. */
    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            c.settings.update { it.copy(onboarded = true, weightUnit = _state.value.profile.unit) }
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val vm = appViewModel { c, h -> OnboardingViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.step) {
                0 -> Welcome(onStart = vm::next, onSkip = { vm.skip(onFinished) })
                1 -> {
                    Text("About you", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Used to estimate how many calories you burn. It stays on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProfileFields(state.profile, vm::editProfile)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = vm::back) { Text("Back") }
                        Button(onClick = vm::next, enabled = state.profile.toProfile() != null) { Text("Continue") }
                    }
                }
                else -> {
                    Text("Your goal", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Pick what you're working toward. You can switch goals any time in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GoalPresetPicker(state.goal.preset, vm::selectPreset)
                    GoalDetails(state.goal, state.profile.weightKgValue, vm::editGoal)
                    TargetsPreview(state.plan, io.github.nullwrx.openmacro.data.settings.EnergyUnit.KCAL)
                    Text(
                        "Estimates only, not medical advice. If you are pregnant, under 18, or managing a health " +
                            "condition, check targets with a professional.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = vm::back) { Text("Back") }
                        Button(onClick = { vm.finish(onFinished) }, enabled = state.plan != null) { Text("Start tracking") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onSkip: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Text("OpenMacro", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
    Text("Track calories and macros. Free, forever.", style = MaterialTheme.typography.titleLarge)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Goals for cutting, recomping, lean bulking or bulking, or set your own",
            "Barcode and nutrition-label scanning, no subscription",
            "Your data stays on your phone; export it any time",
            "No ads, no account, open source",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Set up my goal") }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
}
