package com.example.domain.model

import com.example.data.local.entity.VisitEntity

data class DailySummary(
    val dateLabel: String,
    val startOfDayUtc: Long,
    val endOfDayUtc: Long,
    val totalDistanceMeters: Double = 0.0,
    val totalPlacesVisited: Int = 0,
    val timeOutsideSeconds: Long = 0L,
    val stationaryTimeSeconds: Long = 0L,
    val visits: List<VisitEntity> = emptyList()
)
