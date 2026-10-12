package io.github.nullwrx.openmacro

import android.app.Application
import io.github.nullwrx.openmacro.core.label.ParsedLabel
import io.github.nullwrx.openmacro.data.DiaryRepository
import io.github.nullwrx.openmacro.data.FoodRepository
import io.github.nullwrx.openmacro.data.WeightRepository
import io.github.nullwrx.openmacro.data.backup.BackupManager
import io.github.nullwrx.openmacro.data.db.AppDatabase
import io.github.nullwrx.openmacro.data.off.OpenFoodFactsClient
import io.github.nullwrx.openmacro.data.settings.SettingsRepository
import io.github.nullwrx.openmacro.data.usda.UsdaSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** A finished label scan waiting for the food editor to pick it up. */
data class LabelScan(val label: ParsedLabel, val barcode: String?)

/** Hand-wired dependencies; small enough not to need a DI framework. */
class AppContainer(app: Application) {
    val database: AppDatabase = AppDatabase.build(app)
    val settings = SettingsRepository(app)
    private val offClient = OpenFoodFactsClient(BuildConfig.OFF_USER_AGENT)
    val foods = FoodRepository(database, offClient)
    val diary = DiaryRepository(database)
    val weights = WeightRepository(database)
    val backup = BackupManager(database, settings, BuildConfig.VERSION_NAME)
    val usdaSeeder = UsdaSeeder(app, database, settings)

    /** Hand-off from the label scanner to the food editor. */
    val pendingLabelScan = MutableStateFlow<LabelScan?>(null)

    /** Scope for work that should finish even if the screen that started it goes away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class OpenMacroApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.usdaSeeder.seedIfNeeded() }
    }
}
