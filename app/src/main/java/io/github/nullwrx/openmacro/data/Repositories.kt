package io.github.nullwrx.openmacro.data

import io.github.nullwrx.openmacro.core.Barcodes
import io.github.nullwrx.openmacro.core.BaseUnit
import io.github.nullwrx.openmacro.core.FtsQuery
import io.github.nullwrx.openmacro.core.Nutrients
import io.github.nullwrx.openmacro.core.Portions
import io.github.nullwrx.openmacro.core.Serving
import io.github.nullwrx.openmacro.data.db.AppDatabase
import io.github.nullwrx.openmacro.data.db.DiaryEntryEntity
import io.github.nullwrx.openmacro.data.db.FoodEntity
import io.github.nullwrx.openmacro.data.db.FoodSource
import io.github.nullwrx.openmacro.data.db.NutrientColumns
import io.github.nullwrx.openmacro.data.db.ServingColumn
import io.github.nullwrx.openmacro.data.db.WeightEntity
import io.github.nullwrx.openmacro.data.db.coreServings
import io.github.nullwrx.openmacro.data.off.OffLookup
import io.github.nullwrx.openmacro.data.off.OffProduct
import io.github.nullwrx.openmacro.data.off.OpenFoodFactsClient
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

sealed interface BarcodeResult {
    val code: String

    /** A usable food, from the phone or freshly fetched and saved. */
    data class Found(override val code: String, val food: FoodEntity) : BarcodeResult

    /** The product exists online but has no nutrition facts yet. */
    data class Incomplete(override val code: String, val name: String, val brand: String?) : BarcodeResult

    data class NotFound(override val code: String) : BarcodeResult

    data class Offline(override val code: String, val message: String) : BarcodeResult
}

class FoodRepository(
    private val db: AppDatabase,
    private val off: OpenFoodFactsClient,
) {
    private val foods = db.foods()

    suspend fun get(id: Long): FoodEntity? = foods.get(id)
    fun observe(id: Long): Flow<FoodEntity?> = foods.observe(id)
    fun recentForMeal(meal: Int, limit: Int = 30): Flow<List<FoodEntity>> = foods.recentForMeal(meal, limit)
    fun favorites(): Flow<List<FoodEntity>> = foods.favorites()
    fun customFoods(): Flow<List<FoodEntity>> = foods.customFoods()
    fun usdaCount(): Flow<Int> = foods.usdaCount()

    suspend fun searchLocal(query: String, limit: Int = 50): List<FoodEntity> {
        val match = FtsQuery.build(query) ?: return emptyList()
        return foods.search(match, limit)
    }

    /** Searches Open Food Facts; results are not saved until one is picked ([ensureSaved]). */
    suspend fun searchOnline(query: String): Result<List<FoodEntity>> =
        off.search(query).map { list -> list.map { it.toEntity() } }

    /** Saves an online result (or returns the copy already saved) and returns its local row. */
    suspend fun ensureSaved(food: FoodEntity): FoodEntity {
        if (food.id != 0L) return food
        val sourceId = food.sourceId
        if (sourceId != null) {
            foods.findBySource(food.source, sourceId)?.let { existing ->
                val refreshed = food.copy(
                    id = existing.id,
                    favorite = existing.favorite,
                    hidden = false,
                    createdAt = existing.createdAt,
                    updatedAt = System.currentTimeMillis(),
                )
                foods.update(refreshed)
                return refreshed
            }
        }
        val id = foods.insert(food)
        return food.copy(id = id)
    }

    suspend fun lookupBarcode(raw: String): BarcodeResult {
        val code = Barcodes.normalize(raw)
        foods.findByBarcode(Barcodes.variants(code))?.let { return BarcodeResult.Found(code, it) }
        return when (val result = off.product(code)) {
            is OffLookup.Found -> {
                val product = result.product
                if (product.hasNutrition) {
                    BarcodeResult.Found(code, ensureSaved(product.toEntity()))
                } else {
                    BarcodeResult.Incomplete(code, product.name, product.brand)
                }
            }
            OffLookup.NotFound -> BarcodeResult.NotFound(code)
            is OffLookup.Failed -> BarcodeResult.Offline(code, result.error.message ?: "Network error")
        }
    }

    suspend fun saveCustom(food: FoodEntity): Long {
        val now = System.currentTimeMillis()
        return if (food.id == 0L) {
            foods.insert(food.copy(source = FoodSource.CUSTOM, createdAt = now, updatedAt = now))
        } else {
            foods.update(food.copy(updatedAt = now))
            food.id
        }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = foods.setFavorite(id, favorite)
    suspend fun hide(id: Long) = foods.hide(id)

    fun servingChoices(food: FoodEntity): List<Serving> =
        Portions.servingChoices(food.baseUnit, food.coreServings())

    private fun OffProduct.toEntity(): FoodEntity {
        val servings = buildList {
            if (servingQuantity != null && servingQuantity > 0) {
                add(ServingColumn(servingLabel ?: "1 serving", servingQuantity))
            }
        }
        return FoodEntity(
            source = FoodSource.OFF,
            sourceId = code,
            barcode = code,
            name = name,
            brand = brand,
            baseUnit = baseUnit,
            nutrients = NutrientColumns.from(per100),
            servings = servings,
            nutriScore = nutriScore,
            novaGroup = novaGroup,
        )
    }
}

class DiaryRepository(private val db: AppDatabase) {
    private val diary = db.diary()

    fun observeDay(date: Long): Flow<List<DiaryEntryEntity>> = diary.observeDay(date)
    suspend fun get(id: Long): DiaryEntryEntity? = diary.get(id)
    suspend fun lastForFood(foodId: Long): DiaryEntryEntity? = diary.lastForFood(foodId)

    /** Logs [count] x [serving] of [food], or updates entry [entryId] when given. */
    suspend fun logFood(
        food: FoodEntity,
        date: Long,
        meal: Int,
        serving: Serving,
        count: Double,
        entryId: Long? = null,
    ): Long {
        val baseAmount = serving.baseAmount * count
        val nutrients = Portions.nutrientsFor(food.nutrients.toCore(), food.baseUnit, baseAmount)
        val existing = entryId?.let { diary.get(it) }
        val entry = DiaryEntryEntity(
            id = existing?.id ?: 0,
            date = date,
            meal = meal,
            foodId = food.id,
            name = food.name,
            brand = food.brand,
            servingLabel = serving.label,
            servingCount = count,
            baseAmount = baseAmount,
            baseUnit = food.baseUnit,
            nutrients = NutrientColumns.from(nutrients),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        return if (existing != null) {
            diary.update(entry)
            existing.id
        } else {
            diary.insert(entry)
        }
    }

    suspend fun quickAdd(date: Long, meal: Int, name: String, nutrients: Nutrients, entryId: Long? = null): Long {
        val existing = entryId?.let { diary.get(it) }
        val entry = DiaryEntryEntity(
            id = existing?.id ?: 0,
            date = date,
            meal = meal,
            foodId = null,
            name = name.ifBlank { "Quick add" },
            brand = null,
            servingLabel = "quick add",
            servingCount = 1.0,
            baseAmount = 1.0,
            baseUnit = BaseUnit.SERVING,
            nutrients = NutrientColumns.from(nutrients),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        return if (existing != null) {
            diary.update(entry)
            existing.id
        } else {
            diary.insert(entry)
        }
    }

    suspend fun delete(id: Long) = diary.delete(id)

    /** Puts back an entry removed by [delete], for undo. */
    suspend fun restore(entry: DiaryEntryEntity) {
        diary.insert(entry)
    }

    suspend fun move(id: Long, date: Long, meal: Int) {
        val e = diary.get(id) ?: return
        diary.update(e.copy(date = date, meal = meal))
    }

    suspend fun copy(id: Long, date: Long, meal: Int) {
        val e = diary.get(id) ?: return
        diary.insert(e.copy(id = 0, date = date, meal = meal, createdAt = System.currentTimeMillis()))
    }

    /** Copies every entry of a meal; returns how many were copied. */
    suspend fun copyMeal(fromDate: Long, fromMeal: Int, toDate: Long, toMeal: Int): Int {
        val now = System.currentTimeMillis()
        val entries = diary.meal(fromDate, fromMeal)
        diary.insertAll(entries.mapIndexed { i, e -> e.copy(id = 0, date = toDate, meal = toMeal, createdAt = now + i) })
        return entries.size
    }

    /** Copies a whole day, meal by meal; returns how many entries were copied. */
    suspend fun copyDay(fromDate: Long, toDate: Long): Int {
        val now = System.currentTimeMillis()
        val entries = diary.day(fromDate)
        db.withTransaction {
            diary.insertAll(entries.mapIndexed { i, e -> e.copy(id = 0, date = toDate, createdAt = now + i) })
        }
        return entries.size
    }
}

class WeightRepository(db: AppDatabase) {
    private val weights = db.weights()

    fun observeAll(): Flow<List<WeightEntity>> = weights.observeAll()
    suspend fun latest(): WeightEntity? = weights.latest()
    suspend fun log(date: Long, kg: Double) = weights.upsert(WeightEntity(date, kg))
    suspend fun delete(date: Long) = weights.delete(date)
}
