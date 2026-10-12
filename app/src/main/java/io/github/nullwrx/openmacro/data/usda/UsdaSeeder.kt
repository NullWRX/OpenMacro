package io.github.nullwrx.openmacro.data.usda

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import io.github.nullwrx.openmacro.core.BaseUnit
import io.github.nullwrx.openmacro.data.db.AppDatabase
import io.github.nullwrx.openmacro.data.db.FoodEntity
import io.github.nullwrx.openmacro.data.db.FoodSource
import io.github.nullwrx.openmacro.data.db.NutrientColumns
import io.github.nullwrx.openmacro.data.db.ServingColumn
import io.github.nullwrx.openmacro.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream

/**
 * Loads the bundled USDA FoodData Central generic foods (public domain) into the database the
 * first time the app runs, and again whenever a newer data file ships with an update.
 *
 * The asset is produced by tools/usda/build_usda_asset.py. Columns, tab separated, values per
 * 100 g: fdc_id, name, category, kcal, protein, carbs, fat, fiber, sugar, sat_fat, sodium_mg,
 * portions ("1 cup=128;1 medium=110"). Empty cells mean unknown.
 */
class UsdaSeeder(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val version = readAssetText(VERSION_ASSET)?.lineSequence()?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return@withContext
        if (settings.current().usdaSeedVersion == version) return@withContext
        try {
            val rows = readRows()
            if (rows.isEmpty()) return@withContext
            val foods = db.foods()
            db.withTransaction {
                val existing = foods.usdaIds().associate { it.sourceId to it.id }
                val now = System.currentTimeMillis()
                val inserts = ArrayList<FoodEntity>()
                val updates = ArrayList<FoodEntity>()
                for (row in rows) {
                    val id = existing[row.sourceId]
                    if (id == null) {
                        inserts += row.copy(createdAt = now, updatedAt = now)
                    } else {
                        val old = foods.get(id) ?: continue
                        updates += row.copy(id = id, favorite = old.favorite, createdAt = old.createdAt, updatedAt = now)
                    }
                }
                inserts.chunked(500).forEach { foods.insertAll(it) }
                updates.chunked(500).forEach { foods.updateAll(it) }
            }
            settings.update { it.copy(usdaSeedVersion = version) }
            Log.i(TAG, "Loaded ${rows.size} USDA foods (data version $version)")
        } catch (e: Exception) {
            Log.e(TAG, "Could not load USDA foods", e)
        }
    }

    private fun readRows(): List<FoodEntity> {
        val stream = try {
            context.assets.open(DATA_ASSET)
        } catch (e: FileNotFoundException) {
            return emptyList()
        }
        val out = ArrayList<FoodEntity>(9000)
        GZIPInputStream(stream).bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line -> parseRow(line)?.let(out::add) }
        }
        return out
    }

    internal fun parseRow(line: String): FoodEntity? {
        val c = line.split('\t')
        if (c.size < 12) return null
        fun d(i: Int): Double? = c[i].takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val name = c[1].trim().ifEmpty { return null }
        val servings = c[11].split(';').mapNotNull { part ->
            val eq = part.lastIndexOf('=')
            if (eq <= 0) return@mapNotNull null
            val grams = part.substring(eq + 1).toDoubleOrNull() ?: return@mapNotNull null
            ServingColumn(part.substring(0, eq).trim(), grams).takeIf { grams > 0 && it.label.isNotEmpty() }
        }
        return FoodEntity(
            source = FoodSource.USDA,
            sourceId = c[0].trim(),
            name = name,
            brand = null,
            baseUnit = BaseUnit.GRAM,
            nutrients = NutrientColumns(
                energyKcal = d(3) ?: 0.0,
                proteinG = d(4) ?: 0.0,
                carbsG = d(5) ?: 0.0,
                fatG = d(6) ?: 0.0,
                fiberG = d(7),
                sugarG = d(8),
                saturatedFatG = d(9),
                sodiumMg = d(10),
            ),
            servings = servings,
        )
    }

    private fun readAssetText(name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (e: FileNotFoundException) {
        null
    }

    companion object {
        private const val TAG = "UsdaSeeder"
        const val DATA_ASSET = "foods/usda_foods.tsv.gz"
        const val VERSION_ASSET = "foods/usda_version.txt"
    }
}
