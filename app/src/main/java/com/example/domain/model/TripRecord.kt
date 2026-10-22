package com.example.domain.model

data class TripRecord(
    val id: String,
    val dateLabel: String,
    val dateTimestampUtc: Long,
    val startTimeUtc: Long,
    val endTimeUtc: Long,
    val totalDistanceMeters: Double,
    val durationSeconds: Long,
    val startPlaceName: String,
    val destinationPlaceName: String,
    val pointCount: Int,
    val visitCount: Int,
    val maxSpeedKmh: Double,
    val averageSpeedKmh: Double,
    val isOngoing: Boolean = false
)
