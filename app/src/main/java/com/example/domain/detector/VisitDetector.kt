package com.example.domain.detector

import android.location.Location
import com.example.data.local.dao.LocationPointDao
import com.example.data.local.dao.VisitDao
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.VisitEntity
import com.example.domain.geocoder.GeocodingService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

class VisitDetector(
    private val visitDao: VisitDao,
    private val locationPointDao: LocationPointDao,
    private val geocodingService: GeocodingService
) {
    private val mutex = Mutex()
    private val candidatePoints = mutableListOf<LocationPointEntity>()
    private var departureWarningCount = 0

    suspend fun processLocation(
        point: LocationPointEntity,
        stationaryRadiusMeters: Float = 45f,
        dwellTimeMinutes: Int = 3
    ): VisitEntity? = mutex.withLock {
        val dwellTimeMillis = dwellTimeMinutes * 60 * 1000L

        // Check if there is an active ongoing visit in the DB
        var ongoingVisit = visitDao.getOngoingVisit()

        if (ongoingVisit != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                ongoingVisit.latitude,
                ongoingVisit.longitude,
                point.latitude,
                point.longitude,
                results
            )
            val distanceToVisit = results[0]

            // Effective radius considering GPS accuracy
            val effectiveRadius = max(stationaryRadiusMeters, point.accuracy.coerceAtMost(60f))

            if (distanceToVisit <= effectiveRadius) {
                // User is still at this visit
                departureWarningCount = 0
                val newPointCount = ongoingVisit.pointCount + 1
                // Refine centroid smoothly with moving average
                val updatedLat = (ongoingVisit.latitude * ongoingVisit.pointCount + point.latitude) / newPointCount
                val updatedLon = (ongoingVisit.longitude * ongoingVisit.pointCount + point.longitude) / newPointCount
                val duration = max(0L, (point.timestampUtc - ongoingVisit.arrivalTimeUtc) / 1000L)
                val newAccuracy = min(ongoingVisit.accuracy, point.accuracy)
                val newConfidence = min(1.0f, 0.7f + (newPointCount * 0.03f))

                val updatedVisit = ongoingVisit.copy(
                    latitude = updatedLat,
                    longitude = updatedLon,
                    pointCount = newPointCount,
                    durationSeconds = duration,
                    accuracy = newAccuracy,
                    confidence = newConfidence
                )
                visitDao.updateVisit(updatedVisit)
                return updatedVisit
            } else {
                // Movement detected away from place
                departureWarningCount++
                if (departureWarningCount >= 2 || point.speed > 1.5f || distanceToVisit > stationaryRadiusMeters * 2.5f) {
                    // Confirmed departure!
                    val departureTime = point.timestampUtc
                    val totalDuration = max(0L, (departureTime - ongoingVisit.arrivalTimeUtc) / 1000L)
                    val closedVisit = ongoingVisit.copy(
                        departureTimeUtc = departureTime,
                        durationSeconds = totalDuration
                    )
                    visitDao.updateVisit(closedVisit)
                    ongoingVisit = null
                    departureWarningCount = 0
                    candidatePoints.clear()
                    candidatePoints.add(point)
                }
            }
        } else {
            // No ongoing visit. Accumulate candidate points to detect stationary dwell.
            candidatePoints.add(point)

            // Remove points that are older than 30 minutes from candidates if not yet in a cluster
            val cutoff = point.timestampUtc - (30 * 60 * 1000L)
            candidatePoints.removeAll { it.timestampUtc < cutoff }

            if (candidatePoints.isNotEmpty()) {
                val centroidLat = candidatePoints.map { it.latitude }.average()
                val centroidLon = candidatePoints.map { it.longitude }.average()

                val results = FloatArray(1)
                var allWithinRadius = true
                for (cp in candidatePoints) {
                    Location.distanceBetween(centroidLat, centroidLon, cp.latitude, cp.longitude, results)
                    if (results[0] > stationaryRadiusMeters + cp.accuracy * 0.5f) {
                        allWithinRadius = false
                        break
                    }
                }

                if (!allWithinRadius) {
                    // Outliers present: drop oldest point and re-evaluate
                    if (candidatePoints.size > 1) {
                        candidatePoints.removeAt(0)
                    }
                } else {
                    val oldestTime = candidatePoints.first().timestampUtc
                    val newestTime = candidatePoints.last().timestampUtc
                    val dwellSpan = newestTime - oldestTime

                    if (dwellSpan >= dwellTimeMillis && candidatePoints.size >= 2) {
                        // Candidate cluster satisfied dwell threshold! Declare NEW VISIT!
                        val geocodeResult = geocodingService.getPlaceAndAddress(centroidLat, centroidLon)
                        val avgAccuracy = candidatePoints.map { it.accuracy }.average().toFloat()

                        val newVisit = VisitEntity(
                            placeName = geocodeResult.placeName,
                            address = geocodeResult.address,
                            latitude = centroidLat,
                            longitude = centroidLon,
                            arrivalTimeUtc = oldestTime,
                            departureTimeUtc = null,
                            durationSeconds = (newestTime - oldestTime) / 1000L,
                            accuracy = avgAccuracy,
                            confidence = 0.85f,
                            pointCount = candidatePoints.size
                        )

                        val newVisitId = visitDao.insertVisit(newVisit)
                        val insertedVisit = newVisit.copy(id = newVisitId)

                        candidatePoints.clear()
                        departureWarningCount = 0
                        return insertedVisit
                    }
                }
            }
        }

        return ongoingVisit
    }

    suspend fun closeOngoingVisitIfAny(departureTimeUtc: Long = System.currentTimeMillis()) = mutex.withLock {
        val ongoing = visitDao.getOngoingVisit()
        if (ongoing != null) {
            val duration = max(0L, (departureTimeUtc - ongoing.arrivalTimeUtc) / 1000L)
            visitDao.updateVisit(
                ongoing.copy(
                    departureTimeUtc = departureTimeUtc,
                    durationSeconds = duration
                )
            )
        }
        candidatePoints.clear()
        departureWarningCount = 0
    }
}
