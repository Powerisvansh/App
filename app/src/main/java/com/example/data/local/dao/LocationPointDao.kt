package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.LocationPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: LocationPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE timestampUtc BETWEEN :startUtc AND :endUtc ORDER BY timestampUtc ASC")
    fun getPointsForDateRange(startUtc: Long, endUtc: Long): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points ORDER BY timestampUtc ASC")
    fun getAllPointsFlow(): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE timestampUtc BETWEEN :startUtc AND :endUtc ORDER BY timestampUtc ASC")
    suspend fun getPointsBetweenSync(startUtc: Long, endUtc: Long): List<LocationPointEntity>

    @Query("SELECT * FROM location_points ORDER BY timestampUtc DESC LIMIT 1")
    suspend fun getLastPoint(): LocationPointEntity?

    @Query("SELECT * FROM location_points ORDER BY timestampUtc DESC LIMIT 1")
    fun getLastPointFlow(): Flow<LocationPointEntity?>

    @Query("SELECT * FROM location_points WHERE associatedVisitId = :visitId ORDER BY timestampUtc ASC")
    fun getPointsForVisit(visitId: Long): Flow<List<LocationPointEntity>>

    @Query("SELECT COALESCE(SUM(distanceFromLastMeters), 0.0) FROM location_points WHERE timestampUtc BETWEEN :startUtc AND :endUtc")
    fun getTotalDistanceBetween(startUtc: Long, endUtc: Long): Flow<Double>

    @Query("DELETE FROM location_points WHERE timestampUtc < :cutoffUtc")
    suspend fun deleteOlderThan(cutoffUtc: Long): Int

    @Query("DELETE FROM location_points")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM location_points")
    suspend fun getCount(): Int
}
