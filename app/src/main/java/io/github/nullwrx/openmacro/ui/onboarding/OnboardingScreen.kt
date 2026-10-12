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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import io.github.nullwrx.openmacro.core.EnergyMath
import io.github.nullwrx.openmacro.core.MacroPlanner
import io.github.nullwrx.openmacro.core.MacroTargets
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.NumberField
import io.github.nullwrx.openmacro.ui.NutrientRow
import io.github.nullwrx.openmacro.ui.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class OnboardingState(
    val step: Int = 0,
    val profile: ProfileForm = ProfileForm(),
    val tdee: Int = 0,
    val targets: MacroTargets? = null,
    val kcalText: String = "",
)

class OnboardingViewModel(private val c: AppContainer, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        // Default to imperial units where people usually use them.
        val country = java.util.Locale.getDefault().country
        if (country in setOf("US", "LR", "MM")) {
            _state.update { it.copy(profile = it.profile.copy(unit = io.github.nullwrx.openmacro.data.settings.WeightUnit.LB, rate = "1")) }
        }
    }

    fun next() = _state.update { it.copy(step = it.step + 1) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun editProfile(transform: (ProfileForm) -> ProfileForm) = _state.update { it.copy(profile = transform(it.profile)) }

    fun computeTargets() {
        val profile = _state.value.profile.toProfile() ?: return
        val targets = MacroPlanner.forProfile(profile)
        _state.update {
            it.copy(
                step = 2,
                tdee = EnergyMath.tdee(profile).roundToInt(),
                targets = targets,
                kcalText = targets.energyKcal.toString(),
            )
        }
    }

    /** Changing calories re-splits the macros the same way. */
    fun setCalories(text: String) {
        val profile = _state.value.profile.toProfile()
        val kcal = Format.parse(text)?.roundToInt()
        _state.update { s ->
            val targets = if (profile != null && kcal != null && kcal in 800..8000) {
                MacroPlanner.fromCalories(kcal, profile.weightKg)
            } else {
                s.targets
            }
            s.copy(kcalText = text, targets = targets)
        }
    }

    fun finish(onDone: () -> Unit) {
        val s = _state.value
        val targets = s.targets ?: return
        viewModelScope.launch {
            c.settings.update { current -> s.profile.applyTo(current).copy(targets = targets, onboarded = true) }
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
                        "This sets your starting calorie and macro targets. It stays on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProfileFields(state.profile, vm::editProfile)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = vm::back) { Text("Back") }
                        Button(onClick = vm::computeTargets, enabled = state.profile.toProfile() != null) { Text("Continue") }
                    }
                }
                else -> {
                    val t = state.targets
                    Text("Your daily targets", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "You burn about ${state.tdee} kcal a day by our estimate. The targets below follow your goal; " +
                            "you can change any of them later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NumberField(state.kcalText, vm::setCalories, "Calories", Modifier.fillMaxWidth(), suffix = "kcal")
                    if (t != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(16.dp)) {
                                NutrientRow("Protein", "${t.proteinG} g")
                                NutrientRow("Carbs", "${t.carbsG} g")
                                NutrientRow("Fat", "${t.fatG} g")
                                NutrientRow("Fiber", "${t.fiberG} g")
                                NutrientRow("Sodium limit", "${t.sodiumMg} mg")
                            }
                        }
                    }
                    Text(
                        "Estimates only, not medical advice. If you are pregnant, under 18, or managing a health " +
                            "condition, check targets with a professional.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = vm::back) { Text("Back") }
                        Button(onClick = { vm.finish(onFinished) }, enabled = t != null) { Text("Start tracking") }
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
            "Barcode and nutrition-label scanning, no subscription",
            "Your data stays on your phone; export it any time",
            "No ads, no account, open source",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Set up my targets") }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
}
