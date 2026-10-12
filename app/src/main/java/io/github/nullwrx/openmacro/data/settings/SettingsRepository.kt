package io.github.nullwrx.openmacro.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.nullwrx.openmacro.core.ActivityLevel
import io.github.nullwrx.openmacro.core.CalorieMode
import io.github.nullwrx.openmacro.core.GoalPlanner
import io.github.nullwrx.openmacro.core.GoalPreset
import io.github.nullwrx.openmacro.core.GoalSettings
import io.github.nullwrx.openmacro.core.MacroMode
import io.github.nullwrx.openmacro.core.MacroTargets
import io.github.nullwrx.openmacro.core.MealDefaults
import io.github.nullwrx.openmacro.core.Profile
import io.github.nullwrx.openmacro.core.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

enum class WeightUnit { KG, LB }
enum class EnergyUnit { KCAL, KJ }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val onboarded: Boolean = false,
    val sex: Sex = Sex.FEMALE,
    val birthYear: Int = 1990,
    val heightCm: Double = 170.0,
    /** Weight used for planning; the weight log holds the history. */
    val weightKg: Double = 70.0,
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val goal: GoalSettings = GoalSettings(),
    /** Recompute [targets] from [goal] whenever the newest weigh-in changes. */
    val autoAdjustTargets: Boolean = true,
    val targets: MacroTargets = MacroTargets(2000, 100, 250, 67, 28, 2300),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val energyUnit: EnergyUnit = EnergyUnit.KCAL,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val meals: List<String> = MealDefaults.DEFAULT_MEALS,
    val usdaSeedVersion: String? = null,
) {
    val ageYears: Int get() = (LocalDate.now().year - birthYear).coerceIn(13, 120)

    fun profile() = Profile(sex, ageYears, heightCm, weightKg, activity, goal.weeklyChangeKg)

    /** These settings with [targets] recomputed from the goal and profile. */
    fun withPlannedTargets(): UserSettings = copy(targets = GoalPlanner.plan(profile(), goal).targets)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val sex = stringPreferencesKey("sex")
        val birthYear = intPreferencesKey("birth_year")
        val heightCm = doublePreferencesKey("height_cm")
        val weightKg = doublePreferencesKey("weight_kg")
        val activity = stringPreferencesKey("activity")
        val weeklyChange = doublePreferencesKey("weekly_change_kg")
        val kcal = intPreferencesKey("target_kcal")
        val protein = intPreferencesKey("target_protein")
        val carbs = intPreferencesKey("target_carbs")
        val fat = intPreferencesKey("target_fat")
        val fiber = intPreferencesKey("target_fiber")
        val sodium = intPreferencesKey("target_sodium")
        val weightUnit = stringPreferencesKey("weight_unit")
        val energyUnit = stringPreferencesKey("energy_unit")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val meals = stringPreferencesKey("meals")
        val usdaSeedVersion = stringPreferencesKey("usda_seed_version")

        // Goal (added in v0.2.0).
        val goalPreset = stringPreferencesKey("goal_preset")
        val calorieMode = stringPreferencesKey("goal_calorie_mode")
        val fixedKcal = intPreferencesKey("goal_fixed_kcal")
        val macroMode = stringPreferencesKey("goal_macro_mode")
        val proteinPct = intPreferencesKey("goal_protein_pct")
        val carbsPct = intPreferencesKey("goal_carbs_pct")
        val fatPct = intPreferencesKey("goal_fat_pct")
        val proteinPerKg = doublePreferencesKey("goal_protein_per_kg")
        val fatPerKg = doublePreferencesKey("goal_fat_per_kg")
        val proteinG = intPreferencesKey("goal_protein_g")
        val carbsG = intPreferencesKey("goal_carbs_g")
        val fatG = intPreferencesKey("goal_fat_g")
        val goalFiber = intPreferencesKey("goal_fiber_g")
        val goalSodium = intPreferencesKey("goal_sodium_mg")
        val autoAdjust = booleanPreferencesKey("auto_adjust_targets")
    }

    val settings: Flow<UserSettings> = store.data.map(::read)

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): UserSettings {
        val d = UserSettings()
        val targets = MacroTargets(
            energyKcal = p[Keys.kcal] ?: d.targets.energyKcal,
            proteinG = p[Keys.protein] ?: d.targets.proteinG,
            carbsG = p[Keys.carbs] ?: d.targets.carbsG,
            fatG = p[Keys.fat] ?: d.targets.fatG,
            fiberG = p[Keys.fiber] ?: d.targets.fiberG,
            sodiumMg = p[Keys.sodium] ?: d.targets.sodiumMg,
        )
        val onboarded = p[Keys.onboarded] ?: d.onboarded
        val weeklyChange = p[Keys.weeklyChange] ?: 0.0
        val storedPreset = p[Keys.goalPreset]

        // Before v0.2.0 there was no goal, only targets. Keep those exact targets as a custom
        // goal in grams, and don't auto-adjust, so updating never changes anyone's numbers.
        val goal: GoalSettings
        val autoAdjust: Boolean
        if (storedPreset == null && onboarded) {
            goal = GoalSettings(
                preset = GoalPreset.CUSTOM,
                weeklyChangeKg = weeklyChange,
                macroMode = MacroMode.GRAMS,
                proteinG = targets.proteinG,
                carbsG = targets.carbsG,
                fatG = targets.fatG,
                fiberG = targets.fiberG,
                sodiumMg = targets.sodiumMg,
            )
            autoAdjust = false
        } else {
            val g = d.goal
            goal = GoalSettings(
                preset = storedPreset.enumOr(g.preset),
                weeklyChangeKg = weeklyChange,
                calorieMode = p[Keys.calorieMode].enumOr(g.calorieMode),
                fixedKcal = p[Keys.fixedKcal] ?: g.fixedKcal,
                macroMode = p[Keys.macroMode].enumOr(g.macroMode),
                proteinPct = p[Keys.proteinPct] ?: g.proteinPct,
                carbsPct = p[Keys.carbsPct] ?: g.carbsPct,
                fatPct = p[Keys.fatPct] ?: g.fatPct,
                proteinPerKg = p[Keys.proteinPerKg] ?: g.proteinPerKg,
                fatPerKg = p[Keys.fatPerKg] ?: g.fatPerKg,
                proteinG = p[Keys.proteinG] ?: g.proteinG,
                carbsG = p[Keys.carbsG] ?: g.carbsG,
                fatG = p[Keys.fatG] ?: g.fatG,
                fiberG = p[Keys.goalFiber],
                sodiumMg = p[Keys.goalSodium] ?: g.sodiumMg,
            )
            autoAdjust = p[Keys.autoAdjust] ?: d.autoAdjustTargets
        }

        return UserSettings(
            onboarded = onboarded,
            sex = p[Keys.sex].enumOr(d.sex),
            birthYear = p[Keys.birthYear] ?: d.birthYear,
            heightCm = p[Keys.heightCm] ?: d.heightCm,
            weightKg = p[Keys.weightKg] ?: d.weightKg,
            activity = p[Keys.activity].enumOr(d.activity),
            goal = goal,
            autoAdjustTargets = autoAdjust,
            targets = targets,
            weightUnit = p[Keys.weightUnit].enumOr(d.weightUnit),
            energyUnit = p[Keys.energyUnit].enumOr(d.energyUnit),
            theme = p[Keys.theme].enumOr(d.theme),
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            meals = p[Keys.meals]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() } ?: d.meals,
            usdaSeedVersion = p[Keys.usdaSeedVersion],
        )
    }

    private fun write(p: MutablePreferences, s: UserSettings) {
        p[Keys.onboarded] = s.onboarded
        p[Keys.sex] = s.sex.name
        p[Keys.birthYear] = s.birthYear
        p[Keys.heightCm] = s.heightCm
        p[Keys.weightKg] = s.weightKg
        p[Keys.activity] = s.activity.name
        p[Keys.kcal] = s.targets.energyKcal
        p[Keys.protein] = s.targets.proteinG
        p[Keys.carbs] = s.targets.carbsG
        p[Keys.fat] = s.targets.fatG
        p[Keys.fiber] = s.targets.fiberG
        p[Keys.sodium] = s.targets.sodiumMg
        p[Keys.weightUnit] = s.weightUnit.name
        p[Keys.energyUnit] = s.energyUnit.name
        p[Keys.theme] = s.theme.name
        p[Keys.dynamicColor] = s.dynamicColor
        p[Keys.meals] = s.meals.joinToString("\n")
        if (s.usdaSeedVersion != null) p[Keys.usdaSeedVersion] = s.usdaSeedVersion else p.remove(Keys.usdaSeedVersion)

        val g = s.goal
        p[Keys.goalPreset] = g.preset.name
        p[Keys.weeklyChange] = g.weeklyChangeKg
        p[Keys.calorieMode] = g.calorieMode.name
        p[Keys.fixedKcal] = g.fixedKcal
        p[Keys.macroMode] = g.macroMode.name
        p[Keys.proteinPct] = g.proteinPct
        p[Keys.carbsPct] = g.carbsPct
        p[Keys.fatPct] = g.fatPct
        p[Keys.proteinPerKg] = g.proteinPerKg
        p[Keys.fatPerKg] = g.fatPerKg
        p[Keys.proteinG] = g.proteinG
        p[Keys.carbsG] = g.carbsG
        p[Keys.fatG] = g.fatG
        val fiber = g.fiberG
        if (fiber != null) p[Keys.goalFiber] = fiber else p.remove(Keys.goalFiber)
        p[Keys.goalSodium] = g.sodiumMg
        p[Keys.autoAdjust] = s.autoAdjustTargets
    }
}

private inline fun <reified E : Enum<E>> String?.enumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
