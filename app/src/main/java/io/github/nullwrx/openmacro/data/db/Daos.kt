package io.github.nullwrx.openmacro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(food: FoodEntity): Long

    @Insert
    suspend fun insertAll(foods: List<FoodEntity>): List<Long>

    @Update
    suspend fun update(food: FoodEntity)

    @Update
    suspend fun updateAll(foods: List<FoodEntity>)

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun get(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    fun observe(id: Long): Flow<FoodEntity?>

    @Query(
        """
        SELECT * FROM foods WHERE barcode IN (:codes) AND hidden = 0
        ORDER BY CASE source WHEN 'CUSTOM' THEN 0 ELSE 1 END, updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findByBarcode(codes: List<String>): FoodEntity?

    @Query("SELECT * FROM foods WHERE source = :source AND sourceId = :sourceId LIMIT 1")
    suspend fun findBySource(source: FoodSource, sourceId: String): FoodEntity?

    /**
     * Full-text search. Favorites first, then foods logged most, then your own foods before
     * packaged products before generic ones, then shorter names.
     */
    @Query(
        """
        SELECT foods.* FROM foods
        JOIN foods_fts ON foods.id = foods_fts.rowid
        WHERE foods_fts MATCH :match AND foods.hidden = 0
        ORDER BY foods.favorite DESC,
            (SELECT COUNT(*) FROM diary_entries d WHERE d.foodId = foods.id) DESC,
            CASE foods.source WHEN 'CUSTOM' THEN 0 WHEN 'OFF' THEN 1 ELSE 2 END,
            length(foods.name)
        LIMIT :limit
        """,
    )
    suspend fun search(match: String, limit: Int): List<FoodEntity>

    /** Foods logged before, those used at this meal first, most recent first. */
    @Query(
        """
        SELECT f.* FROM foods f
        JOIN (
            SELECT foodId, MAX(createdAt) AS lastUsed,
                SUM(CASE WHEN meal = :meal THEN 1 ELSE 0 END) AS mealUses
            FROM diary_entries WHERE foodId IS NOT NULL GROUP BY foodId
        ) r ON r.foodId = f.id
        WHERE f.hidden = 0
        ORDER BY (r.mealUses > 0) DESC, r.lastUsed DESC
        LIMIT :limit
        """,
    )
    fun recentForMeal(meal: Int, limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE favorite = 1 AND hidden = 0 ORDER BY name COLLATE NOCASE")
    fun favorites(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE source = 'CUSTOM' AND hidden = 0 ORDER BY name COLLATE NOCASE")
    fun customFoods(): Flow<List<FoodEntity>>

    @Query("UPDATE foods SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE foods SET hidden = 1, favorite = 0 WHERE id = :id")
    suspend fun hide(id: Long)

    @Query("SELECT id, sourceId FROM foods WHERE source = 'USDA'")
    suspend fun usdaIds(): List<SourceIdRow>

    @Query("SELECT COUNT(*) FROM foods WHERE source = 'USDA'")
    fun usdaCount(): Flow<Int>

    @Query("SELECT * FROM foods WHERE source != 'USDA' ORDER BY id")
    suspend fun allNonUsda(): List<FoodEntity>

    @Query("DELETE FROM foods WHERE source != 'USDA'")
    suspend fun deleteAllNonUsda()
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE date = :date ORDER BY meal, createdAt, id")
    fun observeDay(date: Long): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries WHERE date = :date ORDER BY meal, createdAt, id")
    suspend fun day(date: Long): List<DiaryEntryEntity>

    @Query("SELECT * FROM diary_entries WHERE date = :date AND meal = :meal ORDER BY createdAt, id")
    suspend fun meal(date: Long, meal: Int): List<DiaryEntryEntity>

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun get(id: Long): DiaryEntryEntity?

    @Query("SELECT * FROM diary_entries WHERE foodId = :foodId ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastForFood(foodId: Long): DiaryEntryEntity?

    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<DiaryEntryEntity>)

    @Update
    suspend fun update(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM diary_entries ORDER BY date, meal, createdAt, id")
    suspend fun all(): List<DiaryEntryEntity>

    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()
}

@Dao
interface WeightDao {
    @Upsert
    suspend fun upsert(weight: WeightEntity)

    @Insert
    suspend fun insertAll(weights: List<WeightEntity>)

    @Query("DELETE FROM weights WHERE date = :date")
    suspend fun delete(date: Long)

    @Query("SELECT * FROM weights ORDER BY date")
    fun observeAll(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights ORDER BY date")
    suspend fun all(): List<WeightEntity>

    @Query("SELECT * FROM weights ORDER BY date DESC LIMIT 1")
    suspend fun latest(): WeightEntity?

    @Query("DELETE FROM weights")
    suspend fun deleteAll()
}
