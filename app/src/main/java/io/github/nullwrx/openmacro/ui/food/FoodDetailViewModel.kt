package io.github.nullwrx.openmacro.ui.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.core.MealDefaults
import io.github.nullwrx.openmacro.core.Serving
import io.github.nullwrx.openmacro.data.db.FoodEntity
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.defaultServing
import io.github.nullwrx.openmacro.ui.lastServing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class FoodDetailState(
    val loading: Boolean = true,
    val food: FoodEntity? = null,
    val choices: List<Serving> = emptyList(),
    val servingIndex: Int = 0,
    val countText: String = "1",
    val date: Long = LocalDate.now().toEpochDay(),
    val meal: Int = 0,
    val editing: Boolean = false,
) {
    val serving: Serving? get() = choices.getOrNull(servingIndex)
    val count: Double? get() = Format.parse(countText)?.takeIf { it > 0 }
}

class FoodDetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val foodId: Long = checkNotNull(handle["foodId"])
    private val argDate: Long = handle["date"] ?: -1L
    private val argMeal: Int = handle["meal"] ?: -1
    private val entryId: Long? = (handle.get<Long>("entryId") ?: -1L).takeIf { it >= 0 }

    private val _state = MutableStateFlow(FoodDetailState(editing = entryId != null))
    val state: StateFlow<FoodDetailState> = _state.asStateFlow()

    val settings = c.settings.settings

    init {
        viewModelScope.launch {
            val food = c.foods.get(foodId)
            if (food == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val choices = c.foods.servingChoices(food).toMutableList()
            val mealCount = c.settings.current().meals.size
            val entry = entryId?.let { c.diary.get(it) }
            val (serving, count) = if (entry != null) {
                lastServing(food, entry, choices) ?: (defaultServing(food) to entry.servingCount)
            } else {
                lastServing(food, c.diary.lastForFood(food.id), choices) ?: (defaultServing(food) to 1.0)
            }
            var index = choices.indexOfFirst { it.label == serving.label }
            if (index < 0) {
                choices.add(0, serving)
                index = 0
            }
            _state.value = FoodDetailState(
                loading = false,
                food = food,
                choices = choices,
                servingIndex = index,
                countText = Format.amount(count),
                date = entry?.date ?: argDate.takeIf { it >= 0 } ?: LocalDate.now().toEpochDay(),
                meal = entry?.meal ?: argMeal.takeIf { it >= 0 }
                    ?: MealDefaults.mealForHour(LocalTime.now().hour, mealCount),
                editing = entry != null,
            )
        }
        // Keep the favorite star in sync if the food changes elsewhere.
        viewModelScope.launch {
            c.foods.observe(foodId).collect { food ->
                if (food != null) _state.update { s -> if (s.food != null) s.copy(food = food) else s }
            }
        }
    }

    fun setCount(text: String) = _state.update { it.copy(countText = text) }
    fun setServing(index: Int) = _state.update { it.copy(servingIndex = index) }
    fun setMeal(meal: Int) = _state.update { it.copy(meal = meal) }

    fun toggleFavorite() {
        val food = _state.value.food ?: return
        viewModelScope.launch { c.foods.setFavorite(food.id, !food.favorite) }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val food = s.food ?: return
        val serving = s.serving ?: return
        val count = s.count ?: return
        viewModelScope.launch {
            c.diary.logFood(food, s.date, s.meal, serving, count, entryId)
            onDone()
        }
    }

    fun deleteEntry(onDone: () -> Unit) {
        val id = entryId ?: return
        viewModelScope.launch {
            c.diary.delete(id)
            onDone()
        }
    }
}
