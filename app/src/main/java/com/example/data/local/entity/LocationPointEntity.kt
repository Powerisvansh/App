package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores GPS track points along routes and visits.
 * Timestamps are always stored in UTC milliseconds.
 */
@Entity(
    tableName = "location_points",
    indices = [
        Index(value = ["timestampUtc"]),
        Index(value = ["isVisitCandidate"])
    ]
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float = 0f,
    val altitude: Double = 0.0,
    val timestampUtc: Long = System.currentTimeMillis(),
    val distanceFromLastMeters: Double = 0.0,
    val isVisitCandidate: Boolean = false,
    val associatedVisitId: Long? = null
)
