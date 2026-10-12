package io.github.nullwrx.openmacro.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.core.MacroTargets
import io.github.nullwrx.openmacro.core.MealDefaults
import io.github.nullwrx.openmacro.core.Nutrients
import io.github.nullwrx.openmacro.data.db.DiaryEntryEntity
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class MealSection(
    val index: Int,
    val name: String,
    val entries: List<DiaryEntryEntity>,
    val totals: Nutrients,
)

data class DiaryState(
    val date: Long,
    val meals: List<MealSection> = emptyList(),
    val totals: Nutrients = Nutrients.ZERO,
    val targets: MacroTargets = MacroTargets(2000, 100, 250, 67, 28, 2300),
    val energyUnit: EnergyUnit = EnergyUnit.KCAL,
    val mealNames: List<String> = MealDefaults.DEFAULT_MEALS,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    private val date: StateFlow<Long> = handle.getStateFlow(KEY_DATE, LocalDate.now().toEpochDay())

    val state: StateFlow<DiaryState> = combine(
        date,
        c.settings.settings,
        date.flatMapLatest { c.diary.observeDay(it) },
    ) { day, settings, entries ->
        val meals = settings.meals.mapIndexed { i, name ->
            val mealEntries = entries.filter { it.meal == i }
            MealSection(i, name, mealEntries, Nutrients.sum(mealEntries.map { it.nutrients.toCore() }))
        }
        // Entries whose meal no longer exists (the meal list was shortened) show under the last meal.
        val orphans = entries.filter { it.meal >= settings.meals.size }
        val fixedMeals = if (orphans.isEmpty() || meals.isEmpty()) meals else {
            val last = meals.last()
            val merged = last.entries + orphans
            meals.dropLast(1) + last.copy(entries = merged, totals = Nutrients.sum(merged.map { it.nutrients.toCore() }))
        }
        DiaryState(
            date = day,
            meals = fixedMeals,
            totals = Nutrients.sum(entries.map { it.nutrients.toCore() }),
            targets = settings.targets,
            energyUnit = settings.energyUnit,
            mealNames = settings.meals,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryState(date.value))

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setDate(epochDay: Long) {
        handle[KEY_DATE] = epochDay
    }

    fun shiftDay(days: Long) = setDate(date.value + days)

    fun mealForNow(): Int = MealDefaults.mealForHour(LocalTime.now().hour, state.value.mealNames.size)

    fun delete(entry: DiaryEntryEntity) = viewModelScope.launch { c.diary.delete(entry.id) }

    fun restore(entry: DiaryEntryEntity) = viewModelScope.launch { c.diary.restore(entry) }

    fun move(entry: DiaryEntryEntity, toDate: Long, toMeal: Int) = viewModelScope.launch {
        c.diary.move(entry.id, toDate, toMeal)
        _messages.send("Moved ${entry.name}")
    }

    fun copy(entry: DiaryEntryEntity, toDate: Long, toMeal: Int) = viewModelScope.launch {
        c.diary.copy(entry.id, toDate, toMeal)
        _messages.send("Copied ${entry.name}")
    }

    fun copyMeal(fromDate: Long, fromMeal: Int, toDate: Long, toMeal: Int) = viewModelScope.launch {
        val n = c.diary.copyMeal(fromDate, fromMeal, toDate, toMeal)
        _messages.send(if (n == 0) "Nothing to copy" else "Copied $n ${if (n == 1) "item" else "items"}")
    }

    fun copyDayFrom(fromDate: Long) = viewModelScope.launch {
        val n = c.diary.copyDay(fromDate, date.value)
        _messages.send(if (n == 0) "That day has nothing logged" else "Copied $n ${if (n == 1) "item" else "items"}")
    }

    companion object {
        private const val KEY_DATE = "diary_date"
    }
}
