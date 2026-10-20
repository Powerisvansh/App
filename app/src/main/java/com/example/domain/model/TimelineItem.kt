package com.example.domain.model

import com.example.data.local.entity.VisitEntity

sealed class TimelineItem {
    abstract val timestampUtc: Long

    data class VisitTimelineItem(
        val visit: VisitEntity
    ) : TimelineItem() {
        override val timestampUtc: Long = visit.arrivalTimeUtc
    }

    data class JourneyTimelineItem(
        val fromPlaceName: String,
        val toPlaceName: String,
        val distanceMeters: Double,
        val durationSeconds: Long,
        val startTimeUtc: Long,
        val endTimeUtc: Long,
        val averageSpeedKmh: Double,
        val pointCount: Int,
        val startLatitude: Double = 0.0,
        val startLongitude: Double = 0.0,
        val endLatitude: Double = 0.0,
        val endLongitude: Double = 0.0,
        val waypoints: List<Pair<Double, Double>> = emptyList()
    ) : TimelineItem() {
        override val timestampUtc: Long = startTimeUtc
    }
}
