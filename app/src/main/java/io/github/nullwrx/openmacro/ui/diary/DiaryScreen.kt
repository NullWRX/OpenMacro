package io.github.nullwrx.openmacro.ui.diary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nullwrx.openmacro.core.MacroTargets
import io.github.nullwrx.openmacro.core.Nutrients
import io.github.nullwrx.openmacro.data.db.DiaryEntryEntity
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.ui.AppBottomBar
import io.github.nullwrx.openmacro.ui.DayPickerDialog
import io.github.nullwrx.openmacro.ui.DestinationDialog
import io.github.nullwrx.openmacro.ui.Format
import io.github.nullwrx.openmacro.ui.MacroProgress
import io.github.nullwrx.openmacro.ui.TopLevel
import io.github.nullwrx.openmacro.ui.appViewModel
import io.github.nullwrx.openmacro.ui.theme.LocalMacroColors
import io.github.nullwrx.openmacro.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface DiaryDialog {
    data object PickDay : DiaryDialog
    data object CopyDayFrom : DiaryDialog
    data class MoveEntry(val entry: DiaryEntryEntity) : DiaryDialog
    data class CopyEntry(val entry: DiaryEntryEntity) : DiaryDialog
    data class CopyMealTo(val meal: Int) : DiaryDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    onAdd: (date: Long, meal: Int) -> Unit,
    onOpenFoodEntry: (foodId: Long, date: Long, meal: Int, entryId: Long) -> Unit,
    onOpenQuickAdd: (date: Long, meal: Int, entryId: Long) -> Unit,
    onScan: (date: Long, meal: Int) -> Unit,
    onNavigate: (TopLevel) -> Unit,
) {
    val vm = appViewModel { c, h -> DiaryViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<DiaryDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TextButton(onClick = { dialog = DiaryDialog.PickDay }) {
                        Text(Format.dayTitle(LocalDate.ofEpochDay(state.date)), style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.shiftDay(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.shiftDay(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Go to today") }, onClick = {
                                menuOpen = false
                                vm.setDate(LocalDate.now().toEpochDay())
                            })
                            DropdownMenuItem(text = { Text("Copy yesterday into this day") }, onClick = {
                                menuOpen = false
                                vm.copyDayFrom(state.date - 1)
                            })
                            DropdownMenuItem(text = { Text("Copy another day into this day…") }, onClick = {
                                menuOpen = false
                                dialog = DiaryDialog.CopyDayFrom
                            })
                        }
                    }
                },
            )
        },
        bottomBar = { AppBottomBar(TopLevel.DIARY, onNavigate) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(state.date, vm.mealForNow()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add food") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                SummaryCard(
                    totals = state.totals,
                    targets = state.targets,
                    energyUnit = state.energyUnit,
                    goalLabel = state.goalLabel,
                    onScan = { onScan(state.date, vm.mealForNow()) },
                )
            }
            items(state.meals, key = { "meal-${it.index}" }) { section ->
                MealCard(
                    section = section,
                    energyUnit = state.energyUnit,
                    onAdd = { onAdd(state.date, section.index) },
                    onCopyFromYesterday = { vm.copyMeal(state.date - 1, section.index, state.date, section.index) },
                    onCopyTo = { dialog = DiaryDialog.CopyMealTo(section.index) },
                    onOpen = { e ->
                        val foodId = e.foodId
                        if (foodId != null) onOpenFoodEntry(foodId, e.date, e.meal, e.id) else onOpenQuickAdd(e.date, e.meal, e.id)
                    },
                    onMove = { dialog = DiaryDialog.MoveEntry(it) },
                    onCopy = { dialog = DiaryDialog.CopyEntry(it) },
                    onDelete = { e ->
                        vm.delete(e)
                        scope.launch {
                            val result = snackbar.showSnackbar("Deleted ${e.name}", actionLabel = "Undo", withDismissAction = true)
                            if (result == SnackbarResult.ActionPerformed) vm.restore(e)
                        }
                    },
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        DiaryDialog.PickDay -> DayPickerDialog(state.date, onDismiss = { dialog = null }, onPick = vm::setDate)
        DiaryDialog.CopyDayFrom -> DayPickerDialog(
            initialDay = state.date - 1,
            onDismiss = { dialog = null },
            onPick = { vm.copyDayFrom(it) },
        )
        is DiaryDialog.MoveEntry -> DestinationDialog(
            title = "Move ${d.entry.name}",
            confirmLabel = "Move",
            initialDay = d.entry.date,
            initialMeal = d.entry.meal,
            meals = state.mealNames,
            onDismiss = { dialog = null },
            onConfirm = { day, meal -> vm.move(d.entry, day, meal) },
        )
        is DiaryDialog.CopyEntry -> DestinationDialog(
            title = "Copy ${d.entry.name}",
            confirmLabel = "Copy",
            initialDay = d.entry.date + 1,
            initialMeal = d.entry.meal,
            meals = state.mealNames,
            onDismiss = { dialog = null },
            onConfirm = { day, meal -> vm.copy(d.entry, day, meal) },
        )
        is DiaryDialog.CopyMealTo -> DestinationDialog(
            title = "Copy ${state.mealNames.getOrElse(d.meal) { "meal" }} to",
            confirmLabel = "Copy",
            initialDay = state.date + 1,
            initialMeal = d.meal,
            meals = state.mealNames,
            onDismiss = { dialog = null },
            onConfirm = { day, meal -> vm.copyMeal(state.date, d.meal, day, meal) },
        )
    }
}

@Composable
private fun SummaryCard(totals: Nutrients, targets: MacroTargets, energyUnit: EnergyUnit, goalLabel: String, onScan: () -> Unit) {
    val macro = LocalMacroColors.current
    val eaten = Format.energyValue(totals.energyKcal, energyUnit)
    val goal = Format.energyValue(targets.energyKcal.toDouble(), energyUnit)
    val left = goal - eaten
    val fraction = if (goal > 0) eaten.toFloat() / goal else 0f
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    CircularProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.size(120.dp),
                        strokeWidth = 10.dp,
                        color = if (left < 0) macro.over else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${kotlin.math.abs(left)}",
                            style = MaterialTheme.typography.headlineMedium.merge(NumberStyle),
                        )
                        Text(
                            if (left >= 0) "${Format.energyUnit(energyUnit)} left" else "${Format.energyUnit(energyUnit)} over",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (left < 0) macro.over else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroProgress("Protein", totals.proteinG, targets.proteinG, macro.protein)
                    MacroProgress("Carbs", totals.carbsG, targets.carbsG, macro.carbs)
                    MacroProgress("Fat", totals.fatG, targets.fatG, macro.fat)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (goalLabel.isNotEmpty()) {
                        Text(goalLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "Eaten $eaten · Goal $goal ${Format.energyUnit(energyUnit)}",
                        style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Fiber ${Format.gramsValue(totals.fiberG ?: 0.0)}/${targets.fiberG} g · " +
                            "Sodium ${(totals.sodiumMg ?: 0.0).toInt()}/${targets.sodiumMg} mg",
                        style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onScan) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan a barcode")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealCard(
    section: MealSection,
    energyUnit: EnergyUnit,
    onAdd: () -> Unit,
    onCopyFromYesterday: () -> Unit,
    onCopyTo: () -> Unit,
    onOpen: (DiaryEntryEntity) -> Unit,
    onMove: (DiaryEntryEntity) -> Unit,
    onCopy: (DiaryEntryEntity) -> Unit,
    onDelete: (DiaryEntryEntity) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(section.name, style = MaterialTheme.typography.titleMedium)
                    if (section.entries.isNotEmpty()) {
                        Text(
                            "${Format.energy(section.totals.energyKcal, energyUnit)} · " +
                                "P ${Format.gramsValue(section.totals.proteinG)} · C ${Format.gramsValue(section.totals.carbsG)} · " +
                                "F ${Format.gramsValue(section.totals.fatG)}",
                            style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add to ${section.name}") }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "${section.name} options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Copy from yesterday") }, onClick = {
                            menuOpen = false
                            onCopyFromYesterday()
                        })
                        if (section.entries.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Copy this meal to…") }, onClick = {
                                menuOpen = false
                                onCopyTo()
                            })
                        }
                    }
                }
            }
            if (section.entries.isEmpty()) {
                Text(
                    "Nothing logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            } else {
                section.entries.forEachIndexed { i, entry ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    EntryRow(
                        entry = entry,
                        energyUnit = energyUnit,
                        onOpen = { onOpen(entry) },
                        onMove = { onMove(entry) },
                        onCopy = { onCopy(entry) },
                        onDelete = { onDelete(entry) },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: DiaryEntryEntity,
    energyUnit: EnergyUnit,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val amount = if (entry.foodId == null) "Quick add" else "${Format.amount(entry.servingCount)} × ${entry.servingLabel}"
                Text(
                    listOfNotNull(amount, entry.brand).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${Format.energyValue(entry.nutrients.energyKcal, energyUnit)}",
                style = MaterialTheme.typography.bodyLarge.merge(NumberStyle),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onOpen() })
            DropdownMenuItem(text = { Text("Move to…") }, onClick = { menuOpen = false; onMove() })
            DropdownMenuItem(text = { Text("Copy to…") }, onClick = { menuOpen = false; onCopy() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
        }
    }
}
