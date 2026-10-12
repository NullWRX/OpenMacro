package io.github.nullwrx.openmacro.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nullwrx.openmacro.core.MealDefaults
import io.github.nullwrx.openmacro.data.db.FoodEntity
import io.github.nullwrx.openmacro.data.db.FoodSource
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.ui.MealPicker
import io.github.nullwrx.openmacro.ui.appViewModel
import io.github.nullwrx.openmacro.ui.foodSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    onBack: () -> Unit,
    onOpenFood: (foodId: Long, date: Long, meal: Int) -> Unit,
    onScanBarcode: (date: Long, meal: Int) -> Unit,
    onScanLabel: (date: Long, meal: Int) -> Unit,
    onQuickAdd: (date: Long, meal: Int) -> Unit,
    onCreateFood: (date: Long, meal: Int) -> Unit,
) {
    val vm = appViewModel { c, h -> AddFoodViewModel(c, h) }
    val query by vm.query.collectAsStateWithLifecycle()
    val meal by vm.meal.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val local by vm.localResults.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val energyUnit = settings?.energyUnit ?: EnergyUnit.KCAL
    val meals = settings?.meals ?: MealDefaults.DEFAULT_MEALS

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val open: (FoodEntity) -> Unit = { food -> vm.open(food) { id -> onOpenFood(id, vm.date, meal) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add food") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { MealPicker(meals, meal, vm::setMeal) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search foods") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (local.isEmpty()) vm.searchOnline() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { onScanBarcode(vm.date, meal) },
                    label = { Text("Scan barcode") },
                    leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                )
                AssistChip(
                    onClick = { onScanLabel(vm.date, meal) },
                    label = { Text("Scan label") },
                    leadingIcon = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null) },
                )
                AssistChip(
                    onClick = { onQuickAdd(vm.date, meal) },
                    label = { Text("Quick add") },
                    leadingIcon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
                )
                AssistChip(
                    onClick = { onCreateFood(vm.date, meal) },
                    label = { Text("Create food") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                )
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (query.isBlank()) {
                    foodSection("Favorites", favorites, energyUnit, open, vm::quickLog)
                    foodSection("Recent", recents, energyUnit, open, vm::quickLog)
                    if (favorites.isEmpty() && recents.isEmpty()) {
                        item {
                            Text(
                                "Search for a food, scan a barcode, or create your own. Foods you log show up here " +
                                    "so you can add them again with one tap.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    foodSection("On this phone", local, energyUnit, open, vm::quickLog)
                    if (local.isEmpty()) {
                        item {
                            Text(
                                "No saved foods match.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    onlineSection(online, query, energyUnit, vm::searchOnline, open, vm::quickLog)
                }
            }
        }
    }
}

private fun LazyListScope.foodSection(
    title: String,
    foods: List<FoodEntity>,
    energyUnit: EnergyUnit,
    onOpen: (FoodEntity) -> Unit,
    onQuickLog: (FoodEntity) -> Unit,
) {
    if (foods.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title) }
    items(foods, key = { "$title-${it.source}-${it.id}-${it.sourceId}" }) { food ->
        FoodRow(food, energyUnit, onOpen, onQuickLog)
    }
}

private fun LazyListScope.onlineSection(
    online: OnlineSearch,
    query: String,
    energyUnit: EnergyUnit,
    onSearch: () -> Unit,
    onOpen: (FoodEntity) -> Unit,
    onQuickLog: (FoodEntity) -> Unit,
) {
    val sameQuery = when (online) {
        is OnlineSearch.Results -> online.query == query.trim()
        is OnlineSearch.Failed -> online.query == query.trim()
        is OnlineSearch.Loading -> true
        OnlineSearch.Idle -> false
    }
    when {
        online is OnlineSearch.Loading -> item(key = "online-loading") {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Searching Open Food Facts…", modifier = Modifier.padding(start = 12.dp))
            }
        }
        online is OnlineSearch.Results && sameQuery -> {
            item(key = "online-header") { SectionHeader("Open Food Facts") }
            if (online.foods.isEmpty()) {
                item(key = "online-empty") {
                    Text(
                        "No products with nutrition data found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(online.foods, key = { "online-${it.sourceId}" }) { food -> FoodRow(food, energyUnit, onOpen, onQuickLog) }
        }
        else -> item(key = "online-button") {
            Column(Modifier.padding(16.dp)) {
                if (online is OnlineSearch.Failed && sameQuery) {
                    Text(
                        "Search failed: ${online.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Language, contentDescription = null)
                    Text("Search Open Food Facts for “${query.trim()}”", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FoodRow(
    food: FoodEntity,
    energyUnit: EnergyUnit,
    onOpen: (FoodEntity) -> Unit,
    onQuickLog: (FoodEntity) -> Unit,
) {
    ListItem(
        headlineContent = { Text(food.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val tag = if (food.source == FoodSource.USDA) "Generic · " else ""
            Text(tag + foodSubtitle(food, energyUnit), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { onQuickLog(food) }) {
                    Icon(Icons.Filled.AddCircleOutline, contentDescription = "Add ${food.name} with your usual amount")
                }
            }
        },
        modifier = Modifier.clickable { onOpen(food) },
    )
}
