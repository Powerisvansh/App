package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.GeocodeCacheEntity

@Dao
interface GeocodeCacheDao {

    @Query("SELECT * FROM geocode_cache WHERE coordinateKey = :key LIMIT 1")
    suspend fun getCachedGeocode(key: String): GeocodeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: GeocodeCacheEntity)

    @Query("DELETE FROM geocode_cache WHERE cachedAtUtc < :cutoffUtc")
    suspend fun deleteOlderThan(cutoffUtc: Long): Int

    @Query("DELETE FROM geocode_cache")
    suspend fun deleteAll(): Int
}
