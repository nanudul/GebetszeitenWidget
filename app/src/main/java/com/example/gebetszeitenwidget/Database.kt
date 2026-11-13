package com.example.gebetszeitenwidget

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PrayingCity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    abstract fun prayingCityDao(): PrayingCityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "app_datenbank"
                            ).fallbackToDestructiveMigration(false) // Destroys old tables and recreates them on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "Gebetsorte")
data class PrayingCity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val city: String, val regionId: String
)

@Dao
interface PrayingCityDao {
    @Insert
    suspend fun insert(prayingCity: PrayingCity)

    @Query("SELECT * FROM Gebetsorte")
    suspend fun allPrayingCities(): List<PrayingCity>

    @Query("SELECT regionId FROM Gebetsorte WHERE city=:city")
    suspend fun getRegionId(city: String): String

    @Query("DELETE FROM Gebetsorte")
    suspend fun deleteAllCities()

    @Delete
    suspend fun deletePrayingCity(prayingCity: PrayingCity)
}