package io.github.nullwrx.openmacro.data.backup

import androidx.room.withTransaction
import io.github.nullwrx.openmacro.core.ActivityLevel
import io.github.nullwrx.openmacro.core.BaseUnit
import io.github.nullwrx.openmacro.core.Csv
import io.github.nullwrx.openmacro.core.MacroTargets
import io.github.nullwrx.openmacro.core.Sex
import io.github.nullwrx.openmacro.data.db.AppDatabase
import io.github.nullwrx.openmacro.data.db.DiaryEntryEntity
import io.github.nullwrx.openmacro.data.db.FoodEntity
import io.github.nullwrx.openmacro.data.db.FoodSource
import io.github.nullwrx.openmacro.data.db.NutrientColumns
import io.github.nullwrx.openmacro.data.db.ServingColumn
import io.github.nullwrx.openmacro.data.db.WeightEntity
import io.github.nullwrx.openmacro.data.settings.EnergyUnit
import io.github.nullwrx.openmacro.data.settings.SettingsRepository
import io.github.nullwrx.openmacro.data.settings.ThemeMode
import io.github.nullwrx.openmacro.data.settings.UserSettings
import io.github.nullwrx.openmacro.data.settings.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

@Serializable
data class NutrientsDto(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val saturatedFat: Double? = null,
    val sodiumMg: Double? = null,
)

@Serializable
data class FoodDto(
    val id: Long,
    val source: String,
    val sourceId: String? = null,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val baseUnit: String,
    val nutrients: NutrientsDto,
    val servings: List<ServingColumn> = emptyList(),
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val nutriScore: String? = null,
    val novaGroup: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Which food an entry came from. USDA foods are matched by FDC id, others by backup id. */
@Serializable
data class FoodRef(val source: String, val sourceId: String? = null, val backupId: Long? = null)

@Serializable
data class EntryDto(
    val date: String,
    val meal: Int,
    val food: FoodRef? = null,
    val name: String,
    val brand: String? = null,
    val servingLabel: String,
    val servingCount: Double,
    val baseAmount: Double,
    val baseUnit: String,
    val nutrients: NutrientsDto,
    val createdAt: Long,
)

@Serializable
data class WeightDto(val date: String, val kg: Double)

@Serializable
data class SettingsDto(
    val sex: String,
    val birthYear: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: String,
    val weeklyChangeKg: Double,
    val targetKcal: Int,
    val targetProtein: Int,
    val targetCarbs: Int,
    val targetFat: Int,
    val targetFiber: Int,
    val targetSodiumMg: Int,
    val weightUnit: String,
    val energyUnit: String,
    val theme: String,
    val dynamicColor: Boolean,
    val meals: List<String>,
)

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val appVersion: String,
    val exportedAt: Long,
    val settings: SettingsDto,
    val foods: List<FoodDto>,
    val entries: List<EntryDto>,
    val weights: List<WeightDto>,
) {
    companion object {
        const val FORMAT = "openmacro-backup"
    }
}

data class RestoreSummary(val foods: Int, val entries: Int, val weights: Int)

/** Full JSON backup and restore, plus CSV exports for spreadsheets. */
class BackupManager(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val appVersion: String,
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportJson(out: OutputStream) = withContext(Dispatchers.IO) {
        val foods = db.foods().allNonUsda()
        val usdaById = HashMap<Long, String?>()
        val usdaRows = db.foods().usdaIds()
        usdaRows.forEach { usdaById[it.id] = it.sourceId }
        val ownIds = foods.map { it.id }.toSet()
        val entries = db.diary().all().map { e ->
            val id: Long? = e.foodId
            val ref = if (id == null) {
                null
            } else if (id in ownIds) {
                FoodRef(source = "LOCAL", backupId = id)
            } else if (usdaById.containsKey(id)) {
                FoodRef(source = FoodSource.USDA.name, sourceId = usdaById[id])
            } else {
                null
            }
            e.toDto(ref)
        }
        val file = BackupFile(
            appVersion = appVersion,
            exportedAt = System.currentTimeMillis(),
            settings = settings.current().toDto(),
            foods = foods.map { it.toDto() },
            entries = entries,
            weights = db.weights().all().map { WeightDto(LocalDate.ofEpochDay(it.date).toString(), it.kg) },
        )
        out.bufferedWriter().use { it.write(json.encodeToString(BackupFile.serializer(), file)) }
    }

    /** Replaces all diary data, own foods, weights and settings with the backup's contents. */
    suspend fun importJson(input: InputStream): RestoreSummary = withContext(Dispatchers.IO) {
        val text = input.bufferedReader().use { it.readText() }
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.format == BackupFile.FORMAT) { "This file is not an OpenMacro backup" }
        require(file.version <= 1) { "This backup was made by a newer version of the app" }

        db.withTransaction {
            db.diary().deleteAll()
            db.weights().deleteAll()
            db.foods().deleteAllNonUsda()

            val idMap = HashMap<Long, Long>()
            for (dto in file.foods) {
                idMap[dto.id] = db.foods().insert(dto.toEntity())
            }
            val usdaIds = db.foods().usdaIds().associate { it.sourceId to it.id }
            db.diary().insertAll(
                file.entries.map { dto ->
                    val foodId = dto.food?.let { ref ->
                        when (ref.source) {
                            "LOCAL" -> ref.backupId?.let(idMap::get)
                            FoodSource.USDA.name -> usdaIds[ref.sourceId]
                            else -> null
                        }
                    }
                    dto.toEntity(foodId)
                },
            )
            db.weights().insertAll(
                file.weights.map { WeightEntity(LocalDate.parse(it.date).toEpochDay(), it.kg) },
            )
        }
        settings.update { current -> file.settings.applyTo(current) }
        RestoreSummary(file.foods.size, file.entries.size, file.weights.size)
    }

    suspend fun exportDiaryCsv(out: OutputStream, mealNames: List<String>) = withContext(Dispatchers.IO) {
        out.bufferedWriter().use { w ->
            w.write(
                Csv.row(
                    "date", "meal", "food", "brand", "amount", "serving", "kcal", "protein_g", "carbs_g",
                    "fat_g", "fiber_g", "sugar_g", "saturated_fat_g", "sodium_mg",
                ),
            )
            w.write("\n")
            for (e in db.diary().all()) {
                val n = e.nutrients
                w.write(
                    Csv.row(
                        LocalDate.ofEpochDay(e.date), mealNames.getOrElse(e.meal) { "Meal ${e.meal + 1}" },
                        e.name, e.brand, fmt(e.servingCount), e.servingLabel, fmt(n.energyKcal), fmt(n.proteinG),
                        fmt(n.carbsG), fmt(n.fatG), n.fiberG?.let { fmt(it) }, n.sugarG?.let { fmt(it) },
                        n.saturatedFatG?.let { fmt(it) }, n.sodiumMg?.let { fmt(it) },
                    ),
                )
                w.write("\n")
            }
        }
    }

    suspend fun exportWeightsCsv(out: OutputStream) = withContext(Dispatchers.IO) {
        out.bufferedWriter().use { w ->
            w.write(Csv.row("date", "weight_kg"))
            w.write("\n")
            for (wt in db.weights().all()) {
                w.write(Csv.row(LocalDate.ofEpochDay(wt.date), fmt(wt.kg)))
                w.write("\n")
            }
        }
    }

    private fun fmt(v: Double): String = "%.2f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')

    private fun NutrientColumns.toDto() =
        NutrientsDto(energyKcal, proteinG, carbsG, fatG, fiberG, sugarG, saturatedFatG, sodiumMg)

    private fun NutrientsDto.toColumns() =
        NutrientColumns(kcal, protein, carbs, fat, fiber, sugar, saturatedFat, sodiumMg)

    private fun FoodEntity.toDto() = FoodDto(
        id, source.name, sourceId, barcode, name, brand, baseUnit.name, nutrients.toDto(), servings,
        favorite, hidden, nutriScore, novaGroup, createdAt, updatedAt,
    )

    private fun FoodDto.toEntity() = FoodEntity(
        source = enumValueOrNull<FoodSource>(source) ?: FoodSource.CUSTOM,
        sourceId = sourceId,
        barcode = barcode,
        name = name,
        brand = brand,
        baseUnit = enumValueOrNull<BaseUnit>(baseUnit) ?: BaseUnit.GRAM,
        nutrients = nutrients.toColumns(),
        servings = servings,
        favorite = favorite,
        hidden = hidden,
        nutriScore = nutriScore,
        novaGroup = novaGroup,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun DiaryEntryEntity.toDto(ref: FoodRef?) = EntryDto(
        LocalDate.ofEpochDay(date).toString(), meal, ref, name, brand, servingLabel, servingCount,
        baseAmount, baseUnit.name, nutrients.toDto(), createdAt,
    )

    private fun EntryDto.toEntity(foodId: Long?) = DiaryEntryEntity(
        date = LocalDate.parse(date).toEpochDay(),
        meal = meal,
        foodId = foodId,
        name = name,
        brand = brand,
        servingLabel = servingLabel,
        servingCount = servingCount,
        baseAmount = baseAmount,
        baseUnit = enumValueOrNull<BaseUnit>(baseUnit) ?: BaseUnit.SERVING,
        nutrients = nutrients.toColumns(),
        createdAt = createdAt,
    )

    private fun UserSettings.toDto() = SettingsDto(
        sex.name, birthYear, heightCm, weightKg, activity.name, weeklyChangeKg,
        targets.energyKcal, targets.proteinG, targets.carbsG, targets.fatG, targets.fiberG, targets.sodiumMg,
        weightUnit.name, energyUnit.name, theme.name, dynamicColor, meals,
    )

    private fun SettingsDto.applyTo(s: UserSettings) = s.copy(
        onboarded = true,
        sex = enumValueOrNull<Sex>(sex) ?: s.sex,
        birthYear = birthYear,
        heightCm = heightCm,
        weightKg = weightKg,
        activity = enumValueOrNull<ActivityLevel>(activity) ?: s.activity,
        weeklyChangeKg = weeklyChangeKg,
        targets = MacroTargets(targetKcal, targetProtein, targetCarbs, targetFat, targetFiber, targetSodiumMg),
        weightUnit = enumValueOrNull<WeightUnit>(weightUnit) ?: s.weightUnit,
        energyUnit = enumValueOrNull<EnergyUnit>(energyUnit) ?: s.energyUnit,
        theme = enumValueOrNull<ThemeMode>(theme) ?: s.theme,
        dynamicColor = dynamicColor,
        meals = meals.ifEmpty { s.meals },
    )
}

private inline fun <reified E : Enum<E>> enumValueOrNull(name: String): E? =
    enumValues<E>().firstOrNull { it.name == name }
