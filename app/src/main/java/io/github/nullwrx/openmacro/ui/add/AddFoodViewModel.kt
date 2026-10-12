package io.github.nullwrx.openmacro.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.data.db.FoodEntity
import io.github.nullwrx.openmacro.ui.defaultServing
import io.github.nullwrx.openmacro.ui.lastServing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OnlineSearch {
    data object Idle : OnlineSearch
    data class Loading(val query: String) : OnlineSearch
    data class Results(val query: String, val foods: List<FoodEntity>) : OnlineSearch
    data class Failed(val query: String, val message: String) : OnlineSearch
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AddFoodViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val date: Long = checkNotNull(handle["date"])
    private val _meal = MutableStateFlow(checkNotNull<Int>(handle["meal"]))
    val meal: StateFlow<Int> = _meal.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val localResults: StateFlow<List<FoodEntity>> = _query
        .debounce(120)
        .mapLatest { q -> if (q.isBlank()) emptyList() else c.foods.searchLocal(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recents: StateFlow<List<FoodEntity>> = _meal
        .flatMapLatest { c.foods.recentForMeal(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<FoodEntity>> = c.foods.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _online = MutableStateFlow<OnlineSearch>(OnlineSearch.Idle)
    val online: StateFlow<OnlineSearch> = _online.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setQuery(q: String) {
        _query.value = q
        if (_online.value !is OnlineSearch.Loading) _online.value = OnlineSearch.Idle
    }

    fun setMeal(meal: Int) {
        _meal.value = meal
    }

    fun searchOnline() {
        val q = _query.value.trim()
        if (q.isEmpty() || _online.value is OnlineSearch.Loading) return
        _online.value = OnlineSearch.Loading(q)
        viewModelScope.launch {
            _online.value = c.foods.searchOnline(q).fold(
                onSuccess = { OnlineSearch.Results(q, it) },
                onFailure = { OnlineSearch.Failed(q, it.message ?: "Could not reach Open Food Facts") },
            )
        }
    }

    /** Saves an online result if needed, then hands back its id for the detail screen. */
    fun open(food: FoodEntity, onReady: (Long) -> Unit) {
        viewModelScope.launch { onReady(c.foods.ensureSaved(food).id) }
    }

    /** One tap: logs the amount used last time, or the default serving. */
    fun quickLog(food: FoodEntity) {
        viewModelScope.launch {
            val saved = c.foods.ensureSaved(food)
            val choices = c.foods.servingChoices(saved)
            val (serving, count) = lastServing(saved, c.diary.lastForFood(saved.id), choices)
                ?: (defaultServing(saved) to 1.0)
            c.diary.logFood(saved, date, _meal.value, serving, count)
            val mealName = settings.value?.meals?.getOrNull(_meal.value) ?: "diary"
            _messages.send("Added ${saved.name} to $mealName")
        }
    }
}
