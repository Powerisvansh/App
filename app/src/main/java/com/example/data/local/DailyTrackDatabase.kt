package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.GeocodeCacheDao
import com.example.data.local.dao.LocationPointDao
import com.example.data.local.dao.VisitDao
import com.example.data.local.entity.GeocodeCacheEntity
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.VisitEntity

@Database(
    entities = [
        LocationPointEntity::class,
        VisitEntity::class,
        GeocodeCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DailyTrackDatabase : RoomDatabase() {

    abstract fun locationPointDao(): LocationPointDao
    abstract fun visitDao(): VisitDao
    abstract fun geocodeCacheDao(): GeocodeCacheDao

    companion object {
        @Volatile
        private var INSTANCE: DailyTrackDatabase? = null

        fun getDatabase(context: Context): DailyTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DailyTrackDatabase::class.java,
                    "daily_track_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
