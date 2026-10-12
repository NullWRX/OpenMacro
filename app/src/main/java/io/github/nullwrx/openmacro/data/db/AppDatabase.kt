package io.github.nullwrx.openmacro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val servingList = ListSerializer(ServingColumn.serializer())

    @TypeConverter
    fun servingsToJson(value: List<ServingColumn>): String = json.encodeToString(servingList, value)

    @TypeConverter
    fun servingsFromJson(value: String): List<ServingColumn> =
        if (value.isBlank()) emptyList() else json.decodeFromString(servingList, value)
}

@Database(
    entities = [FoodEntity::class, FoodFts::class, DiaryEntryEntity::class, WeightEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foods(): FoodDao
    abstract fun diary(): DiaryDao
    abstract fun weights(): WeightDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "openmacro.db").build()
    }
}
