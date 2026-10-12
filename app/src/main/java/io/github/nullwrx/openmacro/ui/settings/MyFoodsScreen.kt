package io.github.nullwrx.openmacro.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.ui.appContainer
import io.github.nullwrx.openmacro.ui.foodSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFoodsScreen(onBack: () -> Unit, onOpenFood: (Long) -> Unit, onCreate: () -> Unit) {
    val c = appContainer()
    val foodsFlow = remember(c) { c.foods.customFoods() }
    val foods by foodsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val unit = settings?.energyUnit ?: EnergyUnit.KCAL

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My foods") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Create food") },
            )
        },
    ) { padding ->
        if (foods.isEmpty()) {
            Text(
                "Foods you create or add from a label show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(foods, key = { it.id }) { food ->
                    ListItem(
                        headlineContent = { Text(food.name) },
                        supportingContent = { Text(foodSubtitle(food, unit)) },
                        modifier = Modifier.clickable { onOpenFood(food.id) },
                    )
                }
            }
        }
    }
}
