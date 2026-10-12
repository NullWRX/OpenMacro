package io.github.nullwrx.openmacro.ui.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.nullwrx.openmacro.AppContainer
import io.github.nullwrx.openmacro.BuildConfig
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.data.settings.ThemeMode
import io.github.nullwrx.openmacro.data.settings.UserSettings
import io.github.nullwrx.openmacro.data.settings.WeightUnit
import io.github.nullwrx.openmacro.scan.ScanEngines
import io.github.nullwrx.openmacro.ui.AppBottomBar
import io.github.nullwrx.openmacro.ui.ConfirmDialog
import io.github.nullwrx.openmacro.ui.TopLevel
import io.github.nullwrx.openmacro.ui.appViewModel
import io.github.nullwrx.openmacro.ui.goals.GoalForm
import io.github.nullwrx.openmacro.ui.goals.goalSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsViewModel(private val c: AppContainer, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val usdaCount = c.foods.usdaCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun update(transform: (UserSettings) -> UserSettings) = viewModelScope.launch { c.settings.update(transform) }

    fun backup(resolver: ContentResolver, uri: Uri) = runTask("Backup saved") {
        resolver.openOutputStream(uri)?.use { c.backup.exportJson(it) } ?: error("Could not open the file")
    }

    fun restore(resolver: ContentResolver, uri: Uri) = runTask(null) {
        val summary = resolver.openInputStream(uri)?.use { c.backup.importJson(it) } ?: error("Could not open the file")
        _messages.send("Restored ${summary.entries} diary entries, ${summary.foods} foods and ${summary.weights} weigh-ins")
    }

    fun exportDiary(resolver: ContentResolver, uri: Uri) = runTask("Diary exported") {
        val meals = c.settings.current().meals
        resolver.openOutputStream(uri)?.use { c.backup.exportDiaryCsv(it, meals) } ?: error("Could not open the file")
    }

    fun exportWeights(resolver: ContentResolver, uri: Uri) = runTask("Weights exported") {
        resolver.openOutputStream(uri)?.use { c.backup.exportWeightsCsv(it) } ?: error("Could not open the file")
    }

    private fun runTask(success: String?, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { block() }
            success?.let { _messages.send(it) }
        } catch (e: Exception) {
            _messages.send("Failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (TopLevel) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenMyFoods: () -> Unit,
) {
    val vm = appViewModel { c, h -> SettingsViewModel(c, h) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val usdaCount by vm.usdaCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbar = remember { SnackbarHostState() }
    var editMeals by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.backup(resolver, it) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmRestore = uri
    }
    val diaryCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportDiary(resolver, it) }
    }
    val weightCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportWeights(resolver, it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = { AppBottomBar(TopLevel.SETTINGS, onNavigate) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Header("Goals and food")
            ListItem(
                headlineContent = { Text("Goal and targets") },
                supportingContent = {
                    Text(
                        goalSummary(GoalForm.from(s.goal, s.weightUnit)) + "\n" +
                            "${s.targets.energyKcal} kcal · P ${s.targets.proteinG} g · C ${s.targets.carbsG} g · F ${s.targets.fatG} g",
                    )
                },
                modifier = Modifier.clickable(onClick = onOpenGoals),
            )
            ListItem(
                headlineContent = { Text("My foods") },
                supportingContent = { Text("Foods you created") },
                modifier = Modifier.clickable(onClick = onOpenMyFoods),
            )
            ListItem(
                headlineContent = { Text("Meals") },
                supportingContent = { Text(s.meals.joinToString(", ")) },
                modifier = Modifier.clickable { editMeals = true },
            )

            Header("Units")
            SegmentRow("Weight", WeightUnit.entries, s.weightUnit, { if (it == WeightUnit.KG) "kg" else "lb" }) { u ->
                vm.update { it.copy(weightUnit = u) }
            }
            SegmentRow("Energy", EnergyUnit.entries, s.energyUnit, { if (it == EnergyUnit.KCAL) "kcal" else "kJ" }) { u ->
                vm.update { it.copy(energyUnit = u) }
            }

            Header("Appearance")
            SegmentRow("Theme", ThemeMode.entries, s.theme, {
                when (it) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
            }) { t -> vm.update { it.copy(theme = t) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Use wallpaper colors") },
                    supportingContent = { Text("Material You colors from your wallpaper") },
                    trailingContent = {
                        Switch(checked = s.dynamicColor, onCheckedChange = { on -> vm.update { it.copy(dynamicColor = on) } })
                    },
                )
            }

            Header("Your data")
            ListItem(
                headlineContent = { Text("Back up everything") },
                supportingContent = { Text("One JSON file with your diary, foods, weights and settings") },
                modifier = Modifier.clickable { backupLauncher.launch("openmacro-backup-$today.json") },
            )
            ListItem(
                headlineContent = { Text("Restore a backup") },
                supportingContent = { Text("Replaces the data on this phone") },
                modifier = Modifier.clickable { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
            )
            ListItem(
                headlineContent = { Text("Export diary as CSV") },
                supportingContent = { Text("For spreadsheets") },
                modifier = Modifier.clickable { diaryCsvLauncher.launch("openmacro-diary-$today.csv") },
            )
            ListItem(
                headlineContent = { Text("Export weights as CSV") },
                modifier = Modifier.clickable { weightCsvLauncher.launch("openmacro-weights-$today.csv") },
            )
            Text(
                "Everything stays on this phone. No account, no ads, no tracking. Only barcode lookups and " +
                    "searches you start go to Open Food Facts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Header("Food database")
            ListItem(
                headlineContent = { Text("Generic foods (USDA)") },
                supportingContent = {
                    Text(if (usdaCount > 0) "$usdaCount foods available offline" else "Not included in this build yet")
                },
            )

            Header("About")
            ListItem(
                headlineContent = { Text("OpenMacro ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("Free and open source · scanner: ${ScanEngines.engineName}") },
                modifier = Modifier.clickable { showAbout = true },
            )
            ListItem(
                headlineContent = { Text("Source code") },
                supportingContent = { Text(BuildConfig.PROJECT_URL) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PROJECT_URL)))
                },
            )
        }
    }

    if (editMeals) {
        settings?.let { s ->
            MealsDialog(
                meals = s.meals,
                onDismiss = { editMeals = false },
                onSave = { list ->
                    vm.update { it.copy(meals = list) }
                    editMeals = false
                },
            )
        }
    }
    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = "Restore this backup?",
            text = "Your current diary, foods, weights and settings on this phone are replaced by the backup's. This can't be undone.",
            confirmLabel = "Restore",
            onDismiss = { confirmRestore = null },
            onConfirm = { vm.restore(resolver, uri) },
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About and data sources") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("OpenMacro is free software under the GNU General Public License v3.")
                    Text(
                        "Packaged food data comes from Open Food Facts contributors under the Open Database License (ODbL). " +
                            "Product images are CC BY-SA. Data may be incomplete or wrong, so check it against the package.",
                    )
                    Text("Generic food data comes from USDA FoodData Central, which is in the public domain (CC0).")
                    Text(
                        "Not medical advice. Calorie and macro targets are estimates; talk to a professional before " +
                            "making big changes, especially if you are pregnant, a teen, or have a medical condition.",
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentRow(label: String, options: List<T>, selected: T, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(optionLabel(option)) }
            }
        }
    }
}

@Composable
private fun MealsDialog(meals: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var text by remember { mutableStateOf(meals.joinToString("\n")) }
    val list = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "One meal per line, in the order of your day (up to 8). Removing a meal from the end shows its " +
                        "past entries under the new last meal.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(list.take(8)) }, enabled = list.isNotEmpty()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
