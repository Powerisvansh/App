package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a visited place detected automatically or confirmed by the user.
 * departureTimeUtc is null while the visit is ongoing.
 */
@Entity(
    tableName = "visits",
    indices = [
        Index(value = ["arrivalTimeUtc"]),
        Index(value = ["departureTimeUtc"])
    ]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val placeName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val arrivalTimeUtc: Long,
    val departureTimeUtc: Long? = null,
    val durationSeconds: Long = 0L,
    val accuracy: Float = 0f,
    val confidence: Float = 0.85f,
    val isUserEdited: Boolean = false,
    val pointCount: Int = 1
)
