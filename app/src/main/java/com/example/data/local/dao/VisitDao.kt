package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: VisitEntity): Long

    @Update
    suspend fun updateVisit(visit: VisitEntity)

    @Delete
    suspend fun deleteVisit(visit: VisitEntity)

    @Query("SELECT * FROM visits WHERE (arrivalTimeUtc BETWEEN :startUtc AND :endUtc) OR (departureTimeUtc IS NULL AND arrivalTimeUtc <= :endUtc) ORDER BY arrivalTimeUtc ASC")
    fun getVisitsForDate(startUtc: Long, endUtc: Long): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits ORDER BY arrivalTimeUtc DESC")
    fun getAllVisitsFlow(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits ORDER BY arrivalTimeUtc DESC")
    suspend fun getAllVisitsSync(): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE id = :id")
    fun getVisitByIdFlow(id: Long): Flow<VisitEntity?>

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun getVisitById(id: Long): VisitEntity?

    @Query("SELECT * FROM visits WHERE departureTimeUtc IS NULL ORDER BY arrivalTimeUtc DESC LIMIT 1")
    suspend fun getOngoingVisit(): VisitEntity?

    @Query("SELECT * FROM visits WHERE departureTimeUtc IS NULL ORDER BY arrivalTimeUtc DESC LIMIT 1")
    fun getOngoingVisitFlow(): Flow<VisitEntity?>

    @Query("SELECT * FROM visits WHERE placeName LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' ORDER BY arrivalTimeUtc DESC")
    fun searchVisits(query: String): Flow<List<VisitEntity>>

    @Query("DELETE FROM visits WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM visits WHERE arrivalTimeUtc < :cutoffUtc")
    suspend fun deleteOlderThan(cutoffUtc: Long): Int

    @Query("DELETE FROM visits")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM visits WHERE arrivalTimeUtc BETWEEN :startUtc AND :endUtc")
    fun getVisitCountForDate(startUtc: Long, endUtc: Long): Flow<Int>
}
