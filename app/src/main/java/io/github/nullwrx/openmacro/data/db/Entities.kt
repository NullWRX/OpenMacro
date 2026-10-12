package io.github.nullwrx.openmacro.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.nullwrx.openmacro.core.BaseUnit
import io.github.nullwrx.openmacro.core.Nutrients
import io.github.nullwrx.openmacro.core.Serving
import kotlinx.serialization.Serializable

enum class FoodSource { CUSTOM, OFF, USDA }

/** Nutrient columns shared by foods (per basis) and diary entries (totals). */
data class NutrientColumns(
    val energyKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    val saturatedFatG: Double? = null,
    val sodiumMg: Double? = null,
) {
    fun toCore() = Nutrients(energyKcal, proteinG, carbsG, fatG, fiberG, sugarG, saturatedFatG, sodiumMg)

    companion object {
        fun from(n: Nutrients) = NutrientColumns(
            n.energyKcal, n.proteinG, n.carbsG, n.fatG, n.fiberG, n.sugarG, n.saturatedFatG, n.sodiumMg,
        )
    }
}

@Serializable
data class ServingColumn(val label: String, val baseAmount: Double) {
    fun toCore() = Serving(label, baseAmount)
}

@Entity(
    tableName = "foods",
    indices = [
        Index("barcode"),
        Index(value = ["source", "sourceId"], unique = true),
    ],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: FoodSource,
    /** Id in the source database: USDA FDC id or Open Food Facts barcode. Null for custom foods. */
    val sourceId: String? = null,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val baseUnit: BaseUnit,
    /** Per 100 g or ml, or per 1 serving when [baseUnit] is SERVING. */
    @Embedded val nutrients: NutrientColumns,
    val servings: List<ServingColumn> = emptyList(),
    val favorite: Boolean = false,
    /** Hidden foods (deleted custom foods) stay so old diary entries keep their link. */
    val hidden: Boolean = false,
    val nutriScore: String? = null,
    val novaGroup: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

fun FoodEntity.coreServings(): List<Serving> = servings.map { it.toCore() }

@Fts4(contentEntity = FoodEntity::class)
@Entity(tableName = "foods_fts")
data class FoodFts(
    val name: String,
    val brand: String?,
)

@Entity(
    tableName = "diary_entries",
    indices = [Index("date"), Index("foodId")],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Day as days since 1970-01-01 (LocalDate.toEpochDay). */
    val date: Long,
    /** Index into the user's meal list. */
    val meal: Int,
    /** The food logged, or null for a quick add. */
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val servingLabel: String,
    val servingCount: Double,
    /** Total base units eaten (servingCount x serving size). */
    val baseAmount: Double,
    val baseUnit: BaseUnit,
    /** Totals for this entry, frozen at logging time so history never shifts. */
    @Embedded val nutrients: NutrientColumns,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val date: Long,
    val kg: Double,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A row for mapping source ids to local ids during imports and seeding. */
data class SourceIdRow(val id: Long, val sourceId: String?)
